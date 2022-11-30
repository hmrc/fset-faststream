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

package model.report.onlinetestpassmark

import model.OnlineTestCommands.PsiTestResult
import model.persisted.SchemeEvaluationResult
import model.{ ApplicationRoute, EvaluationResults, SchemeId }
import model.report.{ ApplicationForOnlineTestPassMarkReportItem, TestResultsForOnlineTestPassMarkReportItem }

import scala.util.Random

object ApplicationForOnlineTestPassMarkReportItemExamples {
  lazy val application1 = newApplicationForOnlineTestPassMarkReportItem(TestResultsForOnlineTestPassMarkReportItemExamples.testResults1)
  lazy val application2 = newApplicationForOnlineTestPassMarkReportItem(TestResultsForOnlineTestPassMarkReportItemExamples.testResults2)

  def emptyTests(n: Int): Seq[Option[PsiTestResult]] = Seq.fill[Option[PsiTestResult]](n)(None)

  lazy val applicationWithNoTestResult1 = newApplicationForOnlineTestPassMarkReportItem(
    TestResultsForOnlineTestPassMarkReportItem(phase1 = emptyTests(4), phase2 = emptyTests(2), videoInterview = None, siftTestResult = None,
      fsac = None, overallFsacScore = None, sift = None, fsb = None))
  lazy val applicationWithNoTestResult2 = newApplicationForOnlineTestPassMarkReportItem(
    TestResultsForOnlineTestPassMarkReportItem(phase1 = emptyTests(4), phase2 = emptyTests(2), videoInterview = None, siftTestResult = None,
      fsac = None, overallFsacScore = None, sift = None, fsb = None))

  def newApplicationForOnlineTestPassMarkReportItem(testsResult: TestResultsForOnlineTestPassMarkReportItem) =
    ApplicationForOnlineTestPassMarkReportItem(
      "appId",
      "phase1_tests_results_received",
      ApplicationRoute.Faststream,
      sdipDiversity = None,
      List(SchemeId("Commercial"), SchemeId("CyberSecurity")),
      disability = None,
      gis = None,
      onlineAdjustments = None,
      assessmentCentreAdjustments = None,
      testsResult,
      List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("CyberSecurity"), EvaluationResults.Green.toString)))

  def rnd(prefix: String) = s"$prefix-${Random.nextInt(100)}"
}
