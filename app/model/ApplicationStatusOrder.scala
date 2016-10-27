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

package model

import model.command.ProgressResponse
import model.ProgressStatuses._

object ApplicationStatusOrder {

  def getStatus(progress: Option[ProgressResponse]): String = progress match {
    case Some(p) => getStatus(p)
    case None => RegisteredProgress
  }

  def getStatus(progress: ProgressResponse): String = {
    val default = 0 -> RegisteredProgress

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

  def isNonSubmittedStatus(progress: ProgressResponse): Boolean = {
    val isNotSubmitted = !progress.submitted
    val isNotWithdrawn = !progress.withdrawn
    isNotWithdrawn && isNotSubmitted
  }

  // scalastyle:off
  def statusMaps(progress: ProgressResponse) = Seq(
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
    (progress.phase2ProgressResponse.phase2TestsResultsReceived, 300, Phase2TestsResultsReceived),
    (progress.phase3ProgressResponse.phase3TestsInvited, 305, Phase1TestsInvited),
    (progress.phase3ProgressResponse.phase3TestsStarted, 310, Phase1TestsStarted),
    (progress.phase3ProgressResponse.phase3TestsCompleted, 315, Phase1TestsCompleted),
    (progress.phase3ProgressResponse.phase3TestsExpired, 320, Phase1TestsExpired),
    (progress.phase3ProgressResponse.phase3TestsResultsReceived, 325, Phase1TestsResultsReceived),
    (progress.assessmentScores.entered, 330, AssessmentScoresEnteredProgress),
    (progress.failedToAttend, 335, FailedToAttendProgress),
    (progress.assessmentScores.accepted, 340, AssessmentScoresAcceptedProgress),
    (progress.assessmentCentre.awaitingReevaluation, 345, AwaitingAssessmentCentreReevaluationProgress),
    (progress.assessmentCentre.failed, 350, AssessmentCentreFailedProgress),
    (progress.assessmentCentre.failedNotified, 360, AssessmentCentreFailedNotifiedProgress),
    (progress.assessmentCentre.passed, 365, AssessmentCentrePassedProgress),
    (progress.assessmentCentre.passedNotified, 370, AssessmentCentrePassedNotifiedProgress),
    (progress.assessmentScores.entered, 375, AssessmentScoresEnteredProgress),
    (progress.withdrawn, 999, WithdrawnProgress)
  )
  // scalastyle:on
}
