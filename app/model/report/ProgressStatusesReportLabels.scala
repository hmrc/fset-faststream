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

package model.report

import model.ApplicationStatus._
import model.command.ProgressResponse

trait ProgressStatusesReportLabels {
  import ProgressStatusesReportLabels._

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
    (progress.phase1ProgressResponse.phase1TestsInvited, 100, Phase1TestsInvited),
    (progress.phase1ProgressResponse.phase1TestsFirstRemainder, 110, Phase1TestsFirstRemainder),
    (progress.phase1ProgressResponse.phase1TestsSecondRemainder, 120, Phase1TestsSecondRemainder),
    (progress.phase1ProgressResponse.phase1TestsStarted, 130, Phase1TestsStarted),
    (progress.phase1ProgressResponse.phase1TestsCompleted, 140, Phase1TestsCompleted),
    (progress.phase1ProgressResponse.phase1TestsExpired, 150, Phase1TestsExpired),
    (progress.phase1ProgressResponse.phase1TestsResultsReady, 160, Phase1TestsResultsReady),
    (progress.phase1ProgressResponse.phase1TestsResultsReceived, 170, Phase1TestsResultsReceived),
    (progress.phase1ProgressResponse.phase1TestsPassed, 180, Phase1TestsPassed),
    (progress.phase1ProgressResponse.phase1TestsFailed, 190, Phase1TestsFailed),
    (progress.phase2ProgressResponse.phase2TestsInvited, 200, Phase2TestsInvited),
    (progress.phase2ProgressResponse.phase2TestsFirstRemainder, 210, Phase2TestsFirstRemainder),
    (progress.phase2ProgressResponse.phase2TestsSecondRemainder, 220, Phase2TestsSecondRemainder),
    (progress.phase2ProgressResponse.phase2TestsStarted, 230, Phase2TestsStarted),
    (progress.phase2ProgressResponse.phase2TestsCompleted, 240, Phase2TestsCompleted),
    (progress.phase2ProgressResponse.phase2TestsExpired, 250, Phase2TestsExpired),
    (progress.phase2ProgressResponse.phase2TestsResultsReady, 260, Phase2TestsResultsReady),
    (progress.phase2ProgressResponse.phase2TestsResultsReceived, 270, Phase2TestsResultsReceived),
    (progress.phase2ProgressResponse.phase2TestsPassed, 280, Phase2TestsPassed),
    (progress.phase2ProgressResponse.phase2TestsFailed, 290, Phase2TestsFailed),
    (progress.assessmentScores.entered, 300, AssessmentScoresEnteredProgress),
    (progress.failedToAttend, 310, FailedToAttendProgress),
    (progress.assessmentScores.accepted, 320, AssessmentScoresAcceptedProgress),
    (progress.assessmentCentre.awaitingReevaluation, 330, AwaitingAssessmentCentreReevaluationProgress),
    (progress.assessmentCentre.failed, 340, AssessmentCentreFailedProgress),
    (progress.assessmentCentre.failedNotified, 345, AssessmentCentreFailedNotifiedProgress),
    (progress.assessmentCentre.passed, 350, AssessmentCentrePassedProgress),
    (progress.assessmentCentre.passedNotified, 355, AssessmentCentrePassedNotifiedProgress),
    (progress.withdrawn, 999, WithdrawnProgress)
  )

  def progressStatusNameInReports(progress: ProgressResponse): String = {
    val default = 0 -> ProgressStatusesReportLabels.RegisteredProgress

    type StatusMap = (Boolean, Int, String)
    type HighestStatus = (Int, String)

    def combineStatuses(statusMap: Seq[StatusMap]): HighestStatus = {
      statusMap.foldLeft(default) { (highest, current) =>
        val (highestWeighting, _) = highest
        current match {
          case (true, weighting, name) if weighting > highestWeighting => weighting -> name
          case _ => highest
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
  val Phase1TestsInvited = "phase1_tests_invited"
  val Phase1TestsFirstRemainder = "phase1_tests_first_remainder"
  val Phase1TestsSecondRemainder = "phase1_tests_second_remainder"
  val Phase1TestsStarted = "phase1_tests_started"
  val Phase1TestsCompleted = "phase1_tests_completed"
  val Phase1TestsExpired = "phase1_tests_expired"
  val Phase1TestsResultsReady = "phase1_tests_results_ready"
  val Phase1TestsResultsReceived = "phase1_tests_results_received"
  val Phase1TestsPassed = "phase1_tests_passed"
  val Phase1TestsFailed = "phase1_tests_failed"
  val Phase2TestsInvited = "phase2_tests_invited"
  val Phase2TestsFirstRemainder = "phase2_tests_first_remainder"
  val Phase2TestsSecondRemainder = "phase2_tests_second_remainder"
  val Phase2TestsStarted = "phase2_tests_started"
  val Phase2TestsCompleted = "phase2_tests_completed"
  val Phase2TestsExpired = "phase2_tests_expired"
  val Phase2TestsResultsReady = "phase2_tests_results_ready"
  val Phase2TestsResultsReceived = "phase2_tests_results_received"
  val Phase2TestsPassed = "phase2_tests_passed"
  val Phase2TestsFailed = "phase2_tests_failed"
  val AwaitingOnlineTestReevaluationProgress = "awaiting_online_test_re_evaluation"
  val OnlineTestFailedProgress = "online_test_failed"
  val OnlineTestFailedNotifiedProgress = "online_test_failed_notified"
  val AwaitingOnlineTestAllocationProgress = "awaiting_online_test_allocation"
  val AllocationConfirmedProgress = "allocation_confirmed"
  val AllocationUnconfirmedProgress = "allocation_unconfirmed"
  val FailedToAttendProgress = FAILED_TO_ATTEND.toLowerCase()
  val AssessmentScoresEnteredProgress = ASSESSMENT_SCORES_ENTERED.toLowerCase()
  val AssessmentScoresAcceptedProgress = ASSESSMENT_SCORES_ACCEPTED.toLowerCase()
  val AwaitingAssessmentCentreReevaluationProgress = AWAITING_ASSESSMENT_CENTRE_RE_EVALUATION.toLowerCase()
  val AssessmentCentrePassedProgress = ASSESSMENT_CENTRE_PASSED.toLowerCase()
  val AssessmentCentreFailedProgress = ASSESSMENT_CENTRE_FAILED.toLowerCase()
  val AssessmentCentrePassedNotifiedProgress = ASSESSMENT_CENTRE_PASSED_NOTIFIED.toLowerCase()
  val AssessmentCentreFailedNotifiedProgress = ASSESSMENT_CENTRE_FAILED_NOTIFIED.toLowerCase()
}
