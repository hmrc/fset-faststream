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
import model.ReminderNotice
import model.exchange.CubiksTestResultReady
import model.persisted.{ CubiksTest, NotificationExpiringOnlineTest }
import org.joda.time.DateTime
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
  def processNextTestForReminder(reminder: ReminderNotice)(implicit hc: HeaderCarrier): Future[Unit]

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
                                                      reminder: ReminderNotice)(implicit hc: HeaderCarrier): Future[Unit]

  protected def audit(event: String, userId: String, emailAddress: Option[String] = None): Unit = {
    Logger.info(s"$event for user $userId")

    auditService.logEventNoRequest(
      event,
      Map("userId" -> userId) ++ emailAddress.map("email" -> _).toMap
    )
  }

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

  private def candidateEmailAddress(userId: String): Future[String] =
    cdRepository.find(userId).map(_.email)

  protected def processReminder(expiringTest: NotificationExpiringOnlineTest,
                                reminder: ReminderNotice)(implicit hc: HeaderCarrier): Future[Unit] =
    for {
      emailAddress <- candidateEmailAddress(expiringTest.userId)
      _ <- commitNotificationExpiringTestProgressStatus(expiringTest, reminder, emailAddress)
      _ <- emailCandidateForExpiringTestReminder(expiringTest, emailAddress, reminder)
    } yield ()

  private def commitNotificationExpiringTestProgressStatus(
                                                            expiringTest: NotificationExpiringOnlineTest,
                                                            reminder: ReminderNotice,
                                                            email: String): Future[Unit] = {
    appRepository.addProgressStatusAndUpdateAppStatus(expiringTest.applicationId, reminder.progressStatuses).map { _ =>
      reminder.progressStatuses match {
        case PHASE1_TESTS_FIRST_REMINDER => audit(s"FirstPhase1ReminderFor${reminder.hoursBeforeReminder}Hours",
          expiringTest.userId, Some(email))
        case PHASE1_TESTS_SECOND_REMINDER => audit(s"SecondPhase1ReminderFor${reminder.hoursBeforeReminder}Hours",
          expiringTest.userId, Some(email))
        case PHASE2_TESTS_FIRST_REMINDER => audit(s"FirstPhase2ReminderFor${reminder.hoursBeforeReminder}Hours",
          expiringTest.userId, Some(email))
        case PHASE2_TESTS_SECOND_REMINDER => audit(s"SecondPhase2ReminderFor${reminder.hoursBeforeReminder}Hours",
          expiringTest.userId, Some(email))
      }
    }
  }

  protected def extendTime(alreadyExpired: Boolean, previousExpirationDate: DateTime, clock: DateTimeFactory) = { extraDays: Int =>
    if (alreadyExpired) {
      clock.nowLocalTimeZone.plusDays(extraDays)
    } else {
      previousExpirationDate.plusDays(extraDays)
    }
  }
}
