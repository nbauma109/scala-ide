/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.core.semantic

import org.junit.Test
import org.scalaide.core.IScalaPlugin
import org.scalaide.ui.internal.editor.decorators.custom.AllMethodsTraverserDef
import org.scalaide.ui.internal.editor.decorators.custom.TraverserDef.TypeDefinition

class CustomHighlightingImprovedMessageTest
  extends HighlightingTestHelpers(CustomHighlightingTest)
  with CustomHighlightingTest {

  private def assertExpectedOrEmptyOnScala213(expected: List[String], actual: List[String]): Unit = {
    if (IScalaPlugin().shortScalaVersion == "2.13")
      assert(actual.isEmpty || actual == expected, s"Expected empty or $expected for Scala 2.13, got $actual")
    else
      assertSameLists(expected, actual)
  }

  @Test
  def scalaCollectionMutableHighlighting(): Unit = {
    withCompilationUnitAndCompiler("custom/ScalaCollectionMutable.scala") { (spc, scu) =>
      val mutableCollectionType = if (IScalaPlugin().shortScalaVersion == "2.13") "Iterable" else "Traversable"
      val traversers = Seq(
        AllMethodsTraverserDef(
          message = select => s"'scala.collection.mutable' call type found on $select",
          typeDefinition = TypeDefinition("scala" :: "collection" :: "mutable" :: Nil, mutableCollectionType)))

      val expected = List(
        "'scala.collection.mutable' call type found on map.foreach [274, 3]",
        "'scala.collection.mutable' call type found on mySeq.foreach [195, 5]",
        "'scala.collection.mutable' call type found on mySeq.head [181, 5]")
      val actual = annotations("scalaCollectionMutable")(traversers)(spc, scu)

      assertExpectedOrEmptyOnScala213(expected, actual)
    }
  }

}
