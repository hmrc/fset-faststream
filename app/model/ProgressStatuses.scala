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

import model.ApplicationStatus.ApplicationStatus
import play.api.libs.json.{ Format, JsString, JsSuccess, JsValue }
import reactivemongo.bson.{ BSON, BSONHandler, BSONString }

object ProgressStatuses {
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
  val AwaitingOnlineTestReevaluationProgress = "awaiting_online_test_re_evaluation"
  val OnlineTestFailedProgress = "online_test_failed"
  val OnlineTestFailedNotifiedProgress = "online_test_failed_notified"
  val AwaitingOnlineTestAllocationProgress = "awaiting_online_test_allocation"
  val AllocationConfirmedProgress = "allocation_confirmed"
  val AllocationUnconfirmedProgress = "allocation_unconfirmed"
  val FailedToAttendProgress = ApplicationStatuses.FailedToAttend.toLowerCase()
  val AssessmentScoresEnteredProgress = ApplicationStatuses.AssessmentScoresEntered.toLowerCase()
  val AssessmentScoresAcceptedProgress = ApplicationStatuses.AssessmentScoresAccepted.toLowerCase()
  val AwaitingAssessmentCentreReevaluationProgress = ApplicationStatuses.AwaitingAssessmentCentreReevaluation.toLowerCase()
  val AssessmentCentrePassedProgress = ApplicationStatuses.AssessmentCentrePassed.toLowerCase()
  val AssessmentCentreFailedProgress = ApplicationStatuses.AssessmentCentreFailed.toLowerCase()
  val AssessmentCentrePassedNotifiedProgress = ApplicationStatuses.AssessmentCentrePassedNotified.toLowerCase()
  val AssessmentCentreFailedNotifiedProgress = ApplicationStatuses.AssessmentCentreFailedNotified.toLowerCase()

  sealed abstract class ProgressStatus(applicationStatus: ApplicationStatus)

  object ProgressStatus {
    implicit val progressStatusFormat = new Format[ProgressStatus] {
      def reads(json: JsValue) = JsSuccess(nameToProgressStatus(json.as[String]))
      def writes(myEnum: ProgressStatus) = JsString(myEnum.toString)
    }

    implicit object BSONEnumHandler extends BSONHandler[BSONString, ProgressStatus] {
      def read(doc: BSONString) = nameToProgressStatus(doc.value)
      def write(stats: ProgressStatus) = BSON.write(stats.toString)
    }
  }

  case object PHASE1_TESTS_INVITED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)

  case object PHASE1_TESTS_STARTED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)

  case object PHASE1_TESTS_COMPLETED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)

  case object PHASE1_TESTS_EXPIRED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)

  case object PHASE1_TESTS_RESULTS_RECEIVED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)

  val nameToProgressStatus = List(
    PHASE1_TESTS_INVITED,
    PHASE1_TESTS_STARTED,
    PHASE1_TESTS_COMPLETED,
    PHASE1_TESTS_EXPIRED,
    PHASE1_TESTS_RESULTS_RECEIVED
  ).map { value =>
    value.toString -> value
  }.toMap

}

