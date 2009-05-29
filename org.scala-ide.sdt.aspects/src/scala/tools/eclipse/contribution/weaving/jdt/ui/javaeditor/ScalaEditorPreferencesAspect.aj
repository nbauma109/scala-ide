/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor;

import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;

@SuppressWarnings("restriction")
public privileged aspect ScalaEditorPreferencesAspect {

  pointcut isSemanticHighlightingEnabled() :
    execution(boolean JavaEditor.isSemanticHighlightingEnabled());
  
  boolean around(ScalaEditorStub editor) :
    isSemanticHighlightingEnabled() && target(editor) {
    // Disable Java semantic highlighting for Scala source
    return false;
  }
}
