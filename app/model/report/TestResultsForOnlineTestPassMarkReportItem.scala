/*
 * Copyright 2020 HM Revenue & Customs
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

import model.OnlineTestCommands.PsiTestResult
import play.api.libs.json.{ Format, Json, OFormat }
import model.assessmentscores.AssessmentScoresAllExercises
import model.persisted.fsb.ScoresAndFeedback

case class TestResultsForOnlineTestPassMarkReportItem(phase1: Seq[Option[PsiTestResult]],
                                                      phase2: Seq[Option[PsiTestResult]],
                                                      videoInterview: Option[VideoInterviewTestResult],
                                                      siftTestResult: Option[PsiTestResult],
                                                      fsac: Option[AssessmentScoresAllExercises],
                                                      overallFsacScore: Option[Double],
                                                      sift: Option[SiftPhaseReportItem],
                                                      fsb: Option[ScoresAndFeedback])

object TestResultsForOnlineTestPassMarkReportItem {
  implicit val optionPsiTestResultFormat: Format[Option[PsiTestResult]] = Format.optionWithNull[PsiTestResult]
  implicit val testResultsForOnlineTestPassMarkReportItemFormat: OFormat[TestResultsForOnlineTestPassMarkReportItem] =
    Json.format[TestResultsForOnlineTestPassMarkReportItem]
}
