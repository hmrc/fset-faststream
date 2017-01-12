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

package models

import connectors.exchange.ApplicationResponse
import connectors.exchange.CivilServiceExperienceDetails
import models.ApplicationData.ApplicationStatus
import models.ApplicationData.ApplicationStatus.ApplicationStatus
import models.ApplicationRoute.ApplicationRoute
import org.joda.time.DateTime
import play.api.libs.json._

import scala.language.implicitConversions

case class ApplicationData(applicationId: UniqueIdentifier,
                           userId: UniqueIdentifier,
                           applicationStatus: ApplicationStatus,
                           applicationRoute: ApplicationRoute,
                           progress: Progress,
                           civilServiceExperienceDetails: Option[CivilServiceExperienceDetails],
                           edipCompleted: Option[Boolean],
                           overriddenSubmissionDeadline: Option[DateTime]
                          ) {

  import ApplicationData.ApplicationStatus._

  def isPhase1 = applicationStatus == PHASE1_TESTS || applicationStatus == PHASE1_TESTS_PASSED || applicationStatus == PHASE1_TESTS_FAILED
  def isPhase2 = applicationStatus == PHASE2_TESTS || applicationStatus == PHASE2_TESTS_PASSED || applicationStatus == PHASE2_TESTS_FAILED
  def isPhase3 = (applicationStatus == PHASE3_TESTS || applicationStatus == PHASE3_TESTS_PASSED
                    || applicationStatus == PHASE3_TESTS_FAILED || applicationStatus == PHASE3_TESTS_PASSED_WITH_AMBER)
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
    val READY_FOR_EXPORT, EXPORTED, UPDATE_EXPORTED = Value
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
      resp.applicationRoute, resp.progressResponse, resp.civilServiceExperienceDetails, None, resp.overriddenSubmissionDeadline)

  implicit val applicationDataFormat = Json.format[ApplicationData]
}

/*  object ProgressStatuses {
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
}*/

// scalastyle:off number.of.types
// scalastyle:off number.of.methods
object ProgressStatuses {
  sealed abstract class ProgressStatus(val applicationStatus: ApplicationStatus) {
    def key = toString
  }

  object ProgressStatus {
    implicit val progressStatusFormat = new Format[ProgressStatus] {
      def reads(json: JsValue) = JsSuccess(nameToProgressStatus(json.as[String]))
      def writes(progressStatus: ProgressStatus) = JsString(progressStatus.key)
    }

    implicit def progressStatusToString(progressStatus: ProgressStatus): String = progressStatus.getClass.getSimpleName
  }

  case object CREATED extends ProgressStatus(ApplicationStatus.CREATED) {
    override def key = "created"}

  case object PERSONAL_DETAILS extends ProgressStatus(ApplicationStatus.IN_PROGRESS) {
    override def key = "personal-details"}

  case object PARTNER_GRADUATE_PROGRAMMES extends ProgressStatus(ApplicationStatus.IN_PROGRESS) {
    override def key = "partner-graduate-programmes"}

  case object SCHEME_PREFERENCES extends ProgressStatus(ApplicationStatus.IN_PROGRESS) {
    override def key = "scheme-preferences"}

  case object ASSISTANCE_DETAILS extends ProgressStatus(ApplicationStatus.IN_PROGRESS) {
    override def key = "assistance-details"}

  case object PREVIEW extends ProgressStatus(ApplicationStatus.IN_PROGRESS) {
    override def key = "preview"}

  case object SUBMITTED extends ProgressStatus(ApplicationStatus.SUBMITTED)

