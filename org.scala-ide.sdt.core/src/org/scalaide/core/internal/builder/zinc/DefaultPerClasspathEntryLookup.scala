package org.scalaide.core.internal.builder.zinc

import java.util.Optional

import xsbti.FileConverter
import xsbti.VirtualFile
import xsbti.compile.CompileAnalysis
import xsbti.compile.DefinesClass
import xsbti.compile.PerClasspathEntryLookup

private[zinc] trait DefaultPerClasspathEntryLookup extends PerClasspathEntryLookup {
    //TODO: upgrade to zink 1.6.0
  def converter: FileConverter = null

  override def analysis(classpathEntry: VirtualFile): Optional[CompileAnalysis] =
    Optional.empty()

  override def definesClass(classpathEntry: VirtualFile) = {
    //TODO: upgrade to zink 1.6.0
    val dc = Locator(converter.toPath(classpathEntry).toFile)
    new DefinesClass() {
      def apply(name: String) = dc.apply(name)
    }
  }
}
