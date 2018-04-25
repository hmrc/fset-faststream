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

import model.ApplicationStatus._
import model.ProgressStatuses.{ ELIGIBLE_FOR_JOB_OFFER => _, _ }
import model.command.ProgressResponse

trait ProgressStatusesReportLabels {

  import ProgressStatusesReportLabels._

  // scalastyle:off
  private def statusMaps(progress: ProgressResponse) = Seq(
    (progress.personalDetails, 10, PersonalDetailsCompletedProgress),
    (progress.schemePreferences, 20, SchemePreferencesCompletedProgress),
    (progress.partnerGraduateProgrammes, 25, PartnerGraduateProgrammesCompletedProgress),
    (progress.assistanceDetails, 30, AssistanceDetailsCompletedProgress),
    (progress.questionnaire.contains("start_questionnaire"), 40, StartDiversityQuestionnaireProgress),
    (progress.questionnaire.contains("diversity_questionnaire"), 50, DiversityQuestionsCompletedProgress),
    (progress.questionnaire.contains("education_questionnaire"), 60, EducationQuestionsCompletedProgress),
    (progress.questionnaire.contains("occupation_questionnaire"), 70, OccupationQuestionsCompletedProgress),
    (progress.preview, 80, PreviewCompletedProgress),
    (progress.submitted, 90, SubmittedProgress),
    (progress.fastPassAccepted, 91, FastPassAccepted),
    (progress.phase1ProgressResponse.phase1TestsInvited, 100, Phase1TestsInvited),
    (progress.phase1ProgressResponse.phase1TestsFirstReminder, 110, Phase1TestsFirstReminder),
    (progress.phase1ProgressResponse.phase1TestsSecondReminder, 120, Phase1TestsSecondReminder),
    (progress.phase1ProgressResponse.phase1TestsStarted, 130, Phase1TestsStarted),
    (progress.phase1ProgressResponse.phase1TestsCompleted, 140, Phase1TestsCompleted),
    (progress.phase1ProgressResponse.phase1TestsResultsReady, 150, Phase1TestsResultsReady),
    (progress.phase1ProgressResponse.phase1TestsResultsReceived, 160, Phase1TestsResultsReceived),
    (progress.phase1ProgressResponse.phase1TestsExpired, 170, Phase1TestsExpired),
    (progress.phase1ProgressResponse.phase1TestsFailedSdipAmber, 175, Phase1TestsFailedSdipAmber),
    (progress.phase1ProgressResponse.phase1TestsPassed, 180, Phase1TestsPassed),
    (progress.phase1ProgressResponse.phase1TestsSuccessNotified, 185, Phase1TestsPassedNotified),
    (progress.phase1ProgressResponse.phase1TestsFailed, 190, Phase1TestsFailed),
    (progress.phase1ProgressResponse.phase1TestsFailedNotified, 195, Phase1TestsFailedNotified),
    (progress.phase1ProgressResponse.sdipFSSuccessful, 181, SdipFaststreamPassed),
    (progress.phase1ProgressResponse.sdipFSSuccessfulNotified, 182, SdipFaststreamPassedNotified),
    (progress.phase1ProgressResponse.sdipFSFailed, 191, SdipFaststreamFailed),
    (progress.phase1ProgressResponse.sdipFSFailedNotified, 192, SdipFaststreamFailedNotified),

    (progress.phase2ProgressResponse.phase2TestsInvited, 200, Phase2TestsInvited),
    (progress.phase2ProgressResponse.phase2TestsFirstReminder, 210, Phase2TestsFirstReminder),
    (progress.phase2ProgressResponse.phase2TestsSecondReminder, 220, Phase2TestsSecondReminder),
    (progress.phase2ProgressResponse.phase2TestsStarted, 230, Phase2TestsStarted),
    (progress.phase2ProgressResponse.phase2TestsCompleted, 240, Phase2TestsCompleted),
    (progress.phase2ProgressResponse.phase2TestsResultsReady, 250, Phase2TestsResultsReady),
    (progress.phase2ProgressResponse.phase2TestsResultsReceived, 260, Phase2TestsResultsReceived),
    (progress.phase2ProgressResponse.phase2TestsExpired, 270, Phase2TestsExpired),
    (progress.phase2ProgressResponse.phase2TestsFailedSdipAmber, 275, Phase2TestsFailedSdipAmber),
    (progress.phase2ProgressResponse.phase2TestsPassed, 280, Phase2TestsPassed),
    (progress.phase2ProgressResponse.phase2TestsFailed, 290, Phase2TestsFailed),
    (progress.phase2ProgressResponse.phase2TestsFailedNotified, 295, Phase2TestsFailedNotified),

    (progress.phase3ProgressResponse.phase3TestsInvited, 305, Phase3TestsInvited),
    (progress.phase3ProgressResponse.phase3TestsFirstReminder, 310, Phase3TestsFirstReminder),
    (progress.phase3ProgressResponse.phase3TestsSecondReminder, 320, Phase3TestsSecondReminder),
    (progress.phase3ProgressResponse.phase3TestsStarted, 330, Phase3TestsStarted),
    (progress.phase3ProgressResponse.phase3TestsCompleted, 340, Phase3TestsCompleted),
    (progress.phase3ProgressResponse.phase3TestsResultsReceived, 350, Phase3TestsResultsReceived),
    (progress.phase3ProgressResponse.phase3TestsExpired, 360, Phase3TestsExpired),
    (progress.phase3ProgressResponse.phase3TestsFailedSdipAmber, 365, Phase3TestsFailedSdipAmber),
    (progress.phase3ProgressResponse.phase3TestsPassedWithAmber, 370, Phase3TestsPassedWithAmber),
    (progress.phase3ProgressResponse.phase3TestsPassed, 380, Phase3TestsPassed),
    (progress.phase3ProgressResponse.phase3TestsSuccessNotified, 385, Phase3TestsPassedNotified),
    (progress.phase3ProgressResponse.phase3TestsFailed, 390, Phase3TestsFailed),
    (progress.phase3ProgressResponse.phase3TestsFailedNotified, 395, Phase3TestsFailedNotified),

    (progress.siftProgressResponse.siftEntered, 400, SiftEntered),
    (progress.siftProgressResponse.siftFirstReminder, 401, SiftFirstReminder),
    (progress.siftProgressResponse.siftSecondReminder, 402, SiftSecondReminder),
    (progress.siftProgressResponse.siftTestInvited, 403, SiftTestInvited),
    (progress.siftProgressResponse.siftTestStarted, 404, SiftTestStarted),
    (progress.siftProgressResponse.siftTestCompleted, 405, SiftTestCompleted),
    (progress.siftProgressResponse.siftReady, 406, SiftReady),
    (progress.siftProgressResponse.siftCompleted, 407, SiftCompleted),
    (progress.siftProgressResponse.siftExpired, 408, SiftExpired),
    (progress.siftProgressResponse.siftFaststreamFailedSdipGreen, 409, SiftFaststreamFailedSdipGreen),
    (progress.siftProgressResponse.failedAtSift, 410, SiftFailed),
    (progress.siftProgressResponse.failedAtSiftNotified, 411, SiftFailedNotified),
    (progress.siftProgressResponse.sdipFailedAtSift, 412, SdipSiftFailed),

    (progress.assessmentCentre.awaitingAllocation, 420, AssessmentCentreAwaitingAllocation),
    (progress.assessmentCentre.allocationUnconfirmed, 423, AssessmentCentreAllocationUnconfirmed),
    (progress.assessmentCentre.allocationConfirmed, 426, AssessmentCentreAllocationConfirmed),
    (progress.assessmentCentre.scoresEntered, 429, AssessmentCentreScoresEntered),
    (progress.assessmentCentre.scoresAccepted, 432, AssessmentCentreScoresAccepted),
    (progress.assessmentCentre.failedToAttend, 435, AssessmentCentreFailedToAttend),
    (progress.assessmentCentre.awaitingReevaluation, 438, AssessmentCentreAwaitingReevaluation),
    (progress.assessmentCentre.failedSdipGreen, 440, AssessmentCentreFailedSdipGreenProgress),
    (progress.assessmentCentre.failedSdipGreenNotified, 442, AssessmentCentreFailedSdipGreenNotifiedProgress),
    (progress.assessmentCentre.failed, 444, AssessmentCentreFailedProgress),
    (progress.assessmentCentre.failedNotified, 450, AssessmentCentreFailedNotifiedProgress),
    (progress.assessmentCentre.passed, 460, AssessmentCentrePassedProgress),
    (progress.fsb.awaitingAllocation, 480, FsbAwaitingAllocation),
    (progress.fsb.allocationUnconfirmed, 485, FsbAllocationUnconfirmed),
    (progress.fsb.allocationConfirmed, 490, FsbAllocationConfirmed),
    (progress.fsb.failedToAttend, 495, FsbFailedToAttend),
    (progress.fsb.resultEntered, 500, FsbResultEntered),
    (progress.fsb.passed, 505, FsbPassed),
    (progress.fsb.failed, 510, FsbFailed),
    (progress.fsb.allFailed, 513, FsbAllFailed),
    (progress.fsb.allFailedNotified, 515, FsbAllFailedNotified),
    (progress.eligibleForJobOffer.eligible, 800, EligibleForJobOffer),
    (progress.eligibleForJobOffer.eligibleNotified, 801, EligibleForJobOfferNotified),
    (progress.withdrawn, 999, WithdrawnProgress),
    (progress.applicationArchived, 1000, ApplicationArchived)
  )
  // scalastyle:on

  def progressStatusNameInReports(progress: ProgressResponse): String = {
    val default = 0 -> ProgressStatusesReportLabels.RegisteredProgress

    type StatusMap = (Boolean, Int, String)
    type HighestStatus = (Int, String)

    def combineStatuses(statusMap: Seq[StatusMap]): HighestStatus = {
      statusMap.foldLeft(default) { (highest, current) =>
        val (highestWeighting, _) = highest
        current match {
          case (true, weighting, name) if weighting > highestWeighting =>
            weighting -> name
          case (_, _, _) =>
            highest
        }
      }
    }

    val (_, statusName) = combineStatuses(statusMaps(progress))

    statusName
  }
}

object ProgressStatusesReportLabels extends ProgressStatusesReportLabels {
  val RegisteredProgress = "registered"
  val PersonalDetailsCompletedProgress = "personal_details_completed"
  val SchemePreferencesCompletedProgress = "scheme_preferences_completed"
  val PartnerGraduateProgrammesCompletedProgress = "partner_graduate_programmes_completed"
  val AssistanceDetailsCompletedProgress = "assistance_details_completed"
  val PreviewCompletedProgress = "preview_completed"
  val StartDiversityQuestionnaireProgress = "start_diversity_questionnaire"
  val DiversityQuestionsCompletedProgress = "diversity_questions_completed"
  val EducationQuestionsCompletedProgress = "education_questions_completed"
  val OccupationQuestionsCompletedProgress = "occupation_questions_completed"
  val SubmittedProgress = "submitted"
  val WithdrawnProgress = "withdrawn"

