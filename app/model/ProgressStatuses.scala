/*
 * Copyright 2023 HM Revenue & Customs
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
import model.ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED
import play.api.libs.json.{Format, JsString, JsSuccess, JsValue}

import scala.language.implicitConversions

// scalastyle:off number.of.methods number.of.types
object ProgressStatuses {

  sealed abstract class ProgressStatus(val applicationStatus: ApplicationStatus) {
    def key = toString
  }

  object ProgressStatus {
    implicit val progressStatusFormat: Format[ProgressStatus] = new Format[ProgressStatus] {
      def reads(json: JsValue): JsSuccess[ProgressStatus] = JsSuccess(nameToProgressStatus(json.as[String]))
      def writes(progressStatus: ProgressStatus): JsString = JsString(progressStatus.key)
    }

    implicit def progressStatusToString(progressStatus: ProgressStatus): String = progressStatus.getClass.getSimpleName
  }

  case object CREATED extends ProgressStatus(ApplicationStatus.CREATED) {
    override def key = "created"}

  case object PERSONAL_DETAILS extends ProgressStatus(ApplicationStatus.IN_PROGRESS) {
    override def key = "personal-details"}

  case object SCHEME_PREFERENCES extends ProgressStatus(ApplicationStatus.IN_PROGRESS) {
    override def key = "scheme-preferences"}

  case object LOCATION_PREFERENCES extends ProgressStatus(ApplicationStatus.IN_PROGRESS) {
    override def key = "location-preferences"}

  case object ASSISTANCE_DETAILS extends ProgressStatus(ApplicationStatus.IN_PROGRESS) {
    override def key = "assistance-details"}

  case object QUESTIONNAIRE_OCCUPATION extends ProgressStatus(ApplicationStatus.IN_PROGRESS)

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
  case object PHASE1_TESTS_FAILED_SDIP_AMBER extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)
  case object PHASE1_TESTS_FAILED_SDIP_GREEN extends ProgressStatus(ApplicationStatus.PHASE1_TESTS)

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
  case object PHASE2_TESTS_FAILED_SDIP_AMBER extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)
  case object PHASE2_TESTS_FAILED_SDIP_GREEN extends ProgressStatus(ApplicationStatus.PHASE2_TESTS)

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
  case object PHASE3_TESTS_FAILED_SDIP_AMBER extends ProgressStatus(ApplicationStatus.PHASE3_TESTS)
  case object PHASE3_TESTS_FAILED_SDIP_GREEN extends ProgressStatus(ApplicationStatus.PHASE3_TESTS)

  // Edip and Sdip status only
  case object PHASE1_TESTS_PASSED_NOTIFIED extends ProgressStatus(ApplicationStatus.PHASE1_TESTS_PASSED_NOTIFIED)
  case object PHASE3_TESTS_PASSED_NOTIFIED extends ProgressStatus(ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED)
  case object FAST_PASS_ACCEPTED extends ProgressStatus(ApplicationStatus.FAST_PASS_ACCEPTED)

  case object APPLICATION_ARCHIVED extends ProgressStatus(ApplicationStatus.ARCHIVED)

  case object SIFT_ENTERED extends ProgressStatus(ApplicationStatus.SIFT)
  case object SIFT_TEST_INVITED extends ProgressStatus(ApplicationStatus.SIFT)
  case object SIFT_TEST_STARTED extends ProgressStatus(ApplicationStatus.SIFT)
  case object SIFT_TEST_COMPLETED extends ProgressStatus(ApplicationStatus.SIFT)
  case object SIFT_FIRST_REMINDER extends ProgressStatus(ApplicationStatus.SIFT)
  case object SIFT_SECOND_REMINDER extends ProgressStatus(ApplicationStatus.SIFT)
  case object SIFT_FORMS_COMPLETE_NUMERIC_TEST_PENDING extends ProgressStatus(ApplicationStatus.SIFT)
  // TODO: cubiks - think this is now redundant
  case object SIFT_TEST_RESULTS_READY extends ProgressStatus(ApplicationStatus.SIFT)
  case object SIFT_TEST_RESULTS_RECEIVED extends ProgressStatus(ApplicationStatus.SIFT)
  case object SIFT_READY extends ProgressStatus(ApplicationStatus.SIFT)
  case object SIFT_COMPLETED extends ProgressStatus(ApplicationStatus.SIFT)
  case object SIFT_EXPIRED extends ProgressStatus(ApplicationStatus.SIFT)
  case object SIFT_EXPIRED_NOTIFIED extends ProgressStatus(ApplicationStatus.SIFT)
  case object FAILED_AT_SIFT extends ProgressStatus(ApplicationStatus.FAILED_AT_SIFT)
  case object FAILED_AT_SIFT_NOTIFIED extends ProgressStatus(ApplicationStatus.FAILED_AT_SIFT)
  case object SDIP_FAILED_AT_SIFT extends ProgressStatus(ApplicationStatus.SIFT)
  case object SIFT_FASTSTREAM_FAILED_SDIP_GREEN extends ProgressStatus(ApplicationStatus.SIFT)


  case object ProgressStatusOrder
  {
    val relativeOrder = List(
      CREATED,
      PERSONAL_DETAILS, SCHEME_PREFERENCES, ASSISTANCE_DETAILS, QUESTIONNAIRE_OCCUPATION, PREVIEW,
      SUBMITTED, FAST_PASS_ACCEPTED,

      PHASE1_TESTS_INVITED, PHASE1_TESTS_EXPIRED,
      PHASE1_TESTS_STARTED, PHASE1_TESTS_EXPIRED,
      PHASE1_TESTS_COMPLETED, PHASE1_TESTS_RESULTS_READY,
      PHASE1_TESTS_RESULTS_RECEIVED, PHASE1_TESTS_PASSED, PHASE1_TESTS_PASSED_NOTIFIED,
      PHASE1_TESTS_FAILED, PHASE1_TESTS_FAILED_NOTIFIED,

      PHASE2_TESTS_INVITED, PHASE2_TESTS_EXPIRED,
      PHASE2_TESTS_STARTED, PHASE2_TESTS_EXPIRED,
      PHASE2_TESTS_COMPLETED, PHASE2_TESTS_RESULTS_RECEIVED, PHASE2_TESTS_PASSED,
      PHASE2_TESTS_FAILED, PHASE2_TESTS_FAILED_NOTIFIED,

      PHASE3_TESTS_INVITED, PHASE3_TESTS_EXPIRED,
      PHASE3_TESTS_STARTED, PHASE3_TESTS_EXPIRED,
      PHASE3_TESTS_COMPLETED, PHASE3_TESTS_RESULTS_RECEIVED, PHASE3_TESTS_PASSED, PHASE3_TESTS_PASSED_NOTIFIED,
      PHASE3_TESTS_FAILED, PHASE3_TESTS_FAILED_NOTIFIED,

      SIFT_ENTERED, SIFT_FORMS_COMPLETE_NUMERIC_TEST_PENDING, SIFT_TEST_INVITED, SIFT_TEST_STARTED,
      SIFT_TEST_COMPLETED, SIFT_TEST_RESULTS_RECEIVED, SIFT_READY, SIFT_COMPLETED,

      ASSESSMENT_CENTRE_AWAITING_ALLOCATION, ASSESSMENT_CENTRE_ALLOCATION_UNCONFIRMED,
      ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED, ASSESSMENT_CENTRE_SCORES_ENTERED, ASSESSMENT_CENTRE_SCORES_ACCEPTED,
      ASSESSMENT_CENTRE_PASSED, ASSESSMENT_CENTRE_FAILED, ASSESSMENT_CENTRE_FAILED_NOTIFIED,

      FSB_AWAITING_ALLOCATION, FSB_ALLOCATION_CONFIRMED, FSB_RESULT_ENTERED, FSB_PASSED, ELIGIBLE_FOR_JOB_OFFER,

      ALL_FSBS_AND_FSACS_FAILED
    )
    def isBefore(progressStatus1: ProgressStatus, progressStatus2: ProgressStatus): Option[Boolean] = {
      val index1 = relativeOrder. indexOf(progressStatus1)
      val index2 = relativeOrder.indexOf(progressStatus2)
      if (index1 == -1 || index2 == -1) {
        None
      } else {
        Some(index1 < index2)
      }
    }

    def isAfter(progressStatus1: ProgressStatus, progressStatus2: ProgressStatus): Option[Boolean] = {
      val index1 = relativeOrder. indexOf(progressStatus1)
      val index2 = relativeOrder.indexOf(progressStatus2)
      if (index1 == -1 || index2 == -1) {
        None
      } else {
        Some(index1 > index2)
      }
    }

    def isEqualOrAfter(progressStatus1: ProgressStatus, progressStatus2: ProgressStatus): Option[Boolean] = {
      val index1 = relativeOrder.lastIndexOf(progressStatus1)
      val index2 = relativeOrder.indexOf(progressStatus2)
      if (index1 == -1 || index2 == -1) {
        None
      } else {
        Some(index1 >= index2)
      }
    }
  }

  case object ASSESSMENT_CENTRE_AWAITING_ALLOCATION extends ProgressStatus(ApplicationStatus.ASSESSMENT_CENTRE)
  case object ASSESSMENT_CENTRE_ALLOCATION_UNCONFIRMED extends ProgressStatus(ApplicationStatus.ASSESSMENT_CENTRE)
  case object ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED extends ProgressStatus(ApplicationStatus.ASSESSMENT_CENTRE)
  case object ASSESSMENT_CENTRE_FAILED_TO_ATTEND extends ProgressStatus(ApplicationStatus.ASSESSMENT_CENTRE)
  case object ASSESSMENT_CENTRE_SCORES_ENTERED extends ProgressStatus(ApplicationStatus.ASSESSMENT_CENTRE)
  case object ASSESSMENT_CENTRE_SCORES_ACCEPTED extends ProgressStatus(ApplicationStatus.ASSESSMENT_CENTRE)
  case object ASSESSMENT_CENTRE_AWAITING_RE_EVALUATION extends ProgressStatus(ApplicationStatus.ASSESSMENT_CENTRE)
  case object ASSESSMENT_CENTRE_PASSED extends ProgressStatus(ApplicationStatus.ASSESSMENT_CENTRE)
  case object ASSESSMENT_CENTRE_FAILED extends ProgressStatus(ApplicationStatus.ASSESSMENT_CENTRE)
  case object ASSESSMENT_CENTRE_FAILED_NOTIFIED extends ProgressStatus(ApplicationStatus.ASSESSMENT_CENTRE)
  case object ASSESSMENT_CENTRE_FAILED_SDIP_GREEN extends ProgressStatus(ApplicationStatus.ASSESSMENT_CENTRE)
  case object ASSESSMENT_CENTRE_FAILED_SDIP_GREEN_NOTIFIED extends ProgressStatus(ApplicationStatus.ASSESSMENT_CENTRE)

  // FSB statuses are specially archived. If you're changing these also consult hard coded lists
  // in AssessmentCentretoFsbOrOfferProgressionService
  case object FSB_AWAITING_ALLOCATION extends ProgressStatus(ApplicationStatus.FSB)
  case object FSB_ALLOCATION_UNCONFIRMED extends ProgressStatus(ApplicationStatus.FSB)
  case object FSB_ALLOCATION_CONFIRMED extends ProgressStatus(ApplicationStatus.FSB)
  case object FSB_FAILED_TO_ATTEND extends ProgressStatus(ApplicationStatus.FSB)
  case object FSB_RESULT_ENTERED extends ProgressStatus(ApplicationStatus.FSB)
  case object FSB_PASSED extends ProgressStatus(ApplicationStatus.FSB)
  case object FSB_FAILED extends ProgressStatus(ApplicationStatus.FSB)
  case object FSB_FSAC_REEVALUATION_JOB_OFFER extends ProgressStatus(ApplicationStatus.FSB)
  case object ALL_FSBS_AND_FSACS_FAILED extends ProgressStatus(ApplicationStatus.FSB)
  case object ALL_FSBS_AND_FSACS_FAILED_NOTIFIED extends ProgressStatus(ApplicationStatus.FSB)

  case object ELIGIBLE_FOR_JOB_OFFER extends ProgressStatus(ApplicationStatus.ELIGIBLE_FOR_JOB_OFFER)
  case object ELIGIBLE_FOR_JOB_OFFER_NOTIFIED extends ProgressStatus(ApplicationStatus.ELIGIBLE_FOR_JOB_OFFER)

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

  def nameToProgressStatus(name: String): ProgressStatus = {
    nameToProgressStatusMap(
      name.toLowerCase match {
        case "personal_details" => "personal-details"
        case "assistance_details" => "assistance-details"
        case "scheme_preferences" => "scheme-preferences"
        case _ => name.toLowerCase
      })
  }

  // Reflection is generally 'A bad thing' but in this case it ensures that all progress statues are taken into account
  // Had considered an implementation with a macro, but that would need defining in another compilation unit
  // As it is a val in a object, it is only run once upon startup

  // TODO  this does NOT contain all the statuses as it cannot instantiate the SDIP statuses,
  // so we're a few progress statuses short of an application anywhere we're using this list.
  private[model] val allStatuses: Seq[ProgressStatus] = {
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

  private[model] val nameToProgressStatusMap: Map[String, ProgressStatus] = {
    allStatuses.map { value =>
      value.key.toLowerCase -> value
    }.toMap
  }

  def tryToGetDefaultProgressStatus(applicationStatus: ApplicationStatus): Option[ProgressStatus] = {
    val matching = allStatuses.filter(_.applicationStatus == applicationStatus)
    if (matching.size == 1) matching.headOption else None
  }

  @deprecated("This is not exhaustive, do not use please.", "July 2017")
  def progressesByApplicationStatus(applicationStatuses: ApplicationStatus*): Seq[ProgressStatus] = {
    allStatuses.filter(st => applicationStatuses.contains(st.applicationStatus))
  }

  object EventProgressStatuses {

    case class EventProgressStatus(
      awaitingAllocation: ProgressStatuses.ProgressStatus,
      allocationUnconfirmed: ProgressStatuses.ProgressStatus,
      allocationConfirmed: ProgressStatuses.ProgressStatus,
      failedToAttend: ProgressStatuses.ProgressStatus
    )

    private val fsb = EventProgressStatus(
      FSB_AWAITING_ALLOCATION, FSB_ALLOCATION_UNCONFIRMED, FSB_ALLOCATION_CONFIRMED, FSB_FAILED_TO_ATTEND
    )
    private val assessmentCentre = EventProgressStatus(
      ASSESSMENT_CENTRE_AWAITING_ALLOCATION,
      ASSESSMENT_CENTRE_ALLOCATION_UNCONFIRMED,
      ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED,
      ASSESSMENT_CENTRE_FAILED_TO_ATTEND
    )

    def get(applicationStatus: ApplicationStatus) = applicationStatus match {
      case FSB => fsb
      case ASSESSMENT_CENTRE => assessmentCentre
    }
  }
}
// scalastyle:on
