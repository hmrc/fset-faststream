/*
 * Copyright 2016 HM Revenue & Customs
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

package model.report

import model.SchemeType
import model.persisted.ApplicationForOnlineTestPassMarkReport

import scala.util.Random

object ApplicationForOnlineTestPassMarkReportExamples {
  lazy val application1 = newApplicationForOnlineTestPassMarkReport(TestResultsForOnlineTestPassMarkReportItemExamples.testResults1)
  lazy val application2 = newApplicationForOnlineTestPassMarkReport(TestResultsForOnlineTestPassMarkReportItemExamples.testResults2)

  lazy val applicationWithNoTestResult1 = newApplicationForOnlineTestPassMarkReport(
    TestResultsForOnlineTestPassMarkReportItem(None, None, None))
  lazy val applicationWithNoTestResult2 = newApplicationForOnlineTestPassMarkReport(
    TestResultsForOnlineTestPassMarkReportItem(None, None, None))

  def newApplicationForOnlineTestPassMarkReport(testsResult: TestResultsForOnlineTestPassMarkReportItem) =
    ApplicationForOnlineTestPassMarkReport(
      rnd("AppId"),
      "phase1_tests_results_received",
      List(SchemeType.Commercial, SchemeType.DigitalAndTechnology),
      None,
      None,
      None,
      None,
      testsResult)

  def rnd(prefix: String) = s"$prefix-${Random.nextInt(100)}"
}
