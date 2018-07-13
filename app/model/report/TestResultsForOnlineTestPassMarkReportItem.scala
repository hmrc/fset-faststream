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

package model.report

import model.OnlineTestCommands.TestResult
import play.api.libs.json.Json
import model.OnlineTestCommands.Implicits._
import model.assessmentscores.AssessmentScoresAllExercises
import model.persisted.fsb.ScoresAndFeedback

case class TestResultsForOnlineTestPassMarkReportItem(
                                                      behavioural: Option[TestResult],
                                                      situational: Option[TestResult],
                                                      etray: Option[TestResult],
                                                      videoInterview: Option[VideoInterviewTestResult],
                                                      siftTestResult: Option[TestResult],
                                                      fsac: Option[AssessmentScoresAllExercises],
                                                      overallFsacScore: Option[Double],
                                                      sift: Option[SiftPhaseReportItem],
                                                      fsb: Option[ScoresAndFeedback]
                                                     )

object TestResultsForOnlineTestPassMarkReportItem {
  implicit val testResultsForOnlineTestPassMarkReportItemFormat = Json.format[TestResultsForOnlineTestPassMarkReportItem]
}
