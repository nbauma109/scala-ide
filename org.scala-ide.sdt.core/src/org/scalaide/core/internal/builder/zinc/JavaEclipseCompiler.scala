package org.scalaide.core.internal.builder.zinc

import scala.tools.eclipse.contribution.weaving.jdt.jcompiler.BuildManagerStore

import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IncrementalProjectBuilder.INCREMENTAL_BUILD
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.SubMonitor
import org.eclipse.jdt.core.IJavaModelMarker
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.internal.builder.JDTBuilderFacade
import org.scalaide.util.eclipse.FileUtils

import xsbti.Logger
import xsbti.Reporter
import xsbti.compile.IncToolOptions
import xsbti.compile.JavaCompiler

import sbt.internal.inc.PlainVirtualFileConverter

/**
 * Eclipse Java compiler interface, used by the SBT builder.
 * This class forwards to the internal Eclipse Java compiler, using
 * reflection to circumvent private/protected modifiers.
 */
class JavaEclipseCompiler(p: IProject, monitor: SubMonitor) extends JavaCompiler with JDTBuilderFacade {

  override def project = p

  override def run(sources: Array[xsbti.VirtualFile], unusedOptions: Array[String], compileOutput: xsbti.compile.Output, unusedIncToolOptions: IncToolOptions, reporter: Reporter, unusedLog: Logger): Boolean = {
    val scalaProject = IScalaPlugin().getScalaProject(project)
    val allSourceFiles = scalaProject.allSourceFiles()
    val depends = scalaProject.directDependencies
    if (allSourceFiles.exists(FileUtils.hasBuildErrors(_)))
      depends.toArray
    else {
      ensureProject
      // refresh output directories, since SBT removes classfiles that the Eclipse
      // Java compiler expects to find
      for (folder <- scalaProject.outputFolders) {
        val container =
          if (project.getFullPath == folder)
            project
          else
            ResourcesPlugin.getWorkspace().getRoot().getFolder(folder)
        container.refreshLocal(IResource.DEPTH_INFINITE, null)
      }

      BuildManagerStore.INSTANCE.setJavaSourceFilesToCompile(sources.map(PlainVirtualFileConverter.converter.toPath(_).toFile), project)
      try {
        ProductExposer.showJavaCompilationProducts(project)
        scalaJavaBuilder.build(INCREMENTAL_BUILD, new java.util.HashMap(), monitor)
      } finally {
        BuildManagerStore.INSTANCE.setJavaSourceFilesToCompile(null, project)
        ProductExposer.hideJavaCompilationProductsIfCompilationFailed(project)
      }

      refresh()
    }
    val javaProblems: Seq[IMarker] = project.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, false, IResource.DEPTH_INFINITE)
    Option(reporter).collect {
      case reporter: SbtBuildReporter =>
        javaProblems.foreach { marker =>
          reporter.riseJavaErrorOrWarning(marker.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO))
        }
    }
    !javaProblems.exists(_.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO) == IMarker.SEVERITY_ERROR)
  }

}
