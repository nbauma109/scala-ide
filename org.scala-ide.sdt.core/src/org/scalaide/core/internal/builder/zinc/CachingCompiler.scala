package org.scalaide.core.internal.builder.zinc

import java.io.File
import java.nio.file.Path
import java.util.Optional

import org.scalaide.util.internal.SbtUtils

import sbt.internal.inc.Analysis
import sbt.internal.inc.IncrementalCompilerImpl
import sbt.internal.inc.MixedAnalyzingCompiler
import sbt.util.InterfaceUtil.o2jo
import xsbti.Logger
import xsbti.Reporter
import xsbti.VirtualFile
import xsbti.compile.AnalysisContents
import xsbti.compile.CompileResult
import xsbti.compile.JavaCompiler
import xsbti.compile.ScalaCompiler

/**
 * Contains a Scala and a Java compiler. Should be used instead of
 * [[xsbti.compile.Compilers]]. The latter got a new API in zinc, which we don't
 * need to adapt.
 */
final case class Compilers(scalac: ScalaCompiler, javac: JavaCompiler)

class CachingCompiler private (cacheFile: File, sbtReporter: Reporter, log: Logger) {
  /**
   * Inspired by `IC.compile` and `AggressiveCompile.compile1`
   *
   *  We need to duplicate `IC.compile`, because Java interface passes the incremental
   *  compiler options `IncOptions` as `Map[String, String]`, which is not expressive
   *  enough to use the transactional classfile manager (required for correctness).
   *  In other terms, we need richer (`IncOptions`) parameter type, here.
   *  Other thing is the update of the `AnalysisStore` implemented in `AggressiveCompile.compile1`
   *  method which is not implemented in `IC.compile`.
   */
  def compile(in: SbtInputs, comps: Compilers): Analysis = {
    val lookup = new DefaultPerClasspathEntryLookup {
      override def analysis(classpathEntry: VirtualFile) =
        in.analysisMap(classpathEntry)
    }
    val (previousAnalysis, previousSetup) = SbtUtils.readCache(cacheFile)
      .map {
        case (a, s) => (Option(a), Option(s))
      }.getOrElse((Option(SbtUtils.readAnalysis(cacheFile)), None))

    // TODO: upgrade to zinc 1.6
    val sources: Array[Path] = in.sources.map(_.toPath()).toArray
    val classpath: Array[Path]= in.classpath.map(_.toPath()).toArray
    val compilationResult = new IncrementalCompilerImpl().compile(
      comps.scalac,
      comps.javac,
      sources,
      classpath,
      in.output,
      Optional.empty[xsbti.compile.Output],
      Optional.empty[xsbti.compile.AnalysisStore],
      in.cache,
      in.scalacOptions,
      in.javacOptions,
      o2jo[xsbti.compile.CompileAnalysis](previousAnalysis),
      o2jo[xsbti.compile.MiniSetup](previousSetup),
      lookup,
      sbtReporter,
      in.order,
      false,
      in.progress,
      in.incOptions,
      Optional.empty[Path],
      Array[xsbti.T2[String, String]](),
      fileConverter,
      stamper,
      log)
    cacheAndReturnLastAnalysis(compilationResult)
  }

  private def cacheAndReturnLastAnalysis(compilationResult: CompileResult): Analysis = {
    if (compilationResult.hasModified)
      AnalysisStore.materializeLazy(MixedAnalyzingCompiler.staticCachedStore(cacheFile.toPath, true)).set(AnalysisContents.create(compilationResult.analysis, compilationResult.setup))
    compilationResult.analysis match {
      case a: Analysis => a
      case a => throw new IllegalStateException(s"object of type `Analysis` was expected but got `${a.getClass}`.")
    }
  }
}

object CachingCompiler {
  def apply(cacheFile: File, sbtReporter: Reporter, logger: Logger): CachingCompiler =
    new CachingCompiler(cacheFile, sbtReporter, logger)
}
