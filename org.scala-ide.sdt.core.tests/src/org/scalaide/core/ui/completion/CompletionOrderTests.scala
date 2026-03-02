package org.scalaide.core.ui.completion

import org.junit.Test
import org.scalaide.core.IScalaPlugin

object CompletionOrderTests extends CompletionTests

class CompletionOrderTests {

  import CompletionOrderTests._
  private val includeClassManifest: Boolean = IScalaPlugin().shortScalaVersion != "2.13"
  private def expectedClassCompletions(top: String): Seq[String] =
    if (includeClassManifest) Seq(top, "ClassManifest", "Class")
    else Seq(top, "Class")

  @Test
  def completeFieldOnTop() = """
    package completeFieldOnTop
    object X extends App {
      val ClassName = ""
      def f(name: String) = name
      f(Class^)
    }
  """ becomes """
    package completeFieldOnTop
    object X extends App {
      val ClassName = ""
      def f(name: String) = name
      f(ClassName^)
    }
  """ after Completion("ClassName",
      expectedCompletions = expectedClassCompletions("ClassName"),
      respectOrderOfExpectedCompletions = true)


  @Test
  def completeMethodOnTop() = """
    package completeMethodOnTop
    object X extends App {
      def ClassName = ""
      def f(name: String) = name
      f(Class^)
    }
  """ becomes """
    package completeMethodOnTop
    object X extends App {
      def ClassName = ""
      def f(name: String) = name
      f(ClassName^)
    }
  """ after Completion("ClassName: String",
      expectedCompletions = expectedClassCompletions("ClassName: String"),
      respectOrderOfExpectedCompletions = true)
}
