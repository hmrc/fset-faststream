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

package model.command

import play.api.libs.json.Json

case class OnlineTestProgressResponse(
                                       onlineTestInvited: Boolean = false,
                                       onlineTestStarted: Boolean = false,
                                       onlineTestCompleted: Boolean = false,
                                       onlineTestExpired: Boolean = false,
                                       onlineTestAwaitingReevaluation: Boolean = false,
                                       onlineTestFailed: Boolean = false,
                                       onlineTestFailedNotified: Boolean = false,
                                       onlineTestAwaitingAllocation: Boolean = false,
                                       onlineTestAllocationConfirmed: Boolean = false,
                                       onlineTestAllocationUnconfirmed: Boolean = false
                                     )

case class AssessmentScores(
                             entered: Boolean = false,
                             accepted: Boolean = false
                           )

case class AssessmentCentre(
                             awaitingReevaluation: Boolean = false,
                             passed: Boolean = false,
                             passedNotified: Boolean = false,
                             failed: Boolean = false,
                             failedNotified: Boolean = false
                           )


case class ProgressResponse(
                             applicationId: String,
                             personalDetails: Boolean = false,
                             schemePreferences: Boolean = false,
                             partnerGraduateProgrammes: Boolean = false,
                             assistanceDetails: Boolean = false,
                             preview: Boolean = false,
                             questionnaire: List[String] = Nil,
                             submitted: Boolean = false,
                             withdrawn: Boolean = false,
                             onlineTest: OnlineTestProgressResponse = OnlineTestProgressResponse(),
                             failedToAttend: Boolean = false,
                             assessmentScores: AssessmentScores = AssessmentScores(),
                             assessmentCentre: AssessmentCentre = AssessmentCentre()
                           )


object ProgressResponse {
  implicit val onlineTestProgressResponseFormat = Json.format[OnlineTestProgressResponse]
  implicit val assessmentScoresFormat = Json.format[AssessmentScores]
  implicit val assessmentCentreFormat = Json.format[AssessmentCentre]
  implicit val progressResponseFormat = Json.format[ProgressResponse]
}
