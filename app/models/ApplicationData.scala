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
import connectors.exchange.FastPassDetails
import models.ApplicationData.ApplicationStatus.ApplicationStatus
import play.api.libs.json._

import scala.language.implicitConversions

case class ApplicationData(applicationId: UniqueIdentifier,
                           userId: UniqueIdentifier,
                           applicationStatus: ApplicationStatus,
                           progress: Progress,
                           fastPassDetails: Option[FastPassDetails]
                          )

object ApplicationData {

  // TODO: We have to make sure we implement the application status in the same way in faststream and faststream-frontend
  object ApplicationStatus extends Enumeration {
    type ApplicationStatus = Value
    // format: OFF
    val REGISTERED, CREATED, IN_PROGRESS, SUBMITTED, WITHDRAWN, ONLINE_TEST_INVITED,
    ONLINE_TEST_STARTED, ONLINE_TEST_COMPLETED, ONLINE_TEST_EXPIRED,
    ALLOCATION_CONFIRMED, ALLOCATION_UNCONFIRMED, AWAITING_ALLOCATION,
    ONLINE_TEST_FAILED, ONLINE_TEST_FAILED_NOTIFIED, AWAITING_ONLINE_TEST_RE_EVALUATION, FAILED_TO_ATTEND,
    ASSESSMENT_SCORES_ENTERED, ASSESSMENT_SCORES_ACCEPTED, AWAITING_ASSESSMENT_CENTRE_RE_EVALUATION, ASSESSMENT_CENTRE_PASSED,
    PHASE1_TESTS,
    ASSESSMENT_CENTRE_FAILED, ASSESSMENT_CENTRE_PASSED_NOTIFIED, ASSESSMENT_CENTRE_FAILED_NOTIFIED = Value
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
      resp.progressResponse, resp.fastPassDetails)

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

    private val nameToProgressStatus: Map[String, ProgressStatus] = List(
      PHASE1_TESTS_INVITED,
      PHASE1_TESTS_STARTED,
      PHASE1_TESTS_COMPLETED,
      PHASE1_TESTS_EXPIRED,
      PHASE1_TESTS_RESULTS_RECEIVED
    ).map { value =>
      value.toString -> value
    }.toMap

  }
}
