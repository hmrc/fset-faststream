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

package mocks

import connectors.EmailClient
import org.joda.time.{ DateTime, LocalDate }
import uk.gov.hmrc.play.http.HeaderCarrier
import scala.concurrent.duration.TimeUnit

import scala.concurrent.Future

object EmailClientStub extends EmailClient {
  override def sendApplicationSubmittedConfirmation(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit] =
    Future.successful(Unit)

  override def sendOnlineTestInvitation(to: String, name: String, expireDateTime: DateTime)(implicit hc: HeaderCarrier): Future[Unit] =
    Future.successful(Unit)

  override def sendOnlineTestExpired(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit] =
    Future.successful(Unit)

  override def sendOnlineTestFailed(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit] =
    Future.successful(Unit)

  override def sendConfirmAttendance(to: String, name: String, assessmentDateTime: DateTime, confirmByDate: LocalDate)(
    implicit
    hc: HeaderCarrier
  ): Future[Unit] =
    Future.successful(Unit)

  override def sendReminderToConfirmAttendance(to: String, name: String, assessmentDateTime: DateTime,
    confirmByDate: LocalDate)(implicit hc: HeaderCarrier): Future[Unit] = Future.successful(Unit)

  override def sendAssessmentCentrePassed(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit] = Future.successful(Unit)

  override def sendAssessmentCentreFailed(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit] = Future.successful(Unit)

  override def sendTestExpiringReminder(to: String, name: String, timeLeft: Int,
                               timeUnit: TimeUnit, expiryDate: DateTime)(implicit hc: HeaderCarrier): Future[Unit] = Future.successful(Unit)

}
