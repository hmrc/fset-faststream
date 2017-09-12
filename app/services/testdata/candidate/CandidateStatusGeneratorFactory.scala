/*
 * Copyright 2017 HM Revenue & Customs
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

package services.testdata.candidate

import model.ApplicationStatus._
import model.Exceptions.InvalidApplicationStatusAndProgressStatusException
import model.ProgressStatuses
import model.ProgressStatuses.{ ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED, ASSESSMENT_CENTRE_AWAITING_ALLOCATION, ASSESSMENT_CENTRE_FAILED, ASSESSMENT_CENTRE_FAILED_NOTIFIED }
import model.testdata.CreateAdminData.CreateAdminData
import model.testdata.CreateCandidateData.CreateCandidateData
import services.testdata.admin._
import services.testdata.candidate.assessmentcentre._
import services.testdata.candidate.fsb._
import services.testdata.candidate.onlinetests._
import services.testdata.candidate.onlinetests.phase1._
import services.testdata.candidate.onlinetests.phase2._
import services.testdata.candidate.onlinetests.phase3._
import services.testdata.candidate.sift.{ SiftCompleteStatusGenerator, SiftEnteredStatusGenerator, SiftFormsSubmittedStatusGenerator }

object AdminStatusGeneratorFactory {
  def getGenerator(createData: CreateAdminData): AdminUserBaseGenerator = {
    createData.roles match {
      case createRoles: List[String] if createRoles.contains("assessor") => AssessorCreatedStatusGenerator
      case _ => AdminCreatedStatusGenerator
    }
  }
}

object CandidateStatusGeneratorFactory {


  // scalastyle:off cyclomatic.complexity method.length
  def getGenerator(generatorConfig: CreateCandidateData) = {

    val phase1StartTime = generatorConfig.phase1TestData.flatMap(_.start)
    val phase2StartTime = generatorConfig.phase2TestData.flatMap(_.start)
    val phase3StartTime = generatorConfig.phase3TestData.flatMap(_.start)

    (generatorConfig.statusData.applicationStatus, generatorConfig.statusData.progressStatus) match {
      case (appStatus, None) => appStatus match {
        case REGISTERED => RegisteredStatusGenerator
        case CREATED => CreatedStatusGenerator
        // IN_PROGRESS_PERSONAL_DETAILS should be deprecated, look below
        case IN_PROGRESS_PERSONAL_DETAILS => InProgressPersonalDetailsStatusGenerator
        // IN_PROGRESS_SCHEME_PREFERENCES should be deprecated, look below
        case IN_PROGRESS_SCHEME_PREFERENCES => InProgressSchemePreferencesStatusGenerator
        // IN_PROGRESS_PARTNER_GRADUATE_PROGRAMMES should be deprecatedc
        case IN_PROGRESS_PARTNER_GRADUATE_PROGRAMMES => InProgressPartnerGraduateProgrammesStatusGenerator
        case IN_PROGRESS_QUESTIONNAIRE => InProgressQuestionnaireStatusGenerator
        // IN_PROGRESS_PREVIEW should be deprecated, look below
        case IN_PROGRESS_PREVIEW => InProgressPreviewStatusGenerator
        case SUBMITTED => SubmittedStatusGenerator
        case WITHDRAWN => WithdrawnStatusGenerator
        case PHASE1_TESTS_PASSED => Phase1TestsPassedStatusGenerator
        case PHASE1_TESTS_FAILED => Phase1TestsFailedStatusGenerator
        case PHASE2_TESTS_PASSED => Phase2TestsPassedStatusGenerator
        case PHASE2_TESTS_FAILED => Phase2TestsFailedStatusGenerator
        case PHASE3_TESTS_PASSED => Phase3TestsPassedStatusGenerator
        case PHASE3_TESTS_FAILED => Phase3TestsFailedStatusGenerator
        case PHASE3_TESTS_PASSED_NOTIFIED => Phase3TestsPassedNotifiedStatusGenerator
        case SIFT => SiftEnteredStatusGenerator
        case _ => throw InvalidApplicationStatusAndProgressStatusException(s"status ${generatorConfig.statusData.applicationStatus}" +
          s" and progress status ${generatorConfig.statusData.progressStatus} is not valid or not supported")
      }
      case (SUBMITTED, Some(ProgressStatuses.SUBMITTED)) => SubmittedStatusGenerator
      case (IN_PROGRESS, Some(ProgressStatuses.PERSONAL_DETAILS)) => InProgressPersonalDetailsStatusGenerator
      case (IN_PROGRESS, Some(ProgressStatuses.SCHEME_PREFERENCES)) => InProgressSchemePreferencesStatusGenerator
      case (IN_PROGRESS, Some(ProgressStatuses.PARTNER_GRADUATE_PROGRAMMES)) => InProgressPartnerGraduateProgrammesStatusGenerator
      case (IN_PROGRESS, Some(ProgressStatuses.ASSISTANCE_DETAILS)) => InProgressAssistanceDetailsStatusGenerator
      case (IN_PROGRESS, Some(ProgressStatuses.PREVIEW)) => InProgressPreviewStatusGenerator
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
      // The next two cases are deprecated: PHASE1_TESTS_PASSED, PHASE1_TESTS_FAILED
      case (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED)) => Phase1TestsPassedStatusGenerator
      case (PHASE1_TESTS_PASSED_NOTIFIED, Some(ProgressStatuses.PHASE1_TESTS_PASSED_NOTIFIED)) => Phase1TestsPassedNotifiedStatusGenerator
      case (PHASE1_TESTS_FAILED, Some(ProgressStatuses.PHASE1_TESTS_FAILED)) => Phase1TestsFailedStatusGenerator
      case (PHASE1_TESTS_FAILED, Some(ProgressStatuses.PHASE1_TESTS_FAILED_NOTIFIED)) => Phase1TestsFailedNotifiedStatusGenerator

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
      // The next two cases are deprecated: PHASE2_TESTS_PASSED, PHASE2_TESTS_FAILED
      case (PHASE2_TESTS_PASSED, Some(ProgressStatuses.PHASE2_TESTS_PASSED)) => Phase2TestsPassedStatusGenerator
      case (PHASE2_TESTS_FAILED, Some(ProgressStatuses.PHASE2_TESTS_FAILED)) => Phase2TestsFailedStatusGenerator
      case (PHASE2_TESTS_FAILED, Some(ProgressStatuses.PHASE2_TESTS_FAILED_NOTIFIED)) => Phase2TestsFailedNotifiedStatusGenerator

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
      // The next two cases are deprecated: PHASE3_TESTS_PASSED, PHASE3_TESTS_FAILED
      case (PHASE3_TESTS_PASSED, Some(ProgressStatuses.PHASE3_TESTS_PASSED)) => Phase3TestsPassedStatusGenerator
      case (PHASE3_TESTS_PASSED_NOTIFIED, Some(ProgressStatuses.PHASE3_TESTS_PASSED_NOTIFIED)) => Phase3TestsPassedNotifiedStatusGenerator
      case (PHASE3_TESTS_FAILED, Some(ProgressStatuses.PHASE3_TESTS_FAILED)) => Phase3TestsFailedStatusGenerator
      case (PHASE3_TESTS_FAILED, Some(ProgressStatuses.PHASE3_TESTS_FAILED_NOTIFIED)) => Phase3TestsFailedNotifiedStatusGenerator

      case (SIFT, Some(ProgressStatuses.SIFT_ENTERED)) => SiftEnteredStatusGenerator
      case (SIFT, Some(ProgressStatuses.SIFT_READY)) => SiftFormsSubmittedStatusGenerator
      case (SIFT, Some(ProgressStatuses.SIFT_COMPLETED)) => SiftCompleteStatusGenerator

      case (ASSESSMENT_CENTRE, Some(ASSESSMENT_CENTRE_AWAITING_ALLOCATION)) => AssessmentCentreAwaitingAllocationStatusGenerator
      case (ASSESSMENT_CENTRE, Some(ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED)) => AssessmentCentreAllocationConfirmedStatusGenerator
      case (ASSESSMENT_CENTRE, Some(ASSESSMENT_CENTRE_FAILED)) => AssessmentCentreFailedStatusGenerator
      case (ASSESSMENT_CENTRE, Some(ASSESSMENT_CENTRE_FAILED_NOTIFIED)) => AssessmentCentreFailedNotifiedStatusGenerator
      case (FSB, Some(ProgressStatuses.FSB_AWAITING_ALLOCATION)) => FsbAwaitingAllocationStatusGenerator
      case (FSB, Some(ProgressStatuses.FSB_ALLOCATION_CONFIRMED)) => FsbAllocationConfirmedStatusGenerator
      case (FSB, Some(ProgressStatuses.FSB_RESULT_ENTERED)) => FsbResultEnteredStatusGenerator
      case (FSB, Some(ProgressStatuses.FSB_FAILED)) => FsbFailedStatusGenerator
      case (FSB, Some(ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)) => AllFsbFailedStatusGenerator
      case (FSB, Some(ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED_NOTIFIED)) => AllFsbFailedNotifiedStatusGenerator

      case _ => throw InvalidApplicationStatusAndProgressStatusException(s"status ${generatorConfig.statusData.applicationStatus}" +
        s" and progress status ${generatorConfig.statusData.progressStatus} is not valid or not supported")
    }
  }
  // scalastyle:on cyclomatic.complexity
}

trait ApplicationStatusOnlyForTest {
  this: Enumeration =>
  val REGISTERED, IN_PROGRESS_PERSONAL_DETAILS, IN_PROGRESS_SCHEME_PREFERENCES,
  IN_PROGRESS_PARTNER_GRADUATE_PROGRAMMES, IN_PROGRESS_ASSISTANCE_DETAILS,
  IN_PROGRESS_QUESTIONNAIRE, IN_PROGRESS_PREVIEW = Value
}
