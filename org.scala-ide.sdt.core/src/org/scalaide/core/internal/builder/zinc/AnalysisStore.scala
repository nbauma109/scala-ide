package org.scalaide.core.internal.builder.zinc

import java.util.Optional

import sbt.internal.inc.Analysis
import xsbti.compile.AnalysisContents
import xsbti.compile.CompileAnalysis
import xsbti.compile.MiniSetup

object AnalysisStore {
  import xsbti.compile.{ AnalysisStore => SbtAnalysisStore }
  def materializeLazy(backing: SbtAnalysisStore): SbtAnalysisStore = new SbtAnalysisStore {
    private def materializeApis(analysis: CompileAnalysis, setup: MiniSetup): AnalysisContents = {
      if (setup.storeApis()) {
        val apis = analysis match { case a: Analysis => a.apis }
        apis.internal.foreach { case (_, v) => v.api }
        apis.external.foreach { case (_, v) => v.api }
      }
      AnalysisContents.create(analysis, setup)
    }
    def set(contents: AnalysisContents): Unit = {
      backing.set(materializeApis(contents.getAnalysis, contents.getMiniSetup))
    }
    def get(): Optional[AnalysisContents] = backing.get()
    override def unsafeGet: AnalysisContents = get.get
  }
}