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

package models

import connectors.exchange.ApplicationResponse
import connectors.exchange.CivilServiceExperienceDetails
import models.ApplicationData.ApplicationStatus
import models.ApplicationData.ApplicationStatus.ApplicationStatus
import models.ApplicationRoute.ApplicationRoute
import org.joda.time.DateTime
import play.api.libs.json._

import scala.language.implicitConversions

case class ApplicationData(
    applicationId: UniqueIdentifier,
    userId: UniqueIdentifier,
    applicationStatus: ApplicationStatus,
    applicationRoute: ApplicationRoute,
    progress: Progress,
    civilServiceExperienceDetails: Option[CivilServiceExperienceDetails],
    edipCompleted: Option[Boolean],
    overriddenSubmissionDeadline: Option[DateTime]) {

  import ApplicationData.ApplicationStatus._

  def isPhase1 =
    applicationStatus == PHASE1_TESTS || applicationStatus == PHASE1_TESTS_PASSED || applicationStatus == PHASE1_TESTS_FAILED
  def isPhase2 =
    (applicationStatus == PHASE2_TESTS || applicationStatus == PHASE2_TESTS_PASSED || applicationStatus == PHASE2_TESTS_FAILED
      || progress.phase2TestProgress.phase2TestsInvited)
  def isPhase3 =
    (applicationStatus == PHASE3_TESTS || applicationStatus == PHASE3_TESTS_PASSED
      || applicationStatus == PHASE3_TESTS_FAILED || applicationStatus == PHASE3_TESTS_PASSED_WITH_AMBER
      || progress.phase3TestProgress.phase3TestsInvited)

  def isSift = applicationStatus == SIFT
  def isSiftExpired =
    isSift && progress.siftProgress.siftExpired
  def isSdipFaststream = applicationRoute == ApplicationRoute.SdipFaststream
  def isFaststream = applicationRoute == ApplicationRoute.Faststream
}

object ApplicationData {

  // TODO: We have to make sure we implement the application status in the same way in faststream and faststream-frontend
  object ApplicationStatus extends Enumeration {
    type ApplicationStatus = Value
    // format: OFF
    val REGISTERED = Value
    val CREATED, IN_PROGRESS = Value
    val IN_PROGRESS_PERSONAL_DETAILS, IN_PROGRESS_SCHEME_PREFERENCES, IN_PROGRESS_ASSISTANCE_DETAILS,
    IN_PROGRESS_QUESTIONNAIRE, IN_PROGRESS_PREVIEW = Value
    val SUBMITTED = Value
    val PHASE1_TESTS, PHASE1_TESTS_PASSED, PHASE1_TESTS_FAILED = Value
    val PHASE2_TESTS, PHASE2_TESTS_PASSED, PHASE2_TESTS_FAILED = Value
    val PHASE3_TESTS, PHASE3_TESTS_PASSED_WITH_AMBER, PHASE3_TESTS_PASSED, PHASE3_TESTS_FAILED  = Value
    val FAST_PASS_ACCEPTED, PHASE1_TESTS_PASSED_NOTIFIED, PHASE3_TESTS_PASSED_NOTIFIED = Value
    val ARCHIVED = Value

    val SIFT, FAILED_AT_SIFT = Value
    val ASSESSMENT_CENTRE, FSB = Value
    val ELIGIBLE_FOR_JOB_OFFER = Value

    val WITHDRAWN = Value
    // format: ON

    implicit val applicationStatusFormat = new Format[ApplicationStatus] {
      def reads(json: JsValue) =
        JsSuccess(ApplicationStatus.withName(json.as[String]))
      def writes(myEnum: ApplicationStatus) = JsString(myEnum.toString)
    }
  }

  def isReadOnly(applicationStatus: ApplicationStatus) =
    applicationStatus match {
      case ApplicationStatus.REGISTERED  => false
      case ApplicationStatus.CREATED     => false
      case ApplicationStatus.IN_PROGRESS => false
      case _                             => true
    }

  implicit def fromAppRespToAppData(
      resp: ApplicationResponse): ApplicationData =
    new ApplicationData(
      resp.applicationId,
      resp.userId,
      ApplicationStatus.withName(resp.applicationStatus),
      resp.applicationRoute,
      resp.progressResponse,
      resp.civilServiceExperienceDetails,
      None,
      resp.overriddenSubmissionDeadline
    )

  implicit val applicationDataFormat = Json.format[ApplicationData]
}

// scalastyle:off number.of.types
// scalastyle:off number.of.methods
object ProgressStatuses {
  sealed abstract class ProgressStatus(
      val applicationStatus: ApplicationStatus) {
    def key = toString
  }

  object ProgressStatus {
    implicit val progressStatusFormat = new Format[ProgressStatus] {
      def reads(json: JsValue) =
        JsSuccess(nameToProgressStatus(json.as[String]))
      def writes(progressStatus: ProgressStatus) = JsString(progressStatus.key)
    }

    implicit def progressStatusToString(
        progressStatus: ProgressStatus): String =
      progressStatus.getClass.getSimpleName
  }

  case object CREATED extends ProgressStatus(ApplicationStatus.CREATED) {
    override def key = "created"
  }

  case object PERSONAL_DETAILS
      extends ProgressStatus(ApplicationStatus.IN_PROGRESS) {
    override def key = "personal-details"
  }

  case object SCHEME_PREFERENCES
      extends ProgressStatus(ApplicationStatus.IN_PROGRESS) {
    override def key = "scheme-preferences"
  }

  case object ASSISTANCE_DETAILS
      extends ProgressStatus(ApplicationStatus.IN_PROGRESS) {
    override def key = "assistance-details"
  }

  case object PREVIEW extends ProgressStatus(ApplicationStatus.IN_PROGRESS) {
    override def key = "preview"
  }

  case object SUBMITTED extends ProgressStatus(ApplicationStatus.SUBMITTED)

  case object WITHDRAWN extends ProgressStatus(ApplicationStatus.WITHDRAWN)

  case object PHASE1_TESTS_INVITED
      extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)
  case object PHASE1_TESTS_STARTED
      extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)
  case object PHASE1_TESTS_FIRST_REMINDER
      extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)
  case object PHASE1_TESTS_SECOND_REMINDER
      extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)
  case object PHASE1_TESTS_COMPLETED
      extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)
  case object PHASE1_TESTS_EXPIRED
      extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)
  case object PHASE1_TESTS_RESULTS_READY
      extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)
  case object PHASE1_TESTS_RESULTS_RECEIVED
      extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)
  case object PHASE1_TESTS_PASSED
      extends ProgressStatus(ApplicationStatus.PHASE1_TESTS_PASSED)
  case object PHASE1_TESTS_FAILED
      extends ProgressStatus(ApplicationStatus.PHASE1_TESTS_FAILED)
  case object PHASE1_TESTS_FAILED_NOTIFIED
      extends ProgressStatus(ApplicationStatus.PHASE1_TESTS_FAILED)

  case object PHASE2_TESTS_INVITED
      extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)
  case object PHASE2_TESTS_STARTED
      extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)
  case object PHASE2_TESTS_FIRST_REMINDER
      extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)
  case object PHASE2_TESTS_SECOND_REMINDER
      extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)
  case object PHASE2_TESTS_COMPLETED
      extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)
  case object PHASE2_TESTS_EXPIRED
      extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)
  case object PHASE2_TESTS_RESULTS_READY
      extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)
  case object PHASE2_TESTS_RESULTS_RECEIVED
      extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)
  case object PHASE2_TESTS_PASSED
      extends ProgressStatus(ApplicationStatus.PHASE2_TESTS_PASSED)
  case object PHASE2_TESTS_FAILED
      extends ProgressStatus(ApplicationStatus.PHASE2_TESTS_FAILED)
  case object PHASE2_TESTS_FAILED_NOTIFIED
      extends ProgressStatus(ApplicationStatus.PHASE2_TESTS_FAILED)

  case object PHASE3_TESTS_INVITED
      extends ProgressStatus(ApplicationStatus.PHASE3_TESTS)
  case object PHASE3_TESTS_STARTED
      extends ProgressStatus(ApplicationStatus.PHASE3_TESTS)
  case object PHASE3_TESTS_FIRST_REMINDER
      extends ProgressStatus(ApplicationStatus.PHASE3_TESTS)
  case object PHASE3_TESTS_SECOND_REMINDER
      extends ProgressStatus(ApplicationStatus.PHASE3_TESTS)
  case object PHASE3_TESTS_COMPLETED
      extends ProgressStatus(ApplicationStatus.PHASE3_TESTS)
  case object PHASE3_TESTS_EXPIRED
      extends ProgressStatus(ApplicationStatus.PHASE3_TESTS)
  case object PHASE3_TESTS_RESULTS_RECEIVED
      extends ProgressStatus(ApplicationStatus.PHASE3_TESTS)
  case object PHASE3_TESTS_PASSED_WITH_AMBER
      extends ProgressStatus(ApplicationStatus.PHASE3_TESTS_PASSED_WITH_AMBER)
  case object PHASE3_TESTS_PASSED
      extends ProgressStatus(ApplicationStatus.PHASE3_TESTS_PASSED)
  case object PHASE3_TESTS_PASSED_NOTIFIED
      extends ProgressStatus(ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED)
  case object PHASE3_TESTS_FAILED
      extends ProgressStatus(ApplicationStatus.PHASE3_TESTS_FAILED)
  case object PHASE3_TESTS_FAILED_NOTIFIED
      extends ProgressStatus(ApplicationStatus.PHASE3_TESTS_FAILED)

  case object PHASE1_TESTS_SUCCESS_NOTIFIED
      extends ProgressStatus(ApplicationStatus.PHASE1_TESTS_PASSED_NOTIFIED)
  case object PHASE3_TESTS_SUCCESS_NOTIFIED
      extends ProgressStatus(ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED)
  case object FAST_PASS_ACCEPTED
      extends ProgressStatus(ApplicationStatus.FAST_PASS_ACCEPTED)

  case object ALL_SCHEMES_SIFT_ENTERED
      extends ProgressStatus(ApplicationStatus.SIFT)
  case object ALL_SCHEMES_SIFT_COMPLETED
      extends ProgressStatus(ApplicationStatus.SIFT)
  case object SIFT_EXPIRED extends ProgressStatus(ApplicationStatus.SIFT)

  case object ASSESSMENT_CENTRE_AWAITING_ALLOCATION
      extends ProgressStatus(ApplicationStatus.ASSESSMENT_CENTRE)
  case object ASSESSMENT_CENTRE_ALLOCATION_UNCONFIRMED
      extends ProgressStatus(ApplicationStatus.ASSESSMENT_CENTRE)
  case object ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED
      extends ProgressStatus(ApplicationStatus.ASSESSMENT_CENTRE)
  case object ASSESSMENT_CENTRE_FAILED_TO_ATTEND
      extends ProgressStatus(ApplicationStatus.ASSESSMENT_CENTRE)
  case object ASSESSMENT_CENTRE_SCORES_ENTERED
      extends ProgressStatus(ApplicationStatus.ASSESSMENT_CENTRE)
  case object ASSESSMENT_CENTRE_SCORES_ACCEPTED
      extends ProgressStatus(ApplicationStatus.ASSESSMENT_CENTRE)
  case object ASSESSMENT_CENTRE_AWAITING_RE_EVALUATION
      extends ProgressStatus(ApplicationStatus.ASSESSMENT_CENTRE)
  case object ASSESSMENT_CENTRE_PASSED
      extends ProgressStatus(ApplicationStatus.ASSESSMENT_CENTRE)
  case object ASSESSMENT_CENTRE_FAILED
      extends ProgressStatus(ApplicationStatus.ASSESSMENT_CENTRE)

  case object FSB_AWAITING_ALLOCATION
      extends ProgressStatus(ApplicationStatus.FSB)
  case object FSB_ALLOCATION_UNCONFIRMED
      extends ProgressStatus(ApplicationStatus.FSB)
  case object FSB_ALLOCATION_CONFIRMED
      extends ProgressStatus(ApplicationStatus.FSB)
  case object FSB_FAILED_TO_ATTEND extends ProgressStatus(ApplicationStatus.FSB)
  case object FSB_SCORES_ENTERED extends ProgressStatus(ApplicationStatus.FSB)
  case object FSB_PASSED extends ProgressStatus(ApplicationStatus.FSB)
  case object FSB_FAILED extends ProgressStatus(ApplicationStatus.FSB)

  case object APPLICATION_ARCHIVED
      extends ProgressStatus(ApplicationStatus.ARCHIVED)

  def getProgressStatusForSdipFsSuccess(
      applicationStatus: ApplicationStatus): ProgressStatus = {
    case object PHASE1_TESTS_SDIP_FS_PASSED
        extends ProgressStatus(applicationStatus)
    PHASE1_TESTS_SDIP_FS_PASSED
  }

  def getProgressStatusForSdipFsFailed(
      applicationStatus: ApplicationStatus): ProgressStatus = {
    case object PHASE1_TESTS_SDIP_FS_FAILED
        extends ProgressStatus(applicationStatus)
    PHASE1_TESTS_SDIP_FS_FAILED
  }

  def getProgressStatusForSdipFsFailedNotified(
      applicationStatus: ApplicationStatus): ProgressStatus = {
    case object PHASE1_TESTS_SDIP_FS_FAILED_NOTIFIED
        extends ProgressStatus(applicationStatus)
    PHASE1_TESTS_SDIP_FS_FAILED_NOTIFIED
  }

  def getProgressStatusForSdipFsPassedNotified(
      applicationStatus: ApplicationStatus): ProgressStatus = {
    case object PHASE1_TESTS_SDIP_FS_PASSED_NOTIFIED
        extends ProgressStatus(applicationStatus)
    PHASE1_TESTS_SDIP_FS_PASSED_NOTIFIED
  }

  def nameToProgressStatus(name: String) =
    nameToProgressStatusMap(name.toLowerCase)

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

  private[models] val nameToProgressStatusMap: Map[String, ProgressStatus] =
    allStatuses.map { value =>
      value.key.toLowerCase -> value
    }.toMap

  def tryToGetDefaultProgressStatus(
      applicationStatus: ApplicationStatus): Option[ProgressStatus] = {
    val matching = allStatuses.filter(_.applicationStatus == applicationStatus)
    if (matching.size == 1) matching.headOption else None
  }

  def progressesByApplicationStatus(applicationStatuses: ApplicationStatus*) = {
    allStatuses.filter(st => applicationStatuses.contains(st.applicationStatus))
  }
}
// scalastyle:on
