package org.scalaide.core.internal.jdt.model

import java.util.{ Map => JMap }

import scala.tools.eclipse.contribution.weaving.jdt.IScalaSourceFile
import scala.tools.nsc.interactive.Response
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.io.VirtualFile
import scala.util.control.Exception

import org.eclipse.core.resources.IFile
import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.core.JavaModelException
import org.eclipse.jdt.core.WorkingCopyOwner
import org.eclipse.jdt.core.compiler.CharOperation
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.internal.core.{ CompilationUnit => JDTCompilationUnit }
import org.eclipse.jdt.internal.core.OpenableElementInfo
import org.eclipse.jdt.internal.core.PackageFragment
import org.eclipse.jdt.internal.core.util.HandleFactory
import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.scalaide.core.compiler.ScalaCompilationProblem
import org.scalaide.core.extensions.SourceFileProvider
import org.scalaide.core.resources.EclipseFile

class ScalaSourceFileProvider extends SourceFileProvider {
  override def createFrom(path: IPath): Option[InteractiveCompilationUnit] =
    ScalaSourceFile.createFromPath(path.toString)
}

object ScalaSourceFile {

  /**
   * Considering [[org.eclipse.jdt.internal.core.util.HandleFactory]] isn't thread-safe, and because
   * `ScalaSourceFile#createFromPath` can be called concurrently from different threads, using a
   * `ThreadLocal` ensures that a `HandleFactory` instance is never shared across threads.
   */
  private val handleFactory: ThreadLocal[HandleFactory] = new ThreadLocal[HandleFactory] {
    override protected def initialValue(): HandleFactory = new HandleFactory
  }

  /**
   * Creates a Scala source file handle if the given resource path points to a scala source.
   * The resource path is a path to a Scala source file in the workbench (e.g. /Proj/a/b/c/Foo.scala).
   *
   * @note This assumes that the resource path is the toString() of an `IPath`.
   *
   * @param path Is a path to a Scala source file in the workbench.
   */
  def createFromPath(path: String): Option[ScalaSourceFile] = {
    if (!path.endsWith(".scala"))
      None
    else {
      // Always `null` because `handleFactory.createOpenable` is only called to open source files, and the `scope` is not needed for this.
      val unusedScope = null
      val source = handleFactory.get().createOpenable(path, unusedScope)
      source match {
        case ssf: ScalaSourceFile ⇒ Some(ssf)
        case _                    ⇒ None
      }
    }
  }
}

class ScalaSourceFile(fragment: PackageFragment, elementName: String, workingCopyOwner: WorkingCopyOwner)
    extends JDTCompilationUnit(fragment, elementName, workingCopyOwner) with ScalaCompilationUnit with IScalaSourceFile {

  override def getCompilationUnit: JDTCompilationUnit = super.getCompilationUnit.asInstanceOf[JDTCompilationUnit]

  override def getMainTypeName: Array[Char] =
    getElementName.substring(0, getElementName.length - ".scala".length).toCharArray()

  /**
   * Schedule this source file for reconciliation. Add the file to
   * the loaded files managed by the presentation compiler.
   */
  override def initialReconcile(): Response[Unit] = {
    val reloaded = super.initialReconcile()

    this.reconcile(
      ICompilationUnit.NO_AST,
      false /* don't force problem detection */,
      null /* use primary owner */,
      null /* no progress monitor */)

    reloaded
  }

  def reconcile(newContents: String): List[ScalaCompilationProblem] = {
    super.forceReconcile()
  }

  /**
   * We cut short this call since reconciliation is performed through the usual mechanism in the
   * editor. Calls arriving here come from the JDT, for instance from the breadcrumb view, and end
   * up doing expensive computation on the UI thread.
   *
   * @see #1002412
   */
  override def reconcile(
    astLevel: Int,
    reconcileFlags: Int,
    workingCopyOwner: WorkingCopyOwner,
    monitor: IProgressMonitor): org.eclipse.jdt.core.dom.CompilationUnit = {
    null
  }

  override def makeConsistent(
    astLevel: Int,
    resolveBindings: Boolean,
    reconcileFlags: Int,
    problems: JMap[String, Array[org.eclipse.jdt.core.compiler.CategorizedProblem]],
    monitor: IProgressMonitor): org.eclipse.jdt.core.dom.CompilationUnit = {

    // don't rerun this expensive operation unless necessary
    if (!isConsistent()) {
      if (astLevel != ICompilationUnit.NO_AST && resolveBindings) {
        val info = createElementInfo.asInstanceOf[OpenableElementInfo]
        openWhenClosed(info, true, monitor)
      } else
        logger.info(s"Skipped `makeConsistent` with resolveBindings: $resolveBindings and astLevel: $astLevel")
    }
    null
  }

  override def codeSelect(offset: Int, length: Int, workingCopyOwner: WorkingCopyOwner): Array[IJavaElement] =
    codeSelect(this, offset, length, workingCopyOwner)

  override lazy val file: AbstractFile = {
    val res = try { getCorrespondingResource } catch { case _: JavaModelException ⇒ null }
    res match {
      case f: IFile ⇒ new EclipseFile(f)
      case _        ⇒ new VirtualFile(getElementName, getPath.toString)
    }
  }

  /** Implementing the weaving interface requires to return `null` for an empty array. */
  override def getProblems: Array[IProblem] = {
    val probs = currentProblems()
    if (probs.isEmpty) null else probs.toArray
  }

  override def getType(name: String): IType =
    new LazyToplevelClass(this, name)

  override def getContents(): Array[Char] = {
    // in the following case, super#getContents() logs an exception for no good reason
    if (getBufferManager().getBuffer(this) == null && getResource().getLocation() == null && getResource().getLocationURI() == null) {
      return CharOperation.NO_CHAR
    }
    Exception.failAsValue(classOf[CoreException])(CharOperation.NO_CHAR) { super.getContents() }
  }

  /** Makes sure {{{this}}} source is not in the ignore buffer of the compiler and ask the compiler to reload it. */
  final def forceReload(): Unit = scalaProject.presentationCompiler { compiler ⇒
    compiler.askToDoFirst(this)
    reload()
  }

  /** Ask the compiler to reload {{{this}}} source. */
  final def reload(): Unit = scalaProject.presentationCompiler { _.askReload(this, lastSourceMap().sourceFile) }

  /** Ask the compiler to discard {{{this}}} source. */
  final def discard(): Unit = scalaProject.presentationCompiler { _.discardCompilationUnit(this) }
}