  val SdipFaststreamPassed = "phase1_tests_sdip_faststream_passed"
  val SdipFaststreamPassedNotified = "phase1_tests_sdip_passed_notified"
  val SdipFaststreamFailed = "phase1_tests_sdip_failed"
  val SdipFaststreamFailedNotified = "phase1_tests_sdip_failed_notified"

  val Phase1TestsInvited = "phase1_tests_invited"
  val Phase1TestsFirstReminder = "phase1_tests_first_reminder"
  val Phase1TestsSecondReminder = "phase1_tests_second_reminder"
  val Phase1TestsStarted = "phase1_tests_started"
  val Phase1TestsCompleted = "phase1_tests_completed"
  val Phase1TestsExpired = "phase1_tests_expired"
  val Phase1TestsResultsReady = "phase1_tests_results_ready"
  val Phase1TestsResultsReceived = "phase1_tests_results_received"
  val Phase1TestsPassed = "phase1_tests_passed"
  val Phase1TestsFailed = "phase1_tests_failed"
  val Phase1TestsFailedNotified = "phase1_tests_failed_notified"
  val Phase1TestsPassedNotified = "phase1_tests_passed_notified"
  val Phase1TestsFailedSdipAmber = "phase1_tests_failed_sdip_amber"

  val Phase2TestsInvited = "phase2_tests_invited"
  val Phase2TestsFirstReminder = "phase2_tests_first_reminder"
  val Phase2TestsSecondReminder = "phase2_tests_second_reminder"
  val Phase2TestsStarted = "phase2_tests_started"
  val Phase2TestsCompleted = "phase2_tests_completed"
  val Phase2TestsExpired = "phase2_tests_expired"
  val Phase2TestsResultsReady = "phase2_tests_results_ready"
  val Phase2TestsResultsReceived = "phase2_tests_results_received"
  val Phase2TestsPassed = "phase2_tests_passed"
  val Phase2TestsFailed = "phase2_tests_failed"
  val Phase2TestsFailedNotified = "phase2_tests_failed_notified"
  val Phase2TestsFailedSdipAmber = "phase2_tests_failed_sdip_amber"

