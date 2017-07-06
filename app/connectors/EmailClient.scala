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

package connectors

import java.util.TimeZone

import config.{ EmailConfig, WSHttp }
import connectors.ExchangeObjects._
import org.joda.time.{ DateTime, DateTimeZone, LocalDate, LocalDateTime }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.TimeUnit

object CSREmailClient extends CSREmailClient {
  val emailConfig: EmailConfig = config.MicroserviceAppConfig.emailConfig
}

object Phase2OnlineTestEmailClient extends OnlineTestEmailClient with EmailClient {
  val emailConfig: EmailConfig = config.MicroserviceAppConfig.emailConfig

  override def sendOnlineTestInvitation(
    to: String,
    name: String,
    expireDateTime: DateTime
  )(implicit hc: HeaderCarrier): Future[Unit] = sendEmail(
    to,
    "fset_faststream_app_phase2_test_invitation",
    Map("expireDateTime" -> EmailDateFormatter.toExpiryTime(expireDateTime), "name" -> name)
  )

  override def sendTestExpiringReminder(to: String, name: String, timeLeftInHours: Int,
    timeUnit: TimeUnit, expiryDate: DateTime)(implicit hc: HeaderCarrier): Future[Unit] = {
    sendExpiringReminder("fset_faststream_app_online_phase2_test_reminder", to, name, timeLeftInHours, timeUnit, expiryDate)
  }

  override def sendOnlineTestFailed(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit] = sendEmail(
    to,
    "csr_app_online_test_failed",
    Map("name" -> name)
  )
}

object Phase3OnlineTestEmailClient extends OnlineTestEmailClient with EmailClient {
  val emailConfig: EmailConfig = config.MicroserviceAppConfig.emailConfig

  override def sendOnlineTestInvitation(
    to: String,
    name: String,
    expireDateTime: DateTime
  )(implicit hc: HeaderCarrier): Future[Unit] = sendEmail(
    to,
    "fset_faststream_app_phase3_test_invitation",
    Map("expireDateTime" -> EmailDateFormatter.toExpiryTime(expireDateTime), "name" -> name)
  )

  override def sendTestExpiringReminder(to: String, name: String, timeLeftInHours: Int,
    timeUnit: TimeUnit, expiryDate: DateTime)(implicit hc: HeaderCarrier): Future[Unit] =
    sendExpiringReminder("fset_faststream_app_online_phase3_test_reminder", to, name, timeLeftInHours, timeUnit, expiryDate)

  override def sendOnlineTestFailed(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit] =
    sendEmail(
      to,
      "csr_app_online_test_failed",
      Map("name" -> name)
    )
}

trait CSREmailClient extends OnlineTestEmailClient with AssessmentCentreEmailClient with EmailClient {

  override def sendOnlineTestInvitation(to: String, name: String, expireDateTime: DateTime)(implicit hc: HeaderCarrier): Future[Unit] =
    sendEmail(
      to,
      "fset_faststream_app_online_test_invitation",
      Map("expireDateTime" -> EmailDateFormatter.toExpiryTime(expireDateTime), "name" -> name)
    )

  override def sendTestExpiringReminder(to: String, name: String, timeLeftInHours: Int,
    timeUnit: TimeUnit, expiryDate: DateTime)(implicit hc: HeaderCarrier): Future[Unit] = {
    sendExpiringReminder("fset_faststream_app_online_phase1_test_reminder", to, name, timeLeftInHours, timeUnit, expiryDate)
  }

  override def sendOnlineTestFailed(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit] =
    sendEmail(
      to,
      "csr_app_online_test_failed",
      Map("name" -> name)
    )

  override def sendConfirmAttendance(to: String, name: String, assessmentDateTime: DateTime, confirmByDate: LocalDate)(
    implicit
    hc: HeaderCarrier
  ): Future[Unit] =
    sendEmail(
      to,
      "csr_app_confirm_attendance",
      Map(
        "name" -> name,
        "assessmentDateTime" -> EmailDateFormatter.toConfirmTime(assessmentDateTime),
        "confirmByDate" -> EmailDateFormatter.toDate(confirmByDate)
      )
    )

  override def sendReminderToConfirmAttendance(to: String, name: String, assessmentDateTime: DateTime,
    confirmByDate: LocalDate)(implicit hc: HeaderCarrier): Future[Unit] =
    sendEmail(
      to,
      "csr_app_confirm_attendance_reminder",
      Map(
        "name" -> name,
        "assessmentDateTime" -> EmailDateFormatter.toConfirmTime(assessmentDateTime),
        "confirmByDate" -> EmailDateFormatter.toDate(confirmByDate)
      )
    )

  override def sendAssessmentCentrePassed(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    sendEmail(
      to,
      "csr_app_assessment_centre_passed",
      Map("name" -> name)
    )
  }

