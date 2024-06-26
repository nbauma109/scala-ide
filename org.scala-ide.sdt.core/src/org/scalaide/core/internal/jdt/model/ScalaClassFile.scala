package org.scalaide.core.internal.jdt.model

import java.util.{ Map => JMap }

import scala.tools.eclipse.contribution.weaving.jdt.IScalaClassFile
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.io.VirtualFile

import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.core.WorkingCopyOwner
import org.eclipse.jdt.core.compiler.CharOperation
import org.eclipse.jdt.internal.core.BinaryType
import org.eclipse.jdt.internal.core.ClassFile
import org.eclipse.jdt.internal.core.JavaElement
import org.eclipse.jdt.internal.core.JavaModelStatus
import org.eclipse.jdt.internal.core.PackageFragment
import org.eclipse.jdt.internal.core.util.MementoTokenizer
import org.eclipse.jdt.internal.core.util.Util
import org.eclipse.jdt.internal.compiler.env.IElementInfo
import org.scalaide.core.compiler.ScalaCompilationProblem
import org.scalaide.ui.ScalaImages

class ScalaClassFile(parent: PackageFragment, name: String, sourceFile: String)
    extends ClassFile(parent, name) with ScalaCompilationUnit with IScalaClassFile {
  override def getImageDescriptor = ScalaImages.SCALA_CLASS_FILE

  override def getElementAt(position: Int): IJavaElement = {
    val e = getSourceElementAt(position)
    if (e == this) null else e
  }

  /** We don't need to reconcile for a classfile. */
  override def scheduleReconcile(contents: Array[Char]): Unit = ()

  /** Return the corresponding element in the source-based JDT elements.
   *
   *  This method returns the correct java element when there is an attached source file
   *  for this class file. For example, when `element` is an inner type, like `TypeSymbol`,
   *  the elements look like this:
   *
   *  `Symbols$TypeSymbol.class` as an instance of a ScalaClassFile
   *  `TypeSymbol` as an instance of ScalaBinaryType, pointing to the corresponing classfile
   *
   *  when an editor is open, and attached sources are found, the java element hierarchy
   *  (created by the structure builder based on the source file) is:
   *
   *  ScalaSourceFile("Symbols.scala") / `Symbols` / `TypeSymbol`
   *
   *  This method (on `ScalaClassFile`) maps back the `ScalaBinaryType` to the correct inner element
   *  `TypeSymbol`.
   *
   *  @note This method is called by `EditorsUI.revealInEditor`.
   */
  def getCorrespondingElement(element: IJavaElement): Option[IJavaElement] = {
    if (!validateExistence(resource).isOK)
      None
    else {
      val name = element.getElementName
      if (name.length() == 0)
        None
      else {
        val tpe = element.getElementType
        val enclosingType = if (tpe == IJavaElement.TYPE) element else Option(element.getAncestor(IJavaElement.TYPE)).getOrElse(element)
        val tpe1 = allTypes.find(_.getElementName == enclosingType.getElementName)
        if (tpe == IJavaElement.TYPE)
          tpe1
        else
          tpe1.flatMap(_.getChildren.find(e => e.getElementName == name && e.getElementType == tpe))
      }
    }
  }

  override def codeSelect(offset: Int, length: Int, owner: WorkingCopyOwner): Array[IJavaElement] =
    codeSelect(this, offset, length, owner)

  def getContents() = Option(getSourceMapper) flatMap
    { mapper => Option(mapper.findSource(getType, getSourceFileName)) } getOrElse Array.empty

  override lazy val file: AbstractFile = new VirtualFile(getSourceFileName, getSourceFilePath)

  def getSourceFileName() = sourceFile

  def getSourceFilePath() = {
    val tpe = getType
    val pkgFrag = tpe.getPackageFragment.asInstanceOf[PackageFragment]
    Util.concatWith(pkgFrag.names, sourceFile, '/')
  }

  def getPackage(): PackageFragment = parent

  def getPackageName(): Array[Array[Char]] = {
    if (getPackage == null) CharOperation.NO_CHAR_CHAR
    else Util.toCharArrays(getPackage.names)
  }

  lazy val allTypes: Seq[IType] = {
    (this +: getChildren().toList) flatMap {
      case sourceFile: ScalaClassFile =>
        Seq(sourceFile.getType())
      case typeElement: IType =>
        typeElement +: typeElement.getTypes
      case _ =>
        Seq[IType]()
    }
  }

  class ScalaBinaryType(parent: JavaElement, name: String) extends BinaryType(parent, name) {
    def this(name: String) = this(ScalaClassFile.this, name)
    lazy val mirror = {
      allTypes.find(t => t.getElementName == name)
    }
    override def exists = mirror.isDefined
    override def getType(typeName: String): IType = {
      if (name == typeName)
        this
      else
        new ScalaBinaryType(typeName)
    }
  }

  override def getType(): IType =
    new ScalaBinaryType(getTypeName)

  def getMainTypeName(): Array[Char] =
    Util.getNameWithoutJavaLikeExtension(getElementName).toCharArray

  override def getTypeName(): String = {
    val lastDollar = name.lastIndexOf('$')
    if (lastDollar == -1 || lastDollar != name.length - 1)
      super.getTypeName
    else {
      val lastDollar0 = name.lastIndexOf('$', lastDollar - 1)
      if (lastDollar0 > -1) Util.localTypeName(name, lastDollar0, name.length()) else name
    }
  }

  def getFileName(): Array[Char] = getPath.toString.toCharArray

  override def validateExistence(underlyingResource: IResource): IStatus = {
    if ((underlyingResource ne null) && !underlyingResource.isAccessible) newDoesNotExistStatus() else JavaModelStatus.VERIFIED_OK
  }

  override def currentProblems: List[ScalaCompilationProblem] = Nil

  def closeBuffer0() = super.closeBuffer()
  def closing0(info: AnyRef) = super.closing(info)
  def createElementInfo0() = super.createElementInfo()
  def generateInfos0(info: IElementInfo, newElements: JMap[IJavaElement, IElementInfo], monitor: IProgressMonitor) =
    super.generateInfos(info, newElements, monitor)
  def getBufferManager0() = super.getBufferManager()
  def validateExistence0(underlying: IResource): IStatus = validateExistence(underlying)
  def hasBuffer0(): Boolean = super.hasBuffer()
  def openBuffer0(pm: IProgressMonitor, info: IElementInfo) = super.openBuffer(pm, info)
  def resourceExists0(underlyingResource: IResource) = super.resourceExists(underlyingResource)
  def openAncestors0(newElements: JMap[IJavaElement, IElementInfo], monitor: IProgressMonitor): Unit = { super.openAncestors(newElements, monitor) }
  def getHandleMementoDelimiter0() = super.getHandleMementoDelimiter()
  def isSourceElement0(): Boolean = super.isSourceElement()
  override def ignoreOptionalProblems(): Boolean = false

  override def getHandleFromMemento(token: String, memento: MementoTokenizer, owner: WorkingCopyOwner): IJavaElement = {
    token.charAt(0) match {
      case JavaElement.JEM_TYPE =>
        if (!memento.hasMoreTokens)
          this
        else {
          val typeName = memento.nextToken
          val typ = new ScalaBinaryType(typeName)
          typ.getHandleFromMemento(memento, owner)
        }
      case _ => null
    }
  }
}