  val Phase3TestsInvited = "phase3_tests_invited"
  val Phase3TestsFirstReminder = "phase3_tests_first_reminder"
  val Phase3TestsSecondReminder = "phase3_tests_second_reminder"
  val Phase3TestsStarted = "phase3_tests_started"
  val Phase3TestsCompleted = "phase3_tests_completed"
  val Phase3TestsExpired = "phase3_tests_expired"
  val Phase3TestsResultsReceived = "phase3_tests_results_received"
  val Phase3TestsPassedWithAmber = "phase3_tests_passed_with_amber"
  val Phase3TestsPassed = "phase3_tests_passed"
  val Phase3TestsFailed = "phase3_tests_failed"
  val Phase3TestsFailedNotified = "phase3_tests_failed_notified"
  val Phase3TestsPassedNotified = "phase3_tests_passed_notified"
  val Phase3TestsFailedSdipAmber = "phase3_tests_failed_sdip_amber"

  val AssessmentCentreAwaitingAllocation = "assessment_centre_awaiting_allocation"
  val AssessmentCentreAllocationConfirmed = "assessment_centre_allocation_confirmed"
  val AssessmentCentreAllocationUnconfirmed = "assessment_centre_allocation_unconfirmed"
  val AssessmentCentreFailedToAttend = "assessment_centre_failed_to_attend"
  val AssessmentCentreScoresEntered = "assessment_centre_scores_entered"
  val AssessmentCentreScoresAccepted = "assessment_centre_scores_accepted"
  val AssessmentCentreAwaitingReevaluation = "assessment_centre_awaiting_re_evaluation"

  val FsbAwaitingAllocation = "fsb_awaiting_allocation"
  val FsbAllocationUnconfirmed = "fsb_allocation_unconfirmed"
  val FsbAllocationConfirmed = "fsb_allocation_confirmed"
  val FsbFailedToAttend = "fsb_failed_to_attend"
  val FsbResultEntered = "fsb_result_entered"
  val FsbPassed = FSB_PASSED.toString.toLowerCase()
  val FsbFailed = FSB_FAILED.toString.toLowerCase()
  val FsbAllFailed = ALL_FSBS_AND_FSACS_FAILED.toString.toLowerCase()
  val FsbAllFailedNotified = ALL_FSBS_AND_FSACS_FAILED_NOTIFIED.toString.toLowerCase()

  val EligibleForJobOffer = ELIGIBLE_FOR_JOB_OFFER.toString.toLowerCase()
  val EligibleForJobOfferNotified = ELIGIBLE_FOR_JOB_OFFER_NOTIFIED.toString.toLowerCase()

  val SiftEntered = "sift_entered"
  val SiftTestInvited = "sift_test_invited"
  val SiftTestStarted = "sift_test_started"
  val SiftTestCompleted = "sift_test_completed"
  val SiftFirstReminder = "sift_first_reminder"
  val SiftSecondReminder = "sift_second_reminder"
  val SiftReady = "sift_ready"
  val SiftCompleted = "sift_completed"
  val SiftExpired = "sift_expired"
  val SiftFailed = "failed_at_sift"
  val SiftFailedNotified = FAILED_AT_SIFT_NOTIFIED.toString.toLowerCase()
  val SdipSiftFailed = "sdip_failed_at_sift"
  val SiftFaststreamFailedSdipGreen = SIFT_FASTSTREAM_FAILED_SDIP_GREEN.toString.toLowerCase()
  val ApplicationArchived = "application_archived"
  val FastPassAccepted = "fast_pass_accepted"

  val AwaitingOnlineTestReevaluationProgress = "awaiting_online_test_re_evaluation"
  val OnlineTestFailedProgress = "online_test_failed"
  val AssessmentCentrePassedProgress = ASSESSMENT_CENTRE_PASSED.toString.toLowerCase()
  val AssessmentCentreFailedProgress = ASSESSMENT_CENTRE_FAILED.toString.toLowerCase()
  val AssessmentCentreFailedNotifiedProgress = ASSESSMENT_CENTRE_FAILED_NOTIFIED.toString.toLowerCase()
  val AssessmentCentreFailedSdipGreenProgress = ASSESSMENT_CENTRE_FAILED_SDIP_GREEN.toString.toLowerCase()
  val AssessmentCentreFailedSdipGreenNotifiedProgress = ASSESSMENT_CENTRE_FAILED_SDIP_GREEN_NOTIFIED.toString.toLowerCase()
}
