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

package services.testdata.candidate

import javax.inject.{Inject, Provider, Singleton}
import model.ApplicationStatus._
import model.Exceptions.InvalidApplicationStatusAndProgressStatusException
import model.ProgressStatuses
import model.ProgressStatuses.{ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED, ASSESSMENT_CENTRE_AWAITING_ALLOCATION,
  ASSESSMENT_CENTRE_FAILED, ASSESSMENT_CENTRE_FAILED_NOTIFIED, ASSESSMENT_CENTRE_PASSED, ASSESSMENT_CENTRE_SCORES_ACCEPTED,
  ASSESSMENT_CENTRE_SCORES_ENTERED}
import model.testdata.CreateAdminData.CreateAdminData
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import services.testdata.admin.{AdminCreatedStatusGenerator, AdminUserBaseGenerator, AssessorCreatedStatusGenerator}
import services.testdata.candidate.assessmentcentre._
import services.testdata.candidate.fsb._
import services.testdata.candidate.onlinetests._
import services.testdata.candidate.onlinetests.phase1._
import services.testdata.candidate.onlinetests.phase2._
import services.testdata.candidate.onlinetests.phase3._
import services.testdata.candidate.sift._

@Singleton
class AdminStatusGeneratorFactory @Inject() (assessorCreatedStatusGenerator: AssessorCreatedStatusGenerator,
                                             adminCreatedStatusGenerator: AdminCreatedStatusGenerator) {
  def getGenerator(createData: CreateAdminData): AdminUserBaseGenerator = {
    createData.roles match {
      case createRoles: List[String] if createRoles.contains("assessor") => assessorCreatedStatusGenerator
      case _ => adminCreatedStatusGenerator
    }
  }
}

//scalastyle:off line.size.limit
@Singleton
class CandidateStatusGeneratorFactory @Inject() (registeredStatusGenerator: RegisteredStatusGenerator,
                                                 createdStatusGenerator: CreatedStatusGenerator,
                                                 inProgressPersonalDetailsStatusGenerator: InProgressPersonalDetailsStatusGenerator,
                                                 inProgressSchemePreferencesStatusGenerator: InProgressSchemePreferencesStatusGenerator,
                                                 inProgressAssistanceDetailsStatusGenerator: InProgressQuestionnaireStatusGenerator,
                                                 inProgressQuestionnaireStatusGenerator: InProgressQuestionnaireStatusGenerator,
                                                 inProgressPreviewStatusGenerator: InProgressPreviewStatusGenerator,
                                                 submittedStatusGenerator: SubmittedStatusGenerator,
                                                 withdrawnStatusGenerator: WithdrawnStatusGenerator,
                                                 fastPassAcceptedStatusGenerator: FastPassAcceptedStatusGenerator,
                                                 // Phase 1
                                                 phase1TestsInvitedStatusGenerator: Phase1TestsInvitedStatusGenerator,
                                                 phase1TestsStartedStatusGenerator: Phase1TestsStartedStatusGenerator,
                                                 phase1TestsExpiredFromInvitedStatusGenerator: Phase1TestsExpiredFromInvitedStatusGenerator,
                                                 phase1TestsExpiredFromStartedStatusGenerator: Phase1TestsExpiredFromStartedStatusGenerator,
                                                 phase1TestsCompletedStatusGenerator: Phase1TestsCompletedStatusGenerator,
                                                 phase1TestsResultsReceivedStatusGenerator: Phase1TestsResultsReceivedStatusGenerator,
                                                 phase1TestsPassedStatusGenerator: Phase1TestsPassedStatusGenerator,
                                                 phase1TestsPassedNotifiedStatusGenerator: Phase1TestsPassedNotifiedStatusGenerator,
                                                 phase1TestsFailedStatusGenerator: Phase1TestsFailedStatusGenerator,
                                                 phase1TestsFailedNotifiedStatusGenerator: Phase1TestsFailedNotifiedStatusGenerator,
                                                 // Phase2
                                                 phase2TestsInvitedStatusGenerator: Phase2TestsInvitedStatusGenerator,
                                                 phase2TestsStartedStatusGenerator: Phase2TestsStartedStatusGenerator,
                                                 phase2TestsExpiredFromStartedStatusGenerator: Phase2TestsExpiredFromStartedStatusGenerator,
                                                 phase2TestsExpiredFromInvitedStatusGenerator: Phase2TestsExpiredFromInvitedStatusGenerator,
                                                 phase2TestsCompletedStatusGenerator: Phase2TestsCompletedStatusGenerator,
                                                 phase2TestsResultsReceivedStatusGenerator: Phase2TestsResultsReceivedStatusGenerator,
                                                 phase2TestsPassedStatusGenerator: Phase2TestsPassedStatusGenerator,
                                                 phase2TestsFailedStatusGenerator: Phase2TestsFailedStatusGenerator,
                                                 phase2TestsFailedNotifiedStatusGenerator: Phase2TestsFailedNotifiedStatusGenerator,
                                                 // Phase 3
                                                 phase3TestsInvitedStatusGenerator: Phase3TestsInvitedStatusGenerator,
                                                 phase3TestsStartedStatusGenerator: Phase3TestsStartedStatusGenerator,
                                                 phase3TestsExpiredFromStartedStatusGenerator: Phase3TestsExpiredFromStartedStatusGenerator,
                                                 phase3TestsExpiredFromInvitedStatusGenerator: Phase3TestsExpiredFromInvitedStatusGenerator,
                                                 phase3TestsCompletedStatusGenerator: Phase3TestsCompletedStatusGenerator,
                                                 phase3TestsResultsReceivedStatusGenerator: Phase3TestsResultsReceivedStatusGenerator,
                                                 phase3TestsPassedStatusGenerator: Phase3TestsPassedStatusGenerator,
                                                 phase3TestsPassedNotifiedStatusGenerator: Phase3TestsPassedNotifiedStatusGenerator,
                                                 phase3TestsFailedStatusGenerator: Phase3TestsFailedStatusGenerator,
                                                 phase3TestsFailedNotifiedStatusGenerator: Phase3TestsFailedNotifiedStatusGenerator,
                                                 // Sift
                                                 siftEnteredStatusGenerator: SiftEnteredStatusGenerator,
                                                 siftFormsSubmittedStatusGenerator: SiftFormsSubmittedStatusGenerator,
                                                 siftCompleteStatusGenerator: SiftCompleteStatusGenerator,
                                                 // FSAC
                                                 assessmentCentreAwaitingAllocationStatusGenerator: AssessmentCentreAwaitingAllocationStatusGenerator,
                                                 assessmentCentreAllocationConfirmedStatusGenerator: AssessmentCentreAllocationConfirmedStatusGenerator,
                                                 assessmentCentreScoresEnteredStatusGenerator: AssessmentCentreScoresEnteredStatusGenerator,
                                                 assessmentCentreScoresAcceptedStatusGenerator: AssessmentCentreScoresAcceptedStatusGenerator,
                                                 assessmentCentrePassedStatusGenerator: AssessmentCentrePassedStatusGenerator,
                                                 assessmentCentreFailedStatusGenerator: AssessmentCentreFailedStatusGenerator,
                                                 assessmentCentreFailedNotifiedStatusGenerator: AssessmentCentreFailedNotifiedStatusGenerator,
                                                 // FSB
                                                 fsbAwaitingAllocationStatusGenerator: FsbAwaitingAllocationStatusGenerator,
                                                 fsbAllocationConfirmedStatusGenerator: FsbAllocationConfirmedStatusGenerator,
                                                 fsbResultEnteredStatusGenerator: FsbResultEnteredStatusGenerator,
                                                 fsbFailedStatusGenerator: FsbFailedStatusGenerator,
                                                 allFsbFailedStatusGenerator: AllFsbFailedStatusGenerator,
                                                 allFsbFailedNotifiedStatusGenerator: AllFsbFailedNotifiedStatusGenerator
                                                ) {
  //scalastyle:on line.size.limit
  // scalastyle:off cyclomatic.complexity method.length
  def getGenerator(generatorConfig: CreateCandidateData): BaseGenerator = {

    //scalastyle:off
    println("*** CandidateStatusGeneratorFactory.getGenerator - " +
      s"appStatus=${generatorConfig.statusData.applicationStatus}," +
      s"progressstatus=${generatorConfig.statusData.progressStatus}")

    val phase1StartTime = generatorConfig.phase1TestData.flatMap(_.start)
    val phase2StartTime = generatorConfig.phase2TestData.flatMap(_.start)
    val phase3StartTime = generatorConfig.phase3TestData.flatMap(_.start)

    (generatorConfig.statusData.applicationStatus, generatorConfig.statusData.progressStatus) match {
      case (appStatus, None) => appStatus match {
        case REGISTERED => registeredStatusGenerator
        case CREATED => createdStatusGenerator
        // IN_PROGRESS_PERSONAL_DETAILS should be deprecated, look below
        case IN_PROGRESS_PERSONAL_DETAILS => inProgressPersonalDetailsStatusGenerator
        // IN_PROGRESS_SCHEME_PREFERENCES should be deprecated, look below
        case IN_PROGRESS_SCHEME_PREFERENCES => inProgressSchemePreferencesStatusGenerator
        case IN_PROGRESS_QUESTIONNAIRE => inProgressQuestionnaireStatusGenerator
        // IN_PROGRESS_PREVIEW should be deprecated, look below
        case IN_PROGRESS_PREVIEW => inProgressPreviewStatusGenerator
        case SUBMITTED => submittedStatusGenerator
        case WITHDRAWN =>
          withdrawnStatusGenerator
        case FAST_PASS_ACCEPTED => fastPassAcceptedStatusGenerator
        case PHASE1_TESTS_PASSED => phase1TestsPassedStatusGenerator
        case PHASE1_TESTS_FAILED => phase1TestsFailedStatusGenerator
        case PHASE2_TESTS_PASSED => phase2TestsPassedStatusGenerator
        case PHASE2_TESTS_FAILED => phase2TestsFailedStatusGenerator
        case PHASE3_TESTS_PASSED => phase3TestsPassedStatusGenerator
        case PHASE3_TESTS_FAILED => phase3TestsFailedStatusGenerator
        case PHASE3_TESTS_PASSED_NOTIFIED => phase3TestsPassedNotifiedStatusGenerator
        case SIFT =>
          siftEnteredStatusGenerator
        case _ => throw InvalidApplicationStatusAndProgressStatusException(s"status ${generatorConfig.statusData.applicationStatus}" +
          s" and progress status ${generatorConfig.statusData.progressStatus} is not valid or not supported")
      }
      case (SUBMITTED, Some(ProgressStatuses.SUBMITTED)) => submittedStatusGenerator
      case (FAST_PASS_ACCEPTED, Some(ProgressStatuses.FAST_PASS_ACCEPTED)) => fastPassAcceptedStatusGenerator
      case (IN_PROGRESS, Some(ProgressStatuses.PERSONAL_DETAILS)) => inProgressPersonalDetailsStatusGenerator
      case (IN_PROGRESS, Some(ProgressStatuses.SCHEME_PREFERENCES)) => inProgressSchemePreferencesStatusGenerator
      case (IN_PROGRESS, Some(ProgressStatuses.ASSISTANCE_DETAILS)) => inProgressAssistanceDetailsStatusGenerator
      case (IN_PROGRESS, Some(ProgressStatuses.QUESTIONNAIRE_OCCUPATION)) => inProgressQuestionnaireStatusGenerator
      case (IN_PROGRESS, Some(ProgressStatuses.PREVIEW)) => inProgressPreviewStatusGenerator
      case (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_INVITED)) => phase1TestsInvitedStatusGenerator
      case (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_STARTED)) => phase1TestsStartedStatusGenerator
      case (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_EXPIRED)) =>
        if (phase1StartTime.isDefined) {
          phase1TestsExpiredFromStartedStatusGenerator
        } else {
          phase1TestsExpiredFromInvitedStatusGenerator
        }
      case (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_COMPLETED)) => phase1TestsCompletedStatusGenerator
      case (PHASE1_TESTS, Some(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED)) => phase1TestsResultsReceivedStatusGenerator
      // The next two cases are deprecated: PHASE1_TESTS_PASSED, PHASE1_TESTS_FAILED
      case (PHASE1_TESTS_PASSED, Some(ProgressStatuses.PHASE1_TESTS_PASSED)) => phase1TestsPassedStatusGenerator
      case (PHASE1_TESTS_PASSED_NOTIFIED, Some(ProgressStatuses.PHASE1_TESTS_PASSED_NOTIFIED)) => phase1TestsPassedNotifiedStatusGenerator
      case (PHASE1_TESTS_FAILED, Some(ProgressStatuses.PHASE1_TESTS_FAILED)) => phase1TestsFailedStatusGenerator
      case (PHASE1_TESTS_FAILED, Some(ProgressStatuses.PHASE1_TESTS_FAILED_NOTIFIED)) => phase1TestsFailedNotifiedStatusGenerator

      case (PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_INVITED)) => phase2TestsInvitedStatusGenerator
      case (PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_STARTED)) => phase2TestsStartedStatusGenerator
      case (PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_EXPIRED)) =>
        if (phase2StartTime.isDefined) {
          phase2TestsExpiredFromStartedStatusGenerator
        } else {
          phase2TestsExpiredFromInvitedStatusGenerator
        }
      case (PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_COMPLETED)) => phase2TestsCompletedStatusGenerator
      case (PHASE2_TESTS, Some(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED)) => phase2TestsResultsReceivedStatusGenerator
      // The next two cases are deprecated: PHASE2_TESTS_PASSED, PHASE2_TESTS_FAILED
      case (PHASE2_TESTS_PASSED, Some(ProgressStatuses.PHASE2_TESTS_PASSED)) => phase2TestsPassedStatusGenerator
      case (PHASE2_TESTS_FAILED, Some(ProgressStatuses.PHASE2_TESTS_FAILED)) => phase2TestsFailedStatusGenerator
      case (PHASE2_TESTS_FAILED, Some(ProgressStatuses.PHASE2_TESTS_FAILED_NOTIFIED)) => phase2TestsFailedNotifiedStatusGenerator

      case (PHASE3_TESTS, Some(ProgressStatuses.PHASE3_TESTS_INVITED)) => phase3TestsInvitedStatusGenerator
      case (PHASE3_TESTS, Some(ProgressStatuses.PHASE3_TESTS_STARTED)) => phase3TestsStartedStatusGenerator
      case (PHASE3_TESTS, Some(ProgressStatuses.PHASE3_TESTS_EXPIRED)) =>
        if (phase3StartTime.isDefined) {
          phase3TestsExpiredFromStartedStatusGenerator
        } else {
          phase3TestsExpiredFromInvitedStatusGenerator
        }
      case (PHASE3_TESTS, Some(ProgressStatuses.PHASE3_TESTS_COMPLETED)) => phase3TestsCompletedStatusGenerator
      case (PHASE3_TESTS, Some(ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED)) => phase3TestsResultsReceivedStatusGenerator
      // The next two cases are deprecated: PHASE3_TESTS_PASSED, PHASE3_TESTS_FAILED
      case (PHASE3_TESTS_PASSED, Some(ProgressStatuses.PHASE3_TESTS_PASSED)) => phase3TestsPassedStatusGenerator
      case (PHASE3_TESTS_PASSED_NOTIFIED, Some(ProgressStatuses.PHASE3_TESTS_PASSED_NOTIFIED)) => phase3TestsPassedNotifiedStatusGenerator
      case (PHASE3_TESTS_FAILED, Some(ProgressStatuses.PHASE3_TESTS_FAILED)) => phase3TestsFailedStatusGenerator
      case (PHASE3_TESTS_FAILED, Some(ProgressStatuses.PHASE3_TESTS_FAILED_NOTIFIED)) => phase3TestsFailedNotifiedStatusGenerator

      case (SIFT, Some(ProgressStatuses.SIFT_ENTERED)) => siftEnteredStatusGenerator
      case (SIFT, Some(ProgressStatuses.SIFT_READY)) => siftFormsSubmittedStatusGenerator
      case (SIFT, Some(ProgressStatuses.SIFT_COMPLETED)) => siftCompleteStatusGenerator

      case (ASSESSMENT_CENTRE, Some(ASSESSMENT_CENTRE_AWAITING_ALLOCATION)) => assessmentCentreAwaitingAllocationStatusGenerator
      case (ASSESSMENT_CENTRE, Some(ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED)) => assessmentCentreAllocationConfirmedStatusGenerator
      case (ASSESSMENT_CENTRE, Some(ASSESSMENT_CENTRE_SCORES_ENTERED)) => assessmentCentreScoresEnteredStatusGenerator
      case (ASSESSMENT_CENTRE, Some(ASSESSMENT_CENTRE_SCORES_ACCEPTED)) => assessmentCentreScoresAcceptedStatusGenerator
      case (ASSESSMENT_CENTRE, Some(ASSESSMENT_CENTRE_PASSED)) => assessmentCentrePassedStatusGenerator
      case (ASSESSMENT_CENTRE, Some(ASSESSMENT_CENTRE_FAILED)) => assessmentCentreFailedStatusGenerator
      case (ASSESSMENT_CENTRE, Some(ASSESSMENT_CENTRE_FAILED_NOTIFIED)) => assessmentCentreFailedNotifiedStatusGenerator

      case (FSB, Some(ProgressStatuses.FSB_AWAITING_ALLOCATION)) => fsbAwaitingAllocationStatusGenerator
      case (FSB, Some(ProgressStatuses.FSB_ALLOCATION_CONFIRMED)) => fsbAllocationConfirmedStatusGenerator
      case (FSB, Some(ProgressStatuses.FSB_RESULT_ENTERED)) => fsbResultEnteredStatusGenerator
      case (FSB, Some(ProgressStatuses.FSB_FAILED)) => fsbFailedStatusGenerator
      case (FSB, Some(ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED)) => allFsbFailedStatusGenerator
      case (FSB, Some(ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED_NOTIFIED)) => allFsbFailedNotifiedStatusGenerator

      case _ => throw InvalidApplicationStatusAndProgressStatusException(s"status ${generatorConfig.statusData.applicationStatus}" +
        s" and progress status ${generatorConfig.statusData.progressStatus} is not valid or not supported")
    }
  }
  // scalastyle:on cyclomatic.complexity
}

trait ApplicationStatusOnlyForTest {
  this: Enumeration =>
  val REGISTERED, IN_PROGRESS_PERSONAL_DETAILS, IN_PROGRESS_SCHEME_PREFERENCES,
  IN_PROGRESS_ASSISTANCE_DETAILS, IN_PROGRESS_QUESTIONNAIRE, IN_PROGRESS_PREVIEW = Value
}
