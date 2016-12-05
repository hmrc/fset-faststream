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

package models

import connectors.exchange.ApplicationResponse
import connectors.exchange.CivilServiceExperienceDetails
import models.ApplicationData.ApplicationStatus.ApplicationStatus
import models.ApplicationRoute.ApplicationRoute
import play.api.libs.json._

import scala.language.implicitConversions

case class ApplicationData(applicationId: UniqueIdentifier,
                           userId: UniqueIdentifier,
                           applicationStatus: ApplicationStatus,
                           applicationRoute: ApplicationRoute,
                           progress: Progress,
                           civilServiceExperienceDetails: Option[CivilServiceExperienceDetails],
                           edipCompleted: Option[Boolean]
                          ) {
  import ApplicationData.ApplicationStatus._
  def isPhase1 = applicationStatus == PHASE1_TESTS || applicationStatus == PHASE1_TESTS_PASSED || applicationStatus == PHASE1_TESTS_FAILED
  def isPhase2 = applicationStatus == PHASE2_TESTS || applicationStatus == PHASE2_TESTS_PASSED || applicationStatus == PHASE2_TESTS_FAILED
  def isPhase3 = applicationStatus == PHASE3_TESTS || applicationStatus == PHASE3_TESTS_PASSED || applicationStatus == PHASE3_TESTS_FAILED
}

object ApplicationData {

  // TODO: We have to make sure we implement the application status in the same way in faststream and faststream-frontend
  object ApplicationStatus extends Enumeration {
    type ApplicationStatus = Value
    // format: OFF
    val WITHDRAWN, CREATED, IN_PROGRESS, SUBMITTED = Value
    val PHASE1_TESTS, PHASE1_TESTS_PASSED, PHASE1_TESTS_FAILED = Value
    val PHASE2_TESTS, PHASE2_TESTS_PASSED, PHASE2_TESTS_FAILED = Value
    val PHASE3_TESTS, PHASE3_TESTS_PASSED_WITH_AMBER, PHASE3_TESTS_PASSED, PHASE3_TESTS_FAILED  = Value
    val READY_FOR_EXPORT, EXPORTED = Value
    val ARCHIVED = Value

    val REGISTERED = Value
    val IN_PROGRESS_PERSONAL_DETAILS, IN_PROGRESS_SCHEME_PREFERENCES, IN_PROGRESS_PARTNER_GRADUATE_PROGRAMMES, IN_PROGRESS_ASSISTANCE_DETAILS,
    IN_PROGRESS_QUESTIONNAIRE, IN_PROGRESS_PREVIEW = Value
    val ONLINE_TEST_FAILED_NOTIFIED, AWAITING_ONLINE_TEST_RE_EVALUATION,
    AWAITING_ALLOCATION, FAILED_TO_ATTEND, ALLOCATION_UNCONFIRMED, ALLOCATION_CONFIRMED, ASSESSMENT_SCORES_ENTERED,
    ASSESSMENT_SCORES_ACCEPTED, AWAITING_ASSESSMENT_CENTRE_RE_EVALUATION, ASSESSMENT_CENTRE_PASSED,
    ASSESSMENT_CENTRE_PASSED_NOTIFIED, ASSESSMENT_CENTRE_FAILED, ASSESSMENT_CENTRE_FAILED_NOTIFIED = Value
    // format: ON

    implicit val applicationStatusFormat = new Format[ApplicationStatus] {
      def reads(json: JsValue) = JsSuccess(ApplicationStatus.withName(json.as[String]))
      def writes(myEnum: ApplicationStatus) = JsString(myEnum.toString)
    }
  }

  def isReadOnly(applicationStatus: ApplicationStatus) = applicationStatus match {
    case ApplicationStatus.REGISTERED => false
    case ApplicationStatus.CREATED => false
    case ApplicationStatus.IN_PROGRESS => false
    case _ => true
  }

  implicit def fromAppRespToAppData(resp: ApplicationResponse): ApplicationData =
    new ApplicationData(resp.applicationId, resp.userId, ApplicationStatus.withName(resp.applicationStatus),
      resp.applicationRoute, resp.progressResponse, resp.civilServiceExperienceDetails, None)

  implicit val applicationDataFormat = Json.format[ApplicationData]

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

    sealed abstract class ProgressStatus(val applicationStatus: ApplicationStatus)

    object ProgressStatus {
      implicit val progressStatusFormat = new Format[ProgressStatus] {
        def reads(json: JsValue) = JsSuccess(nameToProgressStatus(json.as[String]))
        def writes(progressStatusName: ProgressStatus) = JsString(progressStatusName.toString)
      }
    }

    case object PHASE1_TESTS_INVITED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)

    case object PHASE1_TESTS_STARTED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)

    case object PHASE1_TESTS_COMPLETED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)

    case object PHASE1_TESTS_EXPIRED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)

    case object PHASE1_TESTS_RESULTS_RECEIVED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)

    case object PHASE1_TESTS_PASSED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS_PASSED)

    case object PHASE1_TESTS_FAILED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS_FAILED)

    private val nameToProgressStatus: Map[String, ProgressStatus] = List(
      PHASE1_TESTS_INVITED,
      PHASE1_TESTS_STARTED,
      PHASE1_TESTS_COMPLETED,
      PHASE1_TESTS_EXPIRED,
      PHASE1_TESTS_RESULTS_RECEIVED,
      PHASE1_TESTS_PASSED,
      PHASE1_TESTS_FAILED
    ).map { value =>
      value.toString -> value
    }.toMap
  }
}
