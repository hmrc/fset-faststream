/*
 * Copyright 2018 HM Revenue & Customs
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

package model.report.onlinetestpassmark

import model.persisted.SchemeEvaluationResult
import model.{ ApplicationRoute, EvaluationResults, SchemeId }
import model.report.{ ApplicationForOnlineTestPassMarkReportItem, TestResultsForOnlineTestPassMarkReportItem }

import scala.util.Random

object ApplicationForOnlineTestPassMarkReportItemExamples {
  lazy val application1 = newApplicationForOnlineTestPassMarkReportItem(TestResultsForOnlineTestPassMarkReportItemExamples.testResults1)
  lazy val application2 = newApplicationForOnlineTestPassMarkReportItem(TestResultsForOnlineTestPassMarkReportItemExamples.testResults2)

  lazy val applicationWithNoTestResult1 = newApplicationForOnlineTestPassMarkReportItem(
    TestResultsForOnlineTestPassMarkReportItem(None, None, None, None, None, None, None, None))
  lazy val applicationWithNoTestResult2 = newApplicationForOnlineTestPassMarkReportItem(
    TestResultsForOnlineTestPassMarkReportItem(None, None, None, None, None, None, None, None))

  def newApplicationForOnlineTestPassMarkReportItem(testsResult: TestResultsForOnlineTestPassMarkReportItem) =
    ApplicationForOnlineTestPassMarkReportItem(
      "phase1_tests_results_received",
      ApplicationRoute.Faststream,
      List(SchemeId("Commercial"), SchemeId("DigitalAndTechnology")),
      None,
      None,
      None,
      None,
      testsResult,
      List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), EvaluationResults.Green.toString)))

  def rnd(prefix: String) = s"$prefix-${Random.nextInt(100)}"
}
