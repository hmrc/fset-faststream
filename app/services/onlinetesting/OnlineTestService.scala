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

package services.onlinetesting

import connectors.OnlineTestEmailClient
import factories.{ DateTimeFactory, UUIDFactory }
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses._
import model.events.DataStoreEvents
import model.exchange.CubiksTestResultReady
import model.persisted.{ CubiksTest, ExpiringOnlineTest, NotificationExpiringOnlineTest }
import model.{ ExpiryTest, ProgressStatuses, ReminderNotice }
import org.joda.time.DateTime
import model.events.{ AuditEvents, EmailEvents }
import play.api.Logger
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import services.AuditService
import services.events.EventSink
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }


trait OnlineTestService extends EventSink  {
  val emailClient: OnlineTestEmailClient
  val auditService: AuditService
  val tokenFactory: UUIDFactory
  val dateTimeFactory: DateTimeFactory
  val cdRepository: ContactDetailsRepository
  val appRepository: GeneralApplicationRepository

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  def nextApplicationReadyForOnlineTesting: Future[List[OnlineTestApplication]]
  def registerAndInviteForTestGroup(application: OnlineTestApplication)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit]
  def registerAndInviteForTestGroup(applications: List[OnlineTestApplication])(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit]
  def processNextExpiredTest(expiryTest: ExpiryTest)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit]
  def processNextTestForReminder(reminder: ReminderNotice)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit]

  protected def emailInviteToApplicant(application: OnlineTestApplication, emailAddress: String,
    invitationDate: DateTime, expirationDate: DateTime
  )(implicit hc: HeaderCarrier): Future[Unit] = {
    val preferredName = application.preferredName
    emailClient.sendOnlineTestInvitation(emailAddress, preferredName, expirationDate).map { _ =>
      audit("OnlineTestInvitationEmailSent", application.userId, Some(emailAddress))
    }
  }

  protected def calcOnlineTestDates(expiryTimeInDays: Int): (DateTime, DateTime) = {
    val invitationDate = dateTimeFactory.nowLocalTimeZone
    val expirationDate = invitationDate.plusDays(expiryTimeInDays)
    (invitationDate, expirationDate)
  }

  protected def emailCandidateForExpiringTestReminder(expiringTest: NotificationExpiringOnlineTest,
                                                      emailAddress: String,
                                                      reminder: ReminderNotice)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit]

  @deprecated("use event sink instead")
  protected def audit(event: String, userId: String, emailAddress: Option[String] = None): Unit = {
    Logger.info(s"$event for user $userId")

    auditService.logEventNoRequest(
      event,
      Map("userId" -> userId) ++ emailAddress.map("email" -> _).toMap
    )
  }

  protected def processExpiredTest(expiringTest: ExpiringOnlineTest, expiryTest: ExpiryTest)
                                  (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = for {
    emailAddress <- candidateEmailAddress(expiringTest.userId)
    _ <- commitProgressStatus(expiringTest, expiryTest.expiredStatus)
    _ <- emailCandidate(expiringTest, emailAddress, expiryTest.template)
  } yield ()

  def updateTestReportReady(cubiksTest: CubiksTest, reportReady: CubiksTestResultReady) = cubiksTest.copy(
    resultsReadyToDownload = reportReady.reportStatus == "Ready",
    reportId = reportReady.reportId,
    reportLinkURL = reportReady.reportLinkURL,
    reportStatus = Some(reportReady.reportStatus)
  )

  def updateCubiksTestsById(cubiksUserId: Int, cubiksTests: List[CubiksTest], updateFn: CubiksTest => CubiksTest) = cubiksTests.collect {
      case t if t.cubiksUserId == cubiksUserId => updateFn(t)
      case t => t
  }

  def assertUniqueTestByCubiksUserId(cubiksTests: List[CubiksTest], cubiksUserId: Int) = {
    val requireUserIdOnOnlyOneTestCount = cubiksTests.count(_.cubiksUserId == cubiksUserId)
    require(requireUserIdOnOnlyOneTestCount == 1, s"Cubiks userid $cubiksUserId was on $requireUserIdOnOnlyOneTestCount tests!")
  }

  private[services] def getAdjustedTime(minimum: Int, maximum: Int, percentageToIncrease: Int) = {
    val adjustedValue = math.ceil(minimum.toDouble * (1 + percentageToIncrease / 100.0))
    math.min(adjustedValue, maximum).toInt
  }

  private def emailCandidate(expiringTest: ExpiringOnlineTest, emailAddress: String, template: String)
                            (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    emailClient.sendOnlineTestExpired(emailAddress, expiringTest.preferredName, template).map { _ =>
      EmailEvents.TestExpired(emailAddress, expiringTest.preferredName, Some(template)) :: Nil
    }
  }

  def commitProgressStatus(expiringTest: ExpiringOnlineTest, status: ProgressStatuses.ProgressStatus)
                          (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    appRepository.addProgressStatusAndUpdateAppStatus(expiringTest.applicationId, status).map { _ =>
      AuditEvents.ApplicationExpired(Map("applicationId" -> expiringTest.applicationId, "status" -> status )) ::
        DataStoreEvents.ApplicationExpired(expiringTest.applicationId) :: Nil
    }
  }

  protected def candidateEmailAddress(userId: String): Future[String] = cdRepository.find(userId).map(_.email)

  protected def processReminder(expiringTest: NotificationExpiringOnlineTest,
                                reminder: ReminderNotice)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] =
    for {
      emailAddress <- candidateEmailAddress(expiringTest.userId)
      _ <- commitNotificationExpiringTestProgressStatus(expiringTest, reminder, emailAddress)
      _ <- emailCandidateForExpiringTestReminder(expiringTest, emailAddress, reminder)
    } yield ()

  private def commitNotificationExpiringTestProgressStatus(
    expiringTest: NotificationExpiringOnlineTest,
    reminder: ReminderNotice,
    email: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    appRepository.addProgressStatusAndUpdateAppStatus(expiringTest.applicationId, reminder.progressStatuses).map { _ =>
      AuditEvents.ApplicationExpiryReminder(Map("applicationId" -> expiringTest.applicationId, "status" -> reminder.progressStatuses )) ::
        DataStoreEvents.ApplicationExpiryReminder(expiringTest.applicationId) :: Nil
    }
  }
}
