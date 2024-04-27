package org.scalaide.core.internal.builder.zinc

import java.util.Optional

import xsbti.compile.CompileAnalysis
import xsbti.compile.DefinesClass
import xsbti.compile.PerClasspathEntryLookup

import sbt.internal.inc.PlainVirtualFileConverter

private[zinc] trait DefaultPerClasspathEntryLookup extends PerClasspathEntryLookup {
  override def analysis(classpathEntry: xsbti.VirtualFile): Optional[CompileAnalysis] =
    Optional.empty()

  override def definesClass(classpathEntry: xsbti.VirtualFile) = {
    val dc = Locator(PlainVirtualFileConverter.converter.toPath(classpathEntry).toFile)
    new DefinesClass() {
      def apply(name: String) = dc.apply(name)
    }
  }
}
