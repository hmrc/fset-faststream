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

package connectors.exchange

import play.api.libs.json.Json

case class AssessmentCentre(
    awaitingAllocation: Boolean = false,
    allocationUnconfirmed: Boolean = false,
    allocationConfirmed: Boolean = false,
    failedToAttend: Boolean = false,
    scoresEntered: Boolean = false,
    scoresAccepted: Boolean = false,
    awaitingReevaluation: Boolean = false,
    passed: Boolean = false,
    failed: Boolean = false,
    failedSdipGreen: Boolean = false,
    failedNotified: Boolean = false
)

case class Fsb(
    awaitingAllocation: Boolean = false,
    allocationConfirmed: Boolean = false,
    allocationUnconfirmed: Boolean = false,
    failedToAttend: Boolean = false,
    resultEntered: Boolean = false,
    passed: Boolean = false,
    failed: Boolean = false,
    allFailed: Boolean = false,
    allFailedNotified: Boolean = false
)

case class Phase1ProgressResponse(
    phase1TestsInvited: Boolean = false,
    phase1TestsFirstReminder: Boolean = false,
    phase1TestsSecondReminder: Boolean = false,
    phase1TestsStarted: Boolean = false,
    phase1TestsCompleted: Boolean = false,
    phase1TestsExpired: Boolean = false,
    phase1TestsResultsReady: Boolean = false,
    phase1TestsResultsReceived: Boolean = false,
    phase1TestsPassed: Boolean = false,
    phase1TestsFailed: Boolean = false,
    sdipFSFailed: Boolean = false,
    sdipFSFailedNotified: Boolean = false,
    sdipFSSuccessful: Boolean = false,
    phase1TestsFailedSdipAmber: Boolean = false
)

case class Phase2ProgressResponse(
    phase2TestsInvited: Boolean = false,
    phase2TestsFirstReminder: Boolean = false,
    phase2TestsSecondReminder: Boolean = false,
    phase2TestsStarted: Boolean = false,
    phase2TestsCompleted: Boolean = false,
    phase2TestsExpired: Boolean = false,
    phase2TestsResultsReady: Boolean = false,
    phase2TestsResultsReceived: Boolean = false,
    phase2TestsPassed: Boolean = false,
    phase2TestsFailed: Boolean = false,
    phase2TestsFailedSdipAmber: Boolean = false
)

case class Phase3ProgressResponse(
    phase3TestsInvited: Boolean = false,
    phase3TestsFirstReminder: Boolean = false,
    phase3TestsSecondReminder: Boolean = false,
    phase3TestsStarted: Boolean = false,
    phase3TestsCompleted: Boolean = false,
    phase3TestsExpired: Boolean = false,
    phase3TestsResultsReceived: Boolean = false,
    phase3TestsPassed: Boolean = false,
    phase3TestsFailed: Boolean = false,
    phase3TestsFailedSdipAmber: Boolean = false
)

case class SiftProgressResponse(
    siftEntered: Boolean = false,
    siftTestStarted: Boolean = false,
    siftReady: Boolean = false,
    siftCompleted: Boolean = false,
    siftExpired: Boolean = false,
    sdipFailedAtSift: Boolean = false,
    failedAtSift: Boolean = false,
    failedAtSiftNotified: Boolean = false
)

case class JobOfferProgressResponse(
    eligible: Boolean = false,
    eligibleNotified: Boolean = false
)

case class ProgressResponse(
    applicationId: String,
    personalDetails: Boolean = false,
    schemePreferences: Boolean = false,
    assistanceDetails: Boolean = false,
    preview: Boolean = false,
    questionnaire: List[String] = Nil,
    submitted: Boolean = false,
    withdrawn: Boolean = false,
    eligibleForJobOffer: JobOfferProgressResponse = JobOfferProgressResponse(),
    phase1ProgressResponse: Phase1ProgressResponse = Phase1ProgressResponse(),
    phase2ProgressResponse: Phase2ProgressResponse = Phase2ProgressResponse(),
    phase3ProgressResponse: Phase3ProgressResponse = Phase3ProgressResponse(),
    siftProgressResponse: SiftProgressResponse = SiftProgressResponse(),
    exported: Boolean = false,
    updateExported: Boolean = false,
    assessmentCentre: AssessmentCentre = AssessmentCentre(),
    fsb: Fsb = Fsb()
)

object ProgressResponse {
  implicit val assessmentCentreFormat = Json.format[AssessmentCentre]
  implicit val fsbFormat = Json.format[Fsb]
  implicit val eligibleForJobOfferFormat = Json.format[JobOfferProgressResponse]
  implicit val phase1ProgressResponseFormat =
    Json.format[Phase1ProgressResponse]
  implicit val phase2ProgressResponseFormat =
    Json.format[Phase2ProgressResponse]
  implicit val phase3ProgressResponseFormat =
    Json.format[Phase3ProgressResponse]
  implicit val siftProgressResponseFormat = Json.format[SiftProgressResponse]
  implicit val progressResponseFormat = Json.format[ProgressResponse]
}
