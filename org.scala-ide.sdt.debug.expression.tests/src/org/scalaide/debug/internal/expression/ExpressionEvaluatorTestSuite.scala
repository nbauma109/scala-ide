package org.scalaide.debug.internal.expression

import org.junit.runner.RunWith
import org.junit.runners.Suite
import org.scalaide.debug.internal.ui.completion.SimpleContentProposalProviderTest
import org.scalaide.debug.internal.expression.proxies.phases.PhasesTestSuite

@RunWith(classOf[Suite])
@Suite.SuiteClasses(Array(
  classOf[PhasesTestSuite],
  classOf[SimpleContentProposalProviderTest]
))
class ExpressionEvaluatorTestSuite
