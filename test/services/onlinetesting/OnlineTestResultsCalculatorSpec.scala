/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services.onlinetesting

import model.EvaluationResults.{ Amber, Green, Red }
import model.SchemeId
import model.exchange.passmarksettings.PassMarkThreshold
import testkit.UnitSpec

class OnlineTestResultsCalculatorSpec extends UnitSpec {

  "evaluate test result" should {
    "give green result" in new OnlineTestResultsCalculator {
      evaluateTestResult(PassMarkThreshold(10.0, 20.0))(20.0) mustBe Green     // amber gap and score = pass
      evaluateTestResult(PassMarkThreshold(10.0, 20.0))(21.0) mustBe Green     // amber gap and score > pass
      evaluateTestResult(PassMarkThreshold(20.9, 20.9))(20.91) mustBe Green    // no amber gap and score > pass
      evaluateTestResult(PassMarkThreshold(20.96, 20.96))(20.96) mustBe Green  // no amber gap and score = pass
      evaluateTestResult(PassMarkThreshold(20.96, 20.96))(20.961) mustBe Green // no amber gap and score > pass
    }
    "give amber result" in new OnlineTestResultsCalculator {
      evaluateTestResult(PassMarkThreshold(10.0, 20.0))(15.0) mustBe Amber     // amber gap and score > fail and < pass
      evaluateTestResult(PassMarkThreshold(20.01, 20.02))(20.015) mustBe Amber // amber gap and score > fail and < pass
      evaluateTestResult(PassMarkThreshold(45.0, 45.01))(45.001) mustBe Amber  // amber gap and score > fail and < pass
      evaluateTestResult(PassMarkThreshold(10.0, 20.0))(10.0) mustBe Amber     // amber gap and score = fail
    }
    "give red result" in new OnlineTestResultsCalculator {
      evaluateTestResult(PassMarkThreshold(10.0, 20.0))(9.0) mustBe Red    // amber gap and score < fail
      evaluateTestResult(PassMarkThreshold(45.0, 45.0))(44.99) mustBe Red  // no amber gap and score < fail
      evaluateTestResult(PassMarkThreshold(45.0, 45.0))(44.999) mustBe Red // no amber gap and score < fail
    }
  }

  "combine test results" should {
    val scheme = SchemeId("Commercial")

    "give correct result" in new OnlineTestResultsCalculator {
      combineTestResults(scheme, Red) mustBe Red
      combineTestResults(scheme, Amber) mustBe Amber
      combineTestResults(scheme, Green) mustBe Green

      combineTestResults(scheme, Green, Red) mustBe Red
      combineTestResults(scheme, Red, Green) mustBe Red
      combineTestResults(scheme, Red, Red) mustBe Red

      combineTestResults(scheme, Green, Amber) mustBe Amber
      combineTestResults(scheme, Amber, Green) mustBe Amber
      combineTestResults(scheme, Amber, Amber) mustBe Amber

      combineTestResults(scheme, Green, Green) mustBe Green
    }
    "return exception" in new OnlineTestResultsCalculator {
      an[IllegalArgumentException] must be thrownBy combineTestResults(scheme)
    }
  }
}
