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

package connectors

import config.{EmailConfig, MicroserviceAppConfig}
import connectors.ExchangeObjects._

import javax.inject.{Inject, Singleton}
import model.stc.EmailEvents.{CandidateAllocationConfirmationReminder, CandidateAllocationConfirmationRequest}
import play.api.Logging
import play.api.libs.json.Json
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.TimeUnit
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import java.time.{LocalDate, LocalDateTime, OffsetDateTime, ZoneId}
import java.time.format.DateTimeFormatter

@Singleton
class CSREmailClientImpl @Inject() (val http: HttpClientV2, val appConfig: MicroserviceAppConfig)(
  implicit ec: ExecutionContext) extends CSREmailClient {
  override val emailConfig: EmailConfig = appConfig.emailConfig
}

@Singleton
class Phase2OnlineTestEmailClient @Inject() (val http: HttpClientV2, val appConfig: MicroserviceAppConfig)
  extends OnlineTestEmailClient with EmailClient {
  override val emailConfig: EmailConfig = appConfig.emailConfig

  override def sendOnlineTestInvitation(to: String, name: String, expireDateTime: OffsetDateTime)
                                       (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sendEmail(to,"fset_faststream_app_phase2_test_invitation",
      Map("expireDateTime" -> EmailDateFormatter.toExpiryTime(expireDateTime), "name" -> name)
    )

  override def sendTestExpiringReminder(to: String, name: String, timeLeftInHours: Int,
                                        timeUnit: TimeUnit, expiryDate: OffsetDateTime)
                                       (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    sendExpiringReminder("fset_faststream_app_online_phase2_test_reminder", to, name, timeLeftInHours, timeUnit, expiryDate)
  }

  override def sendOnlineTestFailed(to: String, name: String)
                                   (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sendEmail(to,"csr_app_online_test_failed",
      Map("name" -> name)
    )
}

@Singleton
class Phase3OnlineTestEmailClient @Inject() (val http: HttpClientV2, val appConfig: MicroserviceAppConfig)(implicit ec: ExecutionContext)
  extends OnlineTestEmailClient with EmailClient {
  override val emailConfig: EmailConfig = appConfig.emailConfig

  override def sendOnlineTestInvitation(to: String, name: String, expireDateTime: OffsetDateTime)
                                       (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sendEmail(to,"fset_faststream_app_phase3_test_invitation",
      Map("expireDateTime" -> EmailDateFormatter.toExpiryTime(expireDateTime), "name" -> name)
    )

  override def sendTestExpiringReminder(to: String, name: String, timeLeftInHours: Int,
                                        timeUnit: TimeUnit, expiryDate: OffsetDateTime)
                                       (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sendExpiringReminder("fset_faststream_app_online_phase3_test_reminder", to, name, timeLeftInHours, timeUnit, expiryDate)

  override def sendOnlineTestFailed(to: String, name: String)
                                   (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sendEmail(to,"csr_app_online_test_failed",
      Map("name" -> name)
    )
}

trait CSREmailClient extends OnlineTestEmailClient with AssessmentCentreEmailClient with EmailClient {

  override def sendOnlineTestInvitation(to: String, name: String, expireDateTime: OffsetDateTime)(
    implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sendEmail(to,"fset_faststream_app_online_test_invitation",
      Map("expireDateTime" -> EmailDateFormatter.toExpiryTime(expireDateTime), "name" -> name)
    )

  override def sendTestExpiringReminder(to: String, name: String, timeLeftInHours: Int, timeUnit: TimeUnit, expiryDate: OffsetDateTime)(
    implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    sendExpiringReminder("fset_faststream_app_online_phase1_test_reminder", to,name,timeLeftInHours, timeUnit, expiryDate)
  }

  override def sendOnlineTestFailed(to: String, name: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sendEmail(to,"csr_app_online_test_failed",
      Map("name" -> name)
    )

  override def sendConfirmAttendance(to: String, name: String, assessmentDateTime: OffsetDateTime, confirmByDate: LocalDate)(
    implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sendEmail(to,"csr_app_confirm_attendance",
      Map(
        "name" -> name,
        "assessmentDateTime" -> EmailDateFormatter.toConfirmTime(assessmentDateTime),
        "confirmByDate" -> EmailDateFormatter.toDate(confirmByDate)
      )
    )

  override def sendReminderToConfirmAttendance(to: String, name: String, assessmentDateTime: OffsetDateTime,
                                               confirmByDate: LocalDate)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sendEmail(to,"csr_app_confirm_attendance_reminder",
      Map(
        "name" -> name,
        "assessmentDateTime" -> EmailDateFormatter.toConfirmTime(assessmentDateTime),
        "confirmByDate" -> EmailDateFormatter.toDate(confirmByDate)
      )
    )

  override def sendAssessmentCentrePassed(to: String, name: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    sendEmail(to,"csr_app_assessment_centre_passed",
      Map("name" -> name)
    )
  }

  override def sendAssessmentCentreFailed(to: String, name: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    sendEmail(to,"csr_app_assessment_centre_failed",
      Map("name" -> name)
    )
  }
}

//sealed trait OnlineTestEmailClient extends EmailClient {
trait OnlineTestEmailClient extends EmailClient {

  //  self: EmailClient =>

  def sendOnlineTestInvitation(to: String, name: String, expireDateTime: OffsetDateTime)(
    implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit]

  def sendEmailWithName(to: String, name: String, template: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sendEmail(
      to,
      template,
      Map("name" -> name)
    )

  def sendTestExpiringReminder(to: String, name: String, timeLeftInHours: Int,
                               timeUnit: TimeUnit, expiryDate: OffsetDateTime)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit]

  protected def sendExpiringReminder(template: String, to: String,
                                     name: String, timeLeftInHours: Int, timeUnit: TimeUnit, expiryDate: OffsetDateTime)(
                                      implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    sendEmail(
      to,
      template,
      Map("name" -> name,
        "expireDateTime" -> EmailDateFormatter.toExpiryTime(expiryDate),
        "timeUnit" -> timeUnit.toString.toLowerCase,
        "timeLeft" -> EmailDateFormatter.convertToHoursOrDays(timeUnit, timeLeftInHours)
      )
    )
  }

  def sendOnlineTestFailed(to: String, name: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit]
}

trait AssessmentCentreEmailClient {
  def sendConfirmAttendance(to: String, name: String, assessmentDateTime: OffsetDateTime,
                            confirmByDate: LocalDate)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit]

  def sendReminderToConfirmAttendance(to: String, name: String, assessmentDateTime: OffsetDateTime,
                                      confirmByDate: LocalDate)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit]

  def sendAssessmentCentrePassed(to: String, name: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit]

  def sendAssessmentCentreFailed(to: String, name: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit]
}

trait EmailClient extends Logging {
  val http: HttpClientV2
  val emailConfig: EmailConfig

  protected def sendEmail(to: String, template: String, parameters: Map[String, String])(
    implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    val data = SendFsetMailRequest(
      to :: Nil,
      template,
      parameters ++ Map("programme" -> "faststream")
    )
    if (emailConfig.enabled) {
      import uk.gov.hmrc.http.HttpReads.Implicits._
      import play.api.libs.ws.writeableOf_JsValue
      http.post(url"${emailConfig.url}/fsetfaststream/email")
        .withBody(Json.toJson(data))
        .execute[HttpResponse]
        .map(_ => (): Unit)
    } else {
      logger.warn(s"EmailClient is attempting to send out template $template but is DISABLED")
      Future.successful(())
    }
  }

  def sendApplicationSubmittedConfirmation(to: String, name: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sendEmail(to, "fset_faststream_app_submit_confirmation", Map("name" -> name))

  def sendApplicationPostSubmittedCheckFailed(to: String, name: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sendEmail(to, "fset_faststream_app_submit_check_failed", Map("name" -> name))

  def sendApplicationPostSubmittedCheckPassed(to: String, name: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sendEmail(to, "fset_faststream_app_submit_check_passed", Map("name" -> name))

  def sendWithdrawnConfirmation(to: String, name: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sendEmail(to, "fset_faststream_app_withdrawn", Map("name" -> name))

  def sendAdjustmentsConfirmation(to: String, name: String, etrayAdjustments: String,
                                  videoAdjustments: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sendEmail(to, "fset_faststream_adjustments_confirmation",
      Map("name" -> name, "etrayAdjustments" -> etrayAdjustments, "videoAdjustments" -> videoAdjustments))

  def sendAdjustmentsUpdateConfirmation(to: String, name: String, etrayAdjustments: String,
                                        videoAdjustments: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sendEmail(to, "fset_faststream_adjustments_changed",
      Map("name" -> name, "etrayAdjustments" -> etrayAdjustments, "videoAdjustments" -> videoAdjustments))

  def sendApplicationExtendedToSdip(to: String, name: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sendEmail(to, "fset_faststream_app_converted_to_sdip_confirmation", Map("name" -> name))

  def sendCandidateConfirmationRequestToEvent(c: CandidateAllocationConfirmationRequest)(
    implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    sendEmail(c.to, "fset_faststream_candidate_need_confirm_assessment_date",
      Map("name" -> c.name, "eventDate" -> c.eventDate, "eventStartTime" -> c.eventTime,
        "eventType" -> c.eventType, "eventVenue" -> c.eventVenue, "deadlineDate" -> c.deadlineDate,
        "eventGuideUrl" -> c.eventGuideUrl))
  }

  def sendCandidateConfirmationRequestReminderToEvent(c: CandidateAllocationConfirmationReminder)(
    implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    sendEmail(c.to, "fset_faststream_candidate_need_confirm_assessment_date_reminder",
      Map("name" -> c.name, "eventDate" -> c.eventDate, "eventStartTime" -> c.eventTime, "eventType" -> c.eventType,
        "eventVenue" -> c.eventVenue, "deadlineDate" -> c.deadlineDate, "eventGuideUrl" -> c.eventGuideUrl))
  }

  //scalastyle:off parameter.number
  def sendCandidateInvitationConfirmedToEvent(to: String, name: String, eventDate: String, eventTime: String,
                                              eventType: String, eventVenue: String, eventGuideUrl: String)(
    implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    sendEmail(to, "fset_faststream_candidate_assessment_scheduled",
      Map("name" -> name, "eventDate" -> eventDate, "eventStartTime" -> eventTime,
        "eventType" -> eventType, "eventVenue" -> eventVenue, "eventGuideUrl" -> eventGuideUrl))
  }
  // scalastyle:on

  // scalastyle:off parameter.number
  def sendAssessorAllocatedToEvent(to: String, name: String, eventDate: String, eventRole: String, eventRoleKey: String,
                                   eventName: String, eventLocation: String, eventStartTime: String)(
                                    implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    sendEmail(to, "fset_faststream_notify_event_assessor_allocated",
      Map("name" -> name, "eventDate" -> eventDate, "eventRole" -> eventRole, "eventRoleKey" -> eventRoleKey,
        "eventName" -> eventName, "eventLocation" -> eventLocation, "eventStartTime" -> eventStartTime)
    )
  }
  // scalastyle:on

  def sendAssessorUnAllocatedFromEvent(to: String, name: String, eventDate: String)(
    implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    sendEmail(to, "fset_faststream_notify_event_assessor_unallocated",
      Map("name" -> name, "eventDate" -> eventDate)
    )
  }

  def sendAssessorEventAllocationChanged(to: String, name: String, eventDate: String, eventRole: String, eventName: String,
                                         eventLocation: String, eventStartTime: String)(
                                          implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    sendEmail(to, "fset_faststream_notify_event_assessor_allocation_changed",
      Map("name" -> name, "eventDate" -> eventDate, "eventRole" -> eventRole, "eventName" -> eventName, "eventLocation" -> eventLocation,
        "eventStartTime" -> eventStartTime)
    )
  }

  def sendCandidateAssessmentCompletedMovedToFsb(to: String, name: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    sendEmail(to, "fset_faststream_candidate_assessment_centre_completed", Map("name" -> name))
  }

  def sendCandidateUnAllocatedFromEvent(to: String, name: String, eventDate: String)(
    implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    sendEmail(to, "fset_faststream_notify_event_candidate_unallocated",
      Map("name" -> name, "eventDate" -> eventDate)
    )
  }

  def notifyAssessorsOfNewEvents(to: String, name: String, htmlBody: String, txtBody: String)(
    implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    sendEmail(to, "fset_faststream_notify_assessors_of_new_events",
      Map("name" -> name, "htmlBody" -> htmlBody, "txtBody" -> txtBody))
  }

  def notifyCandidateOnFinalFailure(to: String, name: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    sendEmail(to, "fset_faststream_app_final_failed", Map("name" -> name))
  }

  def notifyCandidateOnFinalSuccess(to: String, name: String, scheme: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    sendEmail(to, "fset_faststream_app_final_success", Map("name" -> name, "scheme" -> scheme))
  }

  def notifyCandidateSiftEnteredAdditionalQuestions(to: String, name: String, expireDateTime: OffsetDateTime)(
    implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    sendEmail(to, "fset_faststream_notify_candidate_sift_entered_additional_questions",
      Map("name" -> name, "expireDateTime" -> EmailDateFormatter.toExpiryTime(expireDateTime))
    )
  }

  def sendSiftNumericTestInvite(to: String, name: String, expiryDate: OffsetDateTime)(
    implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    sendEmail(to,"fset_faststream_sift_numeric_test_invitation",
      Map("name" -> name,
        "expireDateTime" -> EmailDateFormatter.toExpiryTime(expiryDate)
      )
    )
  }

  def sendSiftReminder(to: String, name: String, timeLeftInHours: Int,
                       timeUnit: TimeUnit, expiryDate: OffsetDateTime)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    sendEmail(to,"fset_faststream_sift_reminder",
      Map("name" -> name,
        "expireDateTime" -> EmailDateFormatter.toExpiryTime(expiryDate),
        "timeUnit" -> timeUnit.toString.toLowerCase,
        "timeLeft" -> EmailDateFormatter.convertToHoursOrDays(timeUnit, timeLeftInHours)
      )
    )
  }

  def sendSiftExpired(to: String, name: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sendEmail(to, "fset_faststream_sift_expired", Map("name" -> name))
}

object EmailDateFormatter {

  import scala.concurrent.duration.DAYS

  def toDate(date: LocalDate): String = DateTimeFormatter.ofPattern("d MMMM yyyy").format(date)

  protected def toLondonLocalDateTime(dateTime: OffsetDateTime): LocalDateTime =
    dateTime.atZoneSameInstant(ZoneId.of("Europe/London")).toLocalDateTime

  def toExpiryTime(dateTime: OffsetDateTime): String = {
    DateTimeFormatter.ofPattern("d MMMM yyyy 'at' h:mma").format(toLondonLocalDateTime(dateTime))
      .replace("AM", "am").replace("PM", "pm") // No easy way to change the case of AM/PM
  }

  def toConfirmTime(dateTime: OffsetDateTime): String = {
    DateTimeFormatter.ofPattern("d MMMM yyyy, h:mma").format(toLondonLocalDateTime(dateTime))
      .replace("AM", "am").replace("PM", "pm") // No easy way to change the case of AM/PM
  }

  def convertToHoursOrDays(timeUnit: TimeUnit, timeLeftInHours: Int): String = {
    if(timeUnit == DAYS) { (timeLeftInHours / 24).toString }
    else { timeLeftInHours.toString }
  }
}
