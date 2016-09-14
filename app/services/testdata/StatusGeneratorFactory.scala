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

package services.testdata

import model.{ ApplicationStatus, ApplicationStatuses }
import model.Exceptions.InvalidStatusException
import model.ProgressStatuses.{ PHASE1_TESTS_STARTED, ProgressStatus }

object StatusGeneratorFactory {
  // scalastyle:off cyclomatic.complexity
  def getGenerator(applicationStatus: String, progressStatus: Option[ProgressStatus]) = {

    // Legacy until all application statuses are migrated
    val phase1TestsAppStatus = ApplicationStatus.PHASE1_TESTS.toString

    (applicationStatus, progressStatus) match {
      case (appStatus, None) => appStatus match {
        case "REGISTERED" => RegisteredStatusGenerator
        case ApplicationStatuses.Created => CreatedStatusGenerator
        case "IN_PROGRESS_PERSONAL_DETAILS" => InProgressPersonalDetailsStatusGenerator
        case "IN_PROGRESS_SCHEME_PREFERENCES" => InProgressSchemePreferencesStatusGenerator
        case "IN_PROGRESS_PARTNER_GRADUATE_PROGRAMMES" => InProgressPartnerGraduateProgrammesStatusGenerator
        case "IN_PROGRESS_ASSISTANCE_DETAILS" => InProgressAssistanceDetailsStatusGenerator
        case "IN_PROGRESS_QUESTIONNAIRE" => InProgressQuestionnaireStatusGenerator
        case "IN_PROGRESS_PREVIEW" => InProgressPreviewStatusGenerator
        case ApplicationStatuses.Submitted => SubmittedStatusGenerator
        // TODO: in faststream
        // case ApplicationStatuses.OnlineTestInvited => OnlineTestInvitedStatusGenerator
        // case ApplicationStatuses.OnlineTestExpired => OnlineTestExpiredStatusGenerator
        // case ApplicationStatuses.AwaitingOnlineTestReevaluation => AwaitingOnlineTestReevaluationStatusGenerator
        //case ApplicationStatuses.OnlineTestFailed => OnlineTestFailedStatusGenerator
        //case ApplicationStatuses.OnlineTestFailedNotified => OnlineTestFailedNotifiedStatusGenerator
        case ApplicationStatuses.AwaitingAllocation => AwaitingAllocationStatusGenerator
        case ApplicationStatuses.AllocationConfirmed => AllocationStatusGenerator
        case ApplicationStatuses.AllocationUnconfirmed => AllocationStatusGenerator
        case ApplicationStatuses.FailedToAttend => FailedToAttendStatusGenerator
        case ApplicationStatuses.AssessmentScoresEntered => AssessmentScoresEnteredStatusGenerator
        case ApplicationStatuses.AssessmentScoresAccepted => AssessmentScoresAcceptedStatusGenerator
        case ApplicationStatuses.AwaitingAssessmentCentreReevaluation => AwaitingAssessmentCentreReevalationStatusGenerator
        case ApplicationStatuses.AssessmentCentrePassed => AssessmentCentrePassedStatusGenerator
        case ApplicationStatuses.AssessmentCentreFailed => AssessmentCentreFailedStatusGenerator
        case ApplicationStatuses.AssessmentCentrePassedNotified => AssessmentCentrePassedNotifiedStatusGenerator
        case ApplicationStatuses.AssessmentCentreFailedNotified => AssessmentCentreFailedNotifiedStatusGenerator
        case ApplicationStatuses.Withdrawn => WithdrawnStatusGenerator
      }
      case (phase1TestsAppStatus, Some(PHASE1_TESTS_STARTED)) => Phase1TestsStartedStatusGenerator
      case _ => throw InvalidStatusException(s"$applicationStatus is not valid or not supported")
    }
  }
  // scalastyle:on cyclomatic.complexity
}
