package org.scalaide.core.internal.builder.zinc

import java.util.Optional

import xsbti.VirtualFile
import xsbti.compile.CompileAnalysis
import xsbti.compile.DefinesClass
import xsbti.compile.PerClasspathEntryLookup

private[zinc] trait DefaultPerClasspathEntryLookup extends PerClasspathEntryLookup {

  override def analysis(classpathEntry: VirtualFile): Optional[CompileAnalysis] =
    Optional.empty()

  override def definesClass(classpathEntry: VirtualFile) = {
    val dc = Locator(fileConverter.toPath(classpathEntry).toFile)
    new DefinesClass() {
      def apply(name: String) = dc.apply(name)
    }
  }
}