  override def sendAssessmentCentreFailed(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    sendEmail(
      to,
      "csr_app_assessment_centre_failed",
      Map("name" -> name)
    )
  }
}

sealed trait OnlineTestEmailClient {

  self: EmailClient =>

  def sendOnlineTestInvitation(to: String, name: String, expireDateTime: DateTime)(implicit hc: HeaderCarrier): Future[Unit]
  def sendEmailWithName(to: String, name: String, template: String)(implicit hc: HeaderCarrier): Future[Unit] = sendEmail(
    to,
    template,
    Map("name" -> name)
  )
  def sendTestExpiringReminder(to: String, name: String, timeLeftInHours: Int,
    timeUnit: TimeUnit, expiryDate: DateTime)(implicit hc: HeaderCarrier): Future[Unit]

  protected def sendExpiringReminder(template: String, to: String, name: String, timeLeftInHours: Int,
    timeUnit: TimeUnit, expiryDate: DateTime)(implicit hc: HeaderCarrier): Future[Unit] = {
    sendEmail(
      to,
      template,
      Map(
        "name" -> name,
        "expireDateTime" -> EmailDateFormatter.toExpiryTime(expiryDate),
        "timeUnit" -> timeUnit.toString.toLowerCase,
        "timeLeft" -> EmailDateFormatter.convertToHoursOrDays(timeUnit, timeLeftInHours)
      )
    )
  }

  def sendOnlineTestFailed(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit]
}

trait AssessmentCentreEmailClient {
  def sendConfirmAttendance(to: String, name: String, assessmentDateTime: DateTime,
    confirmByDate: LocalDate)(implicit hc: HeaderCarrier): Future[Unit]
  def sendReminderToConfirmAttendance(to: String, name: String, assessmentDateTime: DateTime,
    confirmByDate: LocalDate)(implicit hc: HeaderCarrier): Future[Unit]
  def sendAssessmentCentrePassed(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit]
  def sendAssessmentCentreFailed(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit]
}

trait EmailClient extends WSHttp {
  val emailConfig: EmailConfig

  protected def sendEmail(to: String, template: String, parameters: Map[String, String])(implicit hc: HeaderCarrier): Future[Unit] = {
    val data = SendFsetMailRequest(
      to :: Nil,
      template,
      parameters ++ Map("programme" -> "faststream")
    )
    POST(s"${emailConfig.url}/fsetfaststream/email", data, Seq()).map(_ => (): Unit)
  }

  def sendApplicationSubmittedConfirmation(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit] =
    sendEmail(to, "fset_faststream_app_submit_confirmation", Map("name" -> name))

  def sendWithdrawnConfirmation(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit] =
    sendEmail(to, "fset_faststream_app_withdrawn", Map("name" -> name))

  def sendAdjustmentsConfirmation(to: String, name: String, etrayAdjustments: String,
    videoAdjustments: String)(implicit hc: HeaderCarrier): Future[Unit] =
    sendEmail(to, "fset_faststream_adjustments_confirmation",
      Map("name" -> name, "etrayAdjustments" -> etrayAdjustments, "videoAdjustments" -> videoAdjustments))

  def sendAdjustmentsUpdateConfirmation(to: String, name: String, etrayAdjustments: String,
    videoAdjustments: String)(implicit hc: HeaderCarrier): Future[Unit] =
    sendEmail(to, "fset_faststream_adjustments_changed",
      Map("name" -> name, "etrayAdjustments" -> etrayAdjustments, "videoAdjustments" -> videoAdjustments))

  def sendApplicationExtendedToSdip(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit] =
    sendEmail(to, "fset_faststream_app_converted_to_sdip_confirmation", Map("name" -> name))

}

object EmailDateFormatter {

  import scala.concurrent.duration.DAYS

  def toDate(date: LocalDate): String = date.toString("d MMMM yyyy")

  protected def toLondonLocalDateTime(dateTime: DateTime): LocalDateTime =
    dateTime.toDateTime(DateTimeZone.forTimeZone(TimeZone.getTimeZone("Europe/London"))).toLocalDateTime

  def toExpiryTime(dateTime: DateTime): String = {
    toLondonLocalDateTime(dateTime).toString("d MMMM yyyy 'at' h:mma")
      .replace("AM", "am").replace("PM", "pm") // Joda time has no easy way to change the case of AM/PM
  }

  def toConfirmTime(dateTime: DateTime): String = {
    toLondonLocalDateTime(dateTime).toString("d MMMM yyyy, h:mma")
      .replace("AM", "am").replace("PM", "pm") // Joda time has no easy way to change the case of AM/PM
  }

  def convertToHoursOrDays(timeUnit: TimeUnit, timeLeftInHours: Int): String = {
    if (timeUnit == DAYS) { (timeLeftInHours / 24).toString }
    else { timeLeftInHours.toString }
  }
}
