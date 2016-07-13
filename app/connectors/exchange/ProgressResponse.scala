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

package connectors.exchange

import models.UniqueIdentifier
import play.api.libs.json.Json

case class ProgressResponse(
  applicationId: UniqueIdentifier,
  personalDetails: Boolean,
  frameworksLocation: Boolean,
  assistance: Boolean,
  review: Boolean,
  questionnaire: List[String],
  submitted: Boolean,
  withdrawn: Boolean,
  onlineTestInvited: Boolean,
  onlineTestStarted: Boolean,
  onlineTestCompleted: Boolean,
  onlineTestExpired: Boolean,
  onlineTestAwaitingReevaluation: Boolean,
  onlineTestFailed: Boolean,
  onlineTestFailedNotified: Boolean,
  onlineTestAwaitingAllocation: Boolean,
  onlineTestAllocationConfirmed: Boolean,
  onlineTestAllocationUnconfirmed: Boolean,
  failedToAttend: Boolean,
  assessmentScores: AssessmentScores = AssessmentScores(),
  assessmentCentre: AssessmentCentre = AssessmentCentre()
)

case class AssessmentScores(
  entered: Boolean = false,
  accepted: Boolean = false
)

case class AssessmentCentre(
  awaitingReevaluation: Boolean = false,
  passed: Boolean = false,
  failed: Boolean = false
)

object ProgressResponse {
  implicit val assessmentScoresFormat = Json.format[AssessmentScores]
  implicit val assessmentCentreFormat = Json.format[AssessmentCentre]
  implicit val progressResponseFormat = Json.format[ProgressResponse]
}
