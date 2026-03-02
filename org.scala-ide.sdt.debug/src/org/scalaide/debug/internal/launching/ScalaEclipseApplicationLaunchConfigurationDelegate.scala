package org.scalaide.debug.internal.launching

import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.jdt.launching.IVMRunner
import org.eclipse.jdt.launching.JavaRuntime
import org.eclipse.jdt.launching.JavaLaunchDelegate

/**
 * Launch configuration delegate starting Scala applications with the Scala debugger.
 */
class ScalaEclipseApplicationLaunchConfigurationDelegate extends JavaLaunchDelegate {

  override def getVMRunner(configuration: ILaunchConfiguration, mode: String): IVMRunner = {
    val vm = JavaRuntime.computeVMInstall(configuration)
    new StandardVMScalaDebugger(vm)
  }

}
