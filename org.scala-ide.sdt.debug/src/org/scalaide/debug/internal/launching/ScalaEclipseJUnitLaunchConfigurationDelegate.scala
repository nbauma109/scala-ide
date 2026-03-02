package org.scalaide.debug.internal.launching

import org.eclipse.jdt.junit.launcher.JUnitLaunchConfigurationDelegate

/**
 * Launch configuration delegate starting Scala applications with the Scala debugger.
 */
class ScalaEclipseJUnitLaunchConfigurationDelegate extends JUnitLaunchConfigurationDelegate
  with ScalaDebuggerForLaunchDelegate