  case object WITHDRAWN extends ProgressStatus(ApplicationStatus.WITHDRAWN)

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
  case object PHASE1_TESTS_FAILED_NOTIFIED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS_FAILED)

  case object PHASE2_TESTS_INVITED extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)
  case object PHASE2_TESTS_STARTED extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)
  case object PHASE2_TESTS_FIRST_REMINDER extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)
  case object PHASE2_TESTS_SECOND_REMINDER extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)
  case object PHASE2_TESTS_COMPLETED extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)
  case object PHASE2_TESTS_EXPIRED extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)
  case object PHASE2_TESTS_RESULTS_READY extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)
  case object PHASE2_TESTS_RESULTS_RECEIVED extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)
  case object PHASE2_TESTS_PASSED extends ProgressStatus(ApplicationStatus.PHASE2_TESTS_PASSED)
  case object PHASE2_TESTS_FAILED extends ProgressStatus(ApplicationStatus.PHASE2_TESTS_FAILED)
  case object PHASE2_TESTS_FAILED_NOTIFIED extends ProgressStatus(ApplicationStatus.PHASE2_TESTS_FAILED)

  case object PHASE3_TESTS_INVITED extends ProgressStatus(ApplicationStatus.PHASE3_TESTS)
  case object PHASE3_TESTS_STARTED extends ProgressStatus(ApplicationStatus.PHASE3_TESTS)
  case object PHASE3_TESTS_FIRST_REMINDER extends ProgressStatus(ApplicationStatus.PHASE3_TESTS)
  case object PHASE3_TESTS_SECOND_REMINDER extends ProgressStatus(ApplicationStatus.PHASE3_TESTS)
  case object PHASE3_TESTS_COMPLETED extends ProgressStatus(ApplicationStatus.PHASE3_TESTS)
  case object PHASE3_TESTS_EXPIRED extends ProgressStatus(ApplicationStatus.PHASE3_TESTS)
  case object PHASE3_TESTS_RESULTS_RECEIVED extends ProgressStatus(ApplicationStatus.PHASE3_TESTS)
  case object PHASE3_TESTS_PASSED_WITH_AMBER extends ProgressStatus(ApplicationStatus.PHASE3_TESTS_PASSED_WITH_AMBER)
  case object PHASE3_TESTS_PASSED extends ProgressStatus(ApplicationStatus.PHASE3_TESTS_PASSED)
  case object PHASE3_TESTS_FAILED extends ProgressStatus(ApplicationStatus.PHASE3_TESTS_FAILED)
  case object PHASE3_TESTS_FAILED_NOTIFIED extends ProgressStatus(ApplicationStatus.PHASE3_TESTS_FAILED)

  case object PHASE1_TESTS_SUCCESS_NOTIFIED extends ProgressStatus(ApplicationStatus.READY_FOR_EXPORT)
  case object PHASE3_TESTS_SUCCESS_NOTIFIED extends ProgressStatus(ApplicationStatus.READY_FOR_EXPORT)
  case object FAST_PASS_ACCEPTED extends ProgressStatus(ApplicationStatus.READY_FOR_EXPORT)

  case object EXPORTED extends ProgressStatus(ApplicationStatus.EXPORTED)
  case object UPDATE_EXPORTED extends ProgressStatus(ApplicationStatus.UPDATE_EXPORTED)
  case object APPLICATION_ARCHIVED extends ProgressStatus(ApplicationStatus.ARCHIVED)

  def getProgressStatusForSdipFsSuccess(applicationStatus: ApplicationStatus): ProgressStatus = {
    case object PHASE1_TESTS_SDIP_FS_PASSED extends ProgressStatus(applicationStatus)
    PHASE1_TESTS_SDIP_FS_PASSED
  }

  def getProgressStatusForSdipFsFailed(applicationStatus: ApplicationStatus): ProgressStatus = {
    case object PHASE1_TESTS_SDIP_FS_FAILED extends ProgressStatus(applicationStatus)
    PHASE1_TESTS_SDIP_FS_FAILED
  }

  def getProgressStatusForSdipFsFailedNotified(applicationStatus: ApplicationStatus): ProgressStatus = {
    case object PHASE1_TESTS_SDIP_FS_FAILED_NOTIFIED extends ProgressStatus(applicationStatus)
    PHASE1_TESTS_SDIP_FS_FAILED_NOTIFIED
  }

  def getProgressStatusForSdipFsPassedNotified(applicationStatus: ApplicationStatus): ProgressStatus = {
    case object PHASE1_TESTS_SDIP_FS_PASSED_NOTIFIED extends ProgressStatus(applicationStatus)
    PHASE1_TESTS_SDIP_FS_PASSED_NOTIFIED
  }

  def nameToProgressStatus(name: String) = nameToProgressStatusMap(name.toLowerCase)

  // Reflection is generally 'A bad thing' but in this case it ensures that all progress statues are taken into account
  // Had considered an implementation with a macro, but that would need defining in another compilation unit
  // As it is a val in a object, it is only run once upon startup
  private[models] val allStatuses: Seq[ProgressStatus] = {
    import scala.reflect.runtime.universe._
    val mirror = runtimeMirror(this.getClass.getClassLoader)
    val insMirror = mirror reflect this
    val originType = insMirror.symbol.typeSignature
    val members = originType.members
    members.collect { member =>
      member.typeSignature match {
        case tpe if tpe <:< typeOf[ProgressStatus] && member.isModule =>
          val module = member.asModule
          (mirror reflectModule module).instance.asInstanceOf[ProgressStatus]
      }
    }.toSeq
  }

  private[models] val nameToProgressStatusMap: Map[String, ProgressStatus] = allStatuses.map { value =>
    value.key.toLowerCase -> value
  }.toMap

  def tryToGetDefaultProgressStatus(applicationStatus: ApplicationStatus): Option[ProgressStatus] = {
    val matching = allStatuses.filter(_.applicationStatus == applicationStatus)
    if (matching.size == 1) matching.headOption else None
  }

  def progressesByApplicationStatus(applicationStatuses: ApplicationStatus*) = {
    allStatuses.filter(st => applicationStatuses.contains(st.applicationStatus))
  }
}
// scalastyle:on

