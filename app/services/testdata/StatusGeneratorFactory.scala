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

import model.ApplicationStatus._
import model.Exceptions.InvalidStatusException
import model.ProgressStatuses
import model.ProgressStatuses.ProgressStatus

object StatusGeneratorFactory {
  // scalastyle:off cyclomatic.complexity
  def getGenerator(applicationStatus: ApplicationStatus, progressStatus: Option[ProgressStatus], generatorConfig: GeneratorConfig) = {

    (applicationStatus, progressStatus) match {
      case (appStatus, None) => appStatus match {
        case REGISTERED => RegisteredStatusGenerator
        case CREATED => CreatedStatusGenerator
        case IN_PROGRESS_PERSONAL_DETAILS => InProgressPersonalDetailsStatusGenerator
        case IN_PROGRESS_SCHEME_PREFERENCES => InProgressSchemePreferencesStatusGenerator
        case IN_PROGRESS_PARTNER_GRADUATE_PROGRAMMES => InProgressPartnerGraduateProgrammesStatusGenerator
        case IN_PROGRESS_ASSISTANCE_DETAILS => InProgressAssistanceDetailsStatusGenerator
        case IN_PROGRESS_QUESTIONNAIRE => InProgressQuestionnaireStatusGenerator
        case IN_PROGRESS_PREVIEW => InProgressPreviewStatusGenerator
        case SUBMITTED => SubmittedStatusGenerator
        case AWAITING_ALLOCATION => AwaitingAllocationStatusGenerator
        case ALLOCATION_CONFIRMED => AllocationStatusGenerator
        case ALLOCATION_UNCONFIRMED => AllocationStatusGenerator
        case FAILED_TO_ATTEND => FailedToAttendStatusGenerator
        case ASSESSMENT_SCORES_ENTERED => AssessmentScoresEnteredStatusGenerator
        case ASSESSMENT_SCORES_ACCEPTED => AssessmentScoresAcceptedStatusGenerator
        case AWAITING_ASSESSMENT_CENTRE_RE_EVALUATION => AwaitingAssessmentCentreReevalationStatusGenerator
        case ASSESSMENT_CENTRE_PASSED => AssessmentCentrePassedStatusGenerator
        case ASSESSMENT_CENTRE_FAILED => AssessmentCentreFailedStatusGenerator
        case ASSESSMENT_CENTRE_PASSED_NOTIFIED => AssessmentCentrePassedNotifiedStatusGenerator
        case ASSESSMENT_CENTRE_FAILED_NOTIFIED => AssessmentCentreFailedNotifiedStatusGenerator
        case WITHDRAWN => WithdrawnStatusGenerator
      }
      case (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_INVITED)) => Phase1TestsInvitedStatusGenerator
      case (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_STARTED)) => Phase1TestsStartedStatusGenerator
      case (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_EXPIRED)) =>
        if (generatorConfig.phase1StartTime.isDefined) {
          Phase1TestsExpiredFromStartedStatusGenerator
        } else {
          Phase1TestsExpiredFromInvitedStatusGenerator
        }
      case (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_COMPLETED)) => Phase1TestsCompletedStatusGenerator
      case (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED)) => Phase1TestsResultsReceivedStatusGenerator
      case (PHASE3_TESTS, Some(ProgressStatuses.PHASE3_TESTS_INVITED)) => Phase3TestsInvitedStatusGenerator
      case _ => throw InvalidStatusException(s"$applicationStatus is not valid or not supported")
    }
  }
  // scalastyle:on cyclomatic.complexity
}
