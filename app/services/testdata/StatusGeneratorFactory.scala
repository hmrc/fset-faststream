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
import model.command.testdata.GeneratorConfig
import services.testdata.onlinetests._
import services.testdata.onlinetests.phase1._
import services.testdata.onlinetests.phase2._
import services.testdata.onlinetests.phase3._

object StatusGeneratorFactory {
  // scalastyle:off cyclomatic.complexity method.length
  def getGenerator(generatorConfig: GeneratorConfig) = {

    val phase1StartTime = generatorConfig.phase1TestData.flatMap(_.start)
    val phase2StartTime = generatorConfig.phase2TestData.flatMap(_.start)
    val phase3StartTime = generatorConfig.phase3TestData.flatMap(_.start)

    (generatorConfig.statusData.applicationStatus, generatorConfig.statusData.progressStatus) match {
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
        if (phase1StartTime.isDefined) {
          Phase1TestsExpiredFromStartedStatusGenerator
        } else {
          Phase1TestsExpiredFromInvitedStatusGenerator
        }
      case (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_COMPLETED)) => Phase1TestsCompletedStatusGenerator
      case (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED)) => Phase1TestsResultsReceivedStatusGenerator
      case (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED)) => Phase1TestsPassedStatusGenerator

      case (PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_INVITED)) => Phase2TestsInvitedStatusGenerator
      case (PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_STARTED)) => Phase2TestsStartedStatusGenerator
      case (PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_EXPIRED)) =>
        if (phase2StartTime.isDefined) {
          Phase2TestsExpiredFromStartedStatusGenerator
        } else {
          Phase2TestsExpiredFromInvitedStatusGenerator
        }
      case (PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_COMPLETED)) => Phase2TestsCompletedStatusGenerator
      case (PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED)) => Phase2TestsResultsReceivedStatusGenerator
      case (PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_PASSED)) => Phase2TestsPassedStatusGenerator

      case (PHASE3_TESTS, Some(ProgressStatuses.PHASE3_TESTS_INVITED)) => Phase3TestsInvitedStatusGenerator
      case (PHASE3_TESTS, Some(ProgressStatuses.PHASE3_TESTS_STARTED)) => Phase3TestsStartedStatusGenerator
      case (PHASE3_TESTS, Some(ProgressStatuses.PHASE3_TESTS_EXPIRED)) =>
        if (phase3StartTime.isDefined) {
          Phase3TestsExpiredFromStartedStatusGenerator
        } else {
          Phase3TestsExpiredFromInvitedStatusGenerator
        }
      case (PHASE3_TESTS, Some(ProgressStatuses.PHASE3_TESTS_COMPLETED)) => Phase3TestsCompletedStatusGenerator
      case (PHASE3_TESTS, Some(ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED)) => Phase3TestsResultsReceivedStatusGenerator
      case (PHASE3_TESTS, Some(ProgressStatuses.PHASE3_TESTS_PASSED)) => Phase3TestsPassedStatusGenerator

      case _ => throw InvalidStatusException(s"${generatorConfig.statusData.applicationStatus} is not valid or not supported")
    }
  }
  // scalastyle:on cyclomatic.complexity
}

trait ApplicationStatusOnlyForTest {
  this: Enumeration =>
  val REGISTERED, IN_PROGRESS_PERSONAL_DETAILS, IN_PROGRESS_SCHEME_PREFERENCES,
  IN_PROGRESS_PARTNER_GRADUATE_PROGRAMMES, IN_PROGRESS_ASSISTANCE_DETAILS,
  IN_PROGRESS_QUESTIONNAIRE, IN_PROGRESS_PREVIEW, AWAITING_ALLOCATION = Value
}
