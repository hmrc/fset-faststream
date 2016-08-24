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
    (progress.onlineTest.onlineTestInvited, 100, OnlineTestInvitedProgress),
    (progress.onlineTest.onlineTestStarted, 110, OnlineTestStartedProgress),
    (progress.onlineTest.onlineTestCompleted, 120, OnlineTestCompletedProgress),
    (progress.onlineTest.onlineTestExpired, 130, OnlineTestExpiredProgress),
    (progress.onlineTest.onlineTestAwaitingReevaluation, 140, AwaitingOnlineTestReevaluationProgress),
    (progress.onlineTest.onlineTestFailed, 150, OnlineTestFailedProgress),
    (progress.onlineTest.onlineTestFailedNotified, 160, OnlineTestFailedNotifiedProgress),
    (progress.onlineTest.onlineTestAwaitingAllocation, 170, AwaitingOnlineTestAllocationProgress),
    (progress.onlineTest.onlineTestAllocationUnconfirmed, 180, AllocationUnconfirmedProgress),
    (progress.onlineTest.onlineTestAllocationConfirmed, 190, AllocationConfirmedProgress),
    (progress.assessmentScores.entered, 200, AssessmentScoresEnteredProgress),
    (progress.failedToAttend, 210, FailedToAttendProgress),
    (progress.assessmentScores.accepted, 220, AssessmentScoresAcceptedProgress),
    (progress.assessmentCentre.awaitingReevaluation, 230, AwaitingAssessmentCentreReevaluationProgress),
    (progress.assessmentCentre.failed, 240, AssessmentCentreFailedProgress),
    (progress.assessmentCentre.failedNotified, 245, AssessmentCentreFailedNotifiedProgress),
    (progress.assessmentCentre.passed, 250, AssessmentCentrePassedProgress),
    (progress.assessmentCentre.passedNotified, 255, AssessmentCentrePassedNotifiedProgress),

    (progress.withdrawn, 999, WithdrawnProgress)
  )
}
