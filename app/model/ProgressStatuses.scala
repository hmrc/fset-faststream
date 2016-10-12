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

import model.ApplicationStatus._
import play.api.libs.json.{ Format, JsString, JsSuccess, JsValue }
import reactivemongo.bson.{ BSON, BSONHandler, BSONString }
import scala.language.implicitConversions

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
  val Phase1TestsInvited = "phase1_tests_invited"
  val Phase1TestsStarted = "phase1_tests_started"
  val Phase1TestsCompleted = "phase1_tests_completed"
  val Phase1TestsExpired = "phase1_tests_expired"
  val Phase1TestsResultsReady = "phase1_tests_ready"
  val Phase1TestsResultsReceived = "phase1_tests_results_received"
  val Phase2TestsInvited = "phase2_tests_invited"
  val Phase2TestsStarted = "phase2_tests_started"
  val Phase2TestsCompleted = "phase2_tests_completed"
  val Phase2TestsExpired = "phase2_tests_expired"
  val Phase2TestsResultsReady = "phase2_tests_ready"
  val Phase2TestsResultsReceived = "phase2_tests_results_received"
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

  sealed abstract class ProgressStatus(val applicationStatus: ApplicationStatus)

  object ProgressStatus {
    implicit val progressStatusFormat = new Format[ProgressStatus] {
      def reads(json: JsValue) = JsSuccess(nameToProgressStatus(json.as[String]))
      def writes(progressStatusName: ProgressStatus) = JsString(progressStatusName.toString)
    }

    implicit object BSONEnumHandler extends BSONHandler[BSONString, ProgressStatus] {
      def read(doc: BSONString) = nameToProgressStatus(doc.value)
      def write(progressStatusName: ProgressStatus) = BSON.write(progressStatusName.toString)
    }

    implicit def progressStatusToString(progressStatus: ProgressStatus): String = progressStatus.getClass.getSimpleName
  }

  case object PHASE1_TESTS_INVITED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)
  case object PHASE1_TESTS_STARTED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)
  case object PHASE1_TESTS_FIRST_REMINDER extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)
  case object PHASE1_TESTS_SECOND_REMINDER extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)
  case object PHASE1_TESTS_COMPLETED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)
  case object PHASE1_TESTS_EXPIRED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)
  case object PHASE1_TESTS_RESULTS_READY extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)
  case object PHASE1_TESTS_RESULTS_RECEIVED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)
  case object PHASE1_TESTS_PASSED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS_PASSED)
  case object PHASE1_TESTS_FAILED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS_FAILED)

  case object PHASE2_TESTS_INVITED extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)
  case object PHASE2_TESTS_STARTED extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)
  case object PHASE2_TESTS_FIRST_REMINDER extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)
  case object PHASE2_TESTS_SECOND_REMINDER extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)
  case object PHASE2_TESTS_COMPLETED extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)
  case object PHASE2_TESTS_EXPIRED extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)
  case object PHASE2_TESTS_RESULTS_READY extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)
  case object PHASE2_TESTS_RESULTS_RECEIVED extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)

  case object PHASE3_TESTS_INVITED extends ProgressStatus(ApplicationStatus.PHASE3_TESTS)

  val nameToProgressStatus: Map[String, ProgressStatus] = List(
    PHASE1_TESTS_INVITED,
    PHASE1_TESTS_STARTED,
    PHASE1_TESTS_COMPLETED,
    PHASE1_TESTS_EXPIRED,
    PHASE1_TESTS_RESULTS_READY,
    PHASE1_TESTS_RESULTS_RECEIVED,
    PHASE1_TESTS_PASSED,
    PHASE1_TESTS_FAILED,
    PHASE2_TESTS_INVITED,
    PHASE2_TESTS_STARTED,
    PHASE2_TESTS_COMPLETED,
    PHASE2_TESTS_EXPIRED,
    PHASE2_TESTS_RESULTS_READY,
    PHASE2_TESTS_RESULTS_RECEIVED,
    PHASE3_TESTS_INVITED
  ).map { value =>
    value.toString -> value
  }.toMap

}

