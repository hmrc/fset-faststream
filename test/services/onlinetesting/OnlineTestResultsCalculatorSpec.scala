/*
 * Copyright 2017 HM Revenue & Customs
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
import model.exchange.passmarksettings.PassMarkThreshold
import testkit.UnitSpec

class OnlineTestResultsCalculatorSpec extends UnitSpec {

  "evaluate test result" should {
    "give green result" in new OnlineTestResultsCalculator {
      evaluateTestResult(PassMarkThreshold(10.0, 20.0))(Some(20.0)) mustBe Green
      evaluateTestResult(PassMarkThreshold(20.9, 20.9))(Some(20.91)) mustBe Green
      evaluateTestResult(PassMarkThreshold(20.96, 20.96))(Some(20.966)) mustBe Green
    }
    "give amber result" in new OnlineTestResultsCalculator {
      evaluateTestResult(PassMarkThreshold(10.0, 20.0))(Some(15.0)) mustBe Amber
      evaluateTestResult(PassMarkThreshold(20.01, 20.02))(Some(20.015)) mustBe Amber
      evaluateTestResult(PassMarkThreshold(45.0, 45.01))(Some(45.001)) mustBe Amber
    }
    "give red result" in new OnlineTestResultsCalculator {
      evaluateTestResult(PassMarkThreshold(10.0, 20.0))(Some(10.0)) mustBe Red
      evaluateTestResult(PassMarkThreshold(20.01, 20.02))(Some(20.01)) mustBe Red
      evaluateTestResult(PassMarkThreshold(45.0, 45.01))(Some(44.99)) mustBe Red
    }
    "throw exception" in new OnlineTestResultsCalculator {
      an[IllegalArgumentException] must be thrownBy evaluateTestResult(PassMarkThreshold(10.0, 20.0))(None)
    }
  }

  "combine test results" should {
    "give correct result" in new OnlineTestResultsCalculator {
      combineTestResults(Red) mustBe Red
      combineTestResults(Amber) mustBe Amber
      combineTestResults(Green) mustBe Green

      combineTestResults(Green, Red) mustBe Red
      combineTestResults(Red, Green) mustBe Red
      combineTestResults(Red, Red) mustBe Red

      combineTestResults(Green, Amber) mustBe Amber
      combineTestResults(Amber, Green) mustBe Amber
      combineTestResults(Amber, Amber) mustBe Amber

      combineTestResults(Green, Green) mustBe Green
    }
    "return exception" in new OnlineTestResultsCalculator {
      an[IllegalArgumentException] must be thrownBy combineTestResults()
    }
  }
}
