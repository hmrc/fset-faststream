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

package models

import java.util.UUID

import connectors.exchange.{ CivilServiceExperienceDetails, ProgressExamples }
import models.ApplicationData.ApplicationStatus

object CachedDataExample {
  val ActiveCandidateUser = CachedUser(UniqueIdentifier(UUID.randomUUID.toString), "firstName", "lastName",
    Some("preferredName"), email = "email@test.com", isActive = true, "")
  val LockedCandidateUser = CachedUser(UniqueIdentifier(UUID.randomUUID.toString), "firstName", "lastName",
    Some("preferredName"), email = "email@test.com", isActive = true, lockStatus = "LOCKED")
  val NonActiveCandidateUser = CachedUser(UniqueIdentifier(UUID.randomUUID.toString), "firstName", "lastName",
    Some("preferredName"), email = "email@test.com", isActive = false, "")

  val CreatedApplication = ApplicationData(
    UniqueIdentifier(UUID.randomUUID.toString),
    UniqueIdentifier(UUID.randomUUID.toString),
    ApplicationStatus.CREATED,
    ApplicationRoute.Faststream,
    ProgressExamples.InitialProgress,
    Some(CivilServiceExperienceDetails(applicable = false)),
    None,
    None
  )

  val fastPassRejectedInvitedToPhase1Application = ApplicationData(
    UniqueIdentifier(UUID.randomUUID.toString),
    UniqueIdentifier(UUID.randomUUID.toString),
    ApplicationStatus.SUBMITTED,
    ApplicationRoute.Faststream,
    ProgressExamples.Phase1TestsInvitedNotStarted,
    Some(CivilServiceExperienceDetails(applicable = true, fastPassReceived = Some(true), fastPassAccepted = Some(false))),
    None,
    None
  )

  val fastPassRejectedPhase1StartedApplication = ApplicationData(
    UniqueIdentifier(UUID.randomUUID.toString),
    UniqueIdentifier(UUID.randomUUID.toString),
    ApplicationStatus.PHASE1_TESTS,
    ApplicationRoute.Faststream,
    ProgressExamples.Phase1TestsStarted,
    Some(CivilServiceExperienceDetails(applicable = true, fastPassReceived = Some(true), fastPassAccepted = Some(false))),
    None,
    None
  )

  val InProgressInPersonalDetailsApplication = CreatedApplication.copy(applicationStatus = ApplicationStatus.IN_PROGRESS,
    progress = ProgressExamples.PersonalDetailsProgress)
  val InProgressInSchemePreferencesApplication = InProgressInPersonalDetailsApplication.copy(progress =
    ProgressExamples.SchemePreferencesProgress)
  val InProgressInAssistanceDetailsApplication = InProgressInSchemePreferencesApplication.copy(progress =
    ProgressExamples.AssistanceDetailsProgress)
  val InProgressInQuestionnaireApplication = InProgressInAssistanceDetailsApplication.copy(progress = ProgressExamples.QuestionnaireProgress)
  val InProgressInPreviewApplication = InProgressInQuestionnaireApplication.copy(progress = ProgressExamples.PreviewProgress)
  val SubmittedApplication = InProgressInPreviewApplication.copy(applicationStatus = ApplicationStatus.SUBMITTED,
    progress = ProgressExamples.SubmittedProgress)
  val EdipPhase1TestsPassedApplication = SubmittedApplication.copy(applicationStatus = ApplicationStatus.PHASE1_TESTS_PASSED,
    progress = ProgressExamples.Phase3TestsPassed, applicationRoute = ApplicationRoute.Edip)
  val SdipPhase1TestsPassedApplication = SubmittedApplication.copy(applicationStatus = ApplicationStatus.PHASE1_TESTS_PASSED,
    progress = ProgressExamples.Phase3TestsPassed, applicationRoute = ApplicationRoute.Sdip)
  val Phase1TestsPassedApplication = SubmittedApplication.copy(applicationStatus = ApplicationStatus.PHASE1_TESTS_PASSED,
    progress = ProgressExamples.Phase1TestsPassed)

  val Phase1TestsFailedApplication = SubmittedApplication.copy(applicationStatus = ApplicationStatus.PHASE1_TESTS_FAILED,
    progress = ProgressExamples.Phase1TestsFailed)
  val Phase1TestsExpiredApplication = SubmittedApplication.copy(applicationStatus = ApplicationStatus.PHASE1_TESTS,
    progress = ProgressExamples.SubmittedProgress.copy(phase1TestProgress = Phase1TestProgress(phase1TestsExpired = true)))
  val Phase2TestsFailedApplication = SubmittedApplication.copy(applicationStatus = ApplicationStatus.PHASE2_TESTS_FAILED,
    progress = ProgressExamples.Phase2TestsFailed)
  val Phase2TestsPassedApplication = SubmittedApplication.copy(applicationStatus = ApplicationStatus.PHASE2_TESTS_PASSED,
    progress = ProgressExamples.Phase2TestsPassed)
  val Phase3TestsPassedApplication = SubmittedApplication.copy(applicationStatus = ApplicationStatus.PHASE3_TESTS_PASSED,
    progress = ProgressExamples.Phase3TestsPassed)
  val Phase3TestsFailedApplication = SubmittedApplication.copy(applicationStatus = ApplicationStatus.PHASE3_TESTS_FAILED,
    progress = ProgressExamples.Phase3TestsFailed)

  val SiftApplication = Phase3TestsPassedApplication.copy(applicationStatus = ApplicationStatus.SIFT, progress = ProgressExamples.SiftEntered)

  val WithdrawApplication = SubmittedApplication.copy(applicationStatus = ApplicationStatus.WITHDRAWN,
    progress = ProgressExamples.WithdrawnAfterSubmitProgress)
  val EdipWithdrawnPhase1TestsPassedApplication = SubmittedApplication.copy(applicationStatus = ApplicationStatus.WITHDRAWN,
    progress = ProgressExamples.Phase3TestsPassed, applicationRoute = ApplicationRoute.Edip)
  val SdipWithdrawnPhase1TestsPassedApplication = SubmittedApplication.copy(applicationStatus = ApplicationStatus.WITHDRAWN,
    progress = ProgressExamples.Phase3TestsPassed, applicationRoute = ApplicationRoute.Sdip)
  val WithdrawnPhase3TestsPassedApplication = SubmittedApplication.copy(applicationStatus = ApplicationStatus.WITHDRAWN,
    progress = ProgressExamples.Phase3TestsPassed)
  val WithdrawnSiftApplication = SiftApplication.copy(applicationStatus = ApplicationStatus.WITHDRAWN)
  val ActiveCandidate = CachedData(ActiveCandidateUser, Some(CreatedApplication))
}
