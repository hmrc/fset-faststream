/*
 * Copyright 2023 HM Revenue & Customs
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

import factories.UUIDFactory
import model.OnlineTestCommands.PsiTestResult
import model.persisted.{ApplicationForOnlineTestPassMarkReport, SchemeEvaluationResult}
import model.report.onlinetestpassmark.TestResultsForOnlineTestPassMarkReportItemExamples
import model.{ApplicationRoute, EvaluationResults, SchemeId, Schemes}

import scala.util.Random

object ApplicationForOnlineTestPassMarkReportExamples extends Schemes {
  lazy val application1 = newApplicationForOnlineTestPassMarkReport(TestResultsForOnlineTestPassMarkReportItemExamples.testResults1)
  lazy val application2 = newApplicationForOnlineTestPassMarkReport(TestResultsForOnlineTestPassMarkReportItemExamples.testResults2)

  def emptyTests(n: Int): Seq[Option[PsiTestResult]] = Seq.fill[Option[PsiTestResult]](n)(None)

  lazy val applicationWithNoTestResult1 = newApplicationForOnlineTestPassMarkReport(
    TestResultsForOnlineTestPassMarkReportItem(emptyTests(4), emptyTests(2), videoInterview = None, siftTestResult = None, fsac = None,
      overallFsacScore = None, sift = None, fsb = None))
  lazy val applicationWithNoTestResult2 = newApplicationForOnlineTestPassMarkReport(
    TestResultsForOnlineTestPassMarkReportItem(emptyTests(4), emptyTests(2), videoInterview = None, siftTestResult = None, fsac = None,
      overallFsacScore = None, sift = None, fsb = None))

  def newApplicationForOnlineTestPassMarkReport(testsResult: TestResultsForOnlineTestPassMarkReportItem) =
    ApplicationForOnlineTestPassMarkReport(
      rnd("UserId"),
      UUIDFactory.generateUUID(),
      "phase1_tests_results_received",
      ApplicationRoute.Faststream,
      sdipDiversity = None,
      List(Commercial, DigitalDataTechnologyAndCyber),
      disability = None,
      gis = None,
      assessmentCentreAdjustments = None,
      testsResult,
      List(SchemeEvaluationResult(Commercial, EvaluationResults.Green.toString),
        SchemeEvaluationResult(DigitalDataTechnologyAndCyber, EvaluationResults.Green.toString)))

  def rnd(prefix: String) = s"$prefix-${Random.nextInt(100)}"
}
