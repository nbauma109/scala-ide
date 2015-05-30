package org.scalaide.core.internal.extensions

import java.io.File

import scala.reflect.internal.util.AbstractFileClassLoader
import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.internal.util.SourceFile
import scala.reflect.io.VirtualDirectory
import scala.tools.nsc.Settings
import scala.tools.nsc.interactive.Global
import scala.tools.nsc.reporters.StoreReporter
import scala.tools.nsc.settings.ScalaVersion
import scala.tools.nsc.settings.SpecificScalaVersion
import scala.util.Try

import org.scalaide.core.IScalaPlugin
import org.scalaide.core.compiler.IScalaPresentationCompiler
import org.scalaide.core.internal.project.ScalaInstallation
import org.scalaide.core.text.Document
import org.scalaide.extensions._
import org.scalaide.logging.HasLogger

// TODO logger seems not yet be available, we probably have to delay the initialization of this object
object ExtensionCompiler extends AnyRef with HasLogger {

  private val reporter = new StoreReporter
  private val outputDir = new VirtualDirectory("(memory)", None)
  private val classLoader = new AbstractFileClassLoader(outputDir, this.getClass.getClassLoader)

  private val settings = {
    val s = new Settings(err ⇒ System.err.println(err))
    s.outputDirs.setSingleOutput(outputDir)
    s.usejavacp.value = true
    s.YpresentationDebug.value = true
    s.YpresentationVerbose.value = true
    s.debug.value = true
    // TODO we can't continue if no 2.11 installation exists
    val install = ScalaInstallation.availableInstallations find { _.version match {
      case SpecificScalaVersion(2, 11, _, _) ⇒ true
      case _ ⇒ false
    }}
    install foreach { install ⇒
      s.bootclasspath.value = install.allJars.map(_.classJar).mkString(File.pathSeparator)
    }
    s.source.value = ScalaVersion("2.11")
    // TODO we have to handle the scala-ide bundles differently for non platform installations
    val scalaIdeClasspath = Seq(
      "/home/antoras/dev/scala/scala-ide/org.scala-ide.sdt.core/target/classes",
      "/home/antoras/dev/scala/scala-ide/org.scala-ide.sdt.aspects/target/classes",
      "/home/antoras/dev/scala/scala-refactoring/org.scala-refactoring.library/bin"
    )

    val bubndles = IScalaPlugin().getBundle.getBundleContext.getBundles.toList
    val bundlesClasspath = bubndles.map(_.getLocation).filter(_.endsWith(".jar")) flatMap {
      _.split(":") match {
        case Array(_, _, ref) ⇒ Seq(ref)
        case _ ⇒ Seq()
      }
    }
    s.classpath.value = (bundlesClasspath ++ scalaIdeClasspath).mkString(File.pathSeparator)
    s
  }

  private val compiler = new Global(settings, reporter)

  private object Types {
    val documentSupport = ExtensionSetting.fullyQualifiedName[DocumentSupport]
    val document = ExtensionSetting.fullyQualifiedName[Document]
    val compiler = ExtensionSetting.fullyQualifiedName[IScalaPresentationCompiler]
    val compilerSupport = ExtensionSetting.fullyQualifiedName[CompilerSupport]
    val sourceFile = ExtensionSetting.fullyQualifiedName[SourceFile]
  }

  private def buildDocumentExt(fullyQualifiedName: String, creatorName: String, pkg: String) = s"""
    package $pkg
    class $creatorName {
      def create(doc: ${Types.document}): ${Types.documentSupport} =
        new $fullyQualifiedName {
          override val document: ${Types.document} = doc
        }
    }
  """

  private def buildCompilerExt(fullyQualifiedName: String, creatorName: String, pkg: String) = s"""
    package $pkg
    class $creatorName {
      def create(
          c: ${Types.compiler},
          t: ${Types.compiler}#Tree,
          sf: ${Types.sourceFile},
          selStart: Int,
          selEnd: Int)
          : ${Types.compilerSupport} = {

        new $fullyQualifiedName {
          override val global: ${Types.compiler} = c
          override val sourceFile: ${Types.sourceFile} = sf
          override val selection = new FileSelection(
            sf.file, t.asInstanceOf[global.Tree], selStart, selEnd)
        }
      }
    }
  """

  /**
   * Compiles `src` and makes defined classes available through [[classLoader]].
   */
  private def compile(src: String): Unit = {
    reporter.reset()
    val srcFiles = List(new BatchSourceFile("(memory)", src))
    val run = new compiler.Run

    compiler ask { () ⇒ run.compileSources(srcFiles) }

    if (reporter.hasErrors || reporter.hasWarnings)
      throw new IllegalStateException(reporter.infos.mkString("Errors occurred during compilation of extension wrapper:\n", "\n", ""))
  }

  private sealed trait ExtensionType
  private case class SaveActionType(src: String, className: String, fn: (Class[_], Any) ⇒ Any) extends ExtensionType

  private def load(fullyQualifiedName: String): ExtensionType = {
    val interfaces = try classLoader.loadClass(fullyQualifiedName).getInterfaces catch {
      case e: ClassNotFoundException ⇒ throw new IllegalArgumentException(s"Extension '$fullyQualifiedName' doesn't exist.", e)
    }
    val isDocumentSaveAction = Set(classOf[SaveAction], classOf[DocumentSupport]) forall interfaces.contains
    val isCompilerSaveAction = Set(classOf[SaveAction], classOf[CompilerSupport]) forall interfaces.contains

    val pkg = "org.scalaide.internal.generated"
    val creatorName = s"${fullyQualifiedName.split('.').last}Creator"

    if (isDocumentSaveAction)
      SaveActionType(
          src = buildDocumentExt(fullyQualifiedName, creatorName, pkg),
          className = s"$pkg.$creatorName",
          fn = (cls, obj) ⇒ {
            val m = cls.getMethod("create", classOf[Document])
            (doc: Document) ⇒
              m.invoke(obj, doc)
          })
    else if (isCompilerSaveAction)
      SaveActionType(
          src = buildCompilerExt(fullyQualifiedName, creatorName, pkg),
          className = s"$pkg.$creatorName",
          fn = (cls, obj) ⇒ {
            val m = cls.getMethod("create", classOf[IScalaPresentationCompiler], classOf[IScalaPresentationCompiler#Tree], classOf[SourceFile], classOf[Int], classOf[Int])
            (c: IScalaPresentationCompiler, t: IScalaPresentationCompiler#Tree, src: SourceFile, selStart: Int, selEnd: Int) ⇒
              m.invoke(obj, c, t, src, Integer.valueOf(selStart), Integer.valueOf(selEnd))
          })
    else
      throw new IllegalArgumentException(s"Extension '$fullyQualifiedName' couldn't be qualified as a valid extension.")
  }

  def loadExtension(fullyQualifiedName: String): Try[Any] = Try {
    load(fullyQualifiedName) match {
      case SaveActionType(src, className, fn) ⇒
        compile(src)
        val cls = classLoader.loadClass(className)
        val obj = cls.newInstance()
        fn(cls, obj)
    }
  }

}
