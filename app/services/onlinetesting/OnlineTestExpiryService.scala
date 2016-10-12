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

import connectors.CSREmailClient
import model.persisted.{ ExpiringOnlineTest, NotificationExpiringOnlineTest }
import model.ProgressStatuses.{ PHASE1_TESTS_EXPIRED, PHASE1_TESTS_FIRST_REMINDER, PHASE1_TESTS_SECOND_REMINDER }
import model.ReminderNotice
import play.api.Logger
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.Phase1TestRepository
import services.AuditService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

trait OnlineTestExpiryService {
  def processNextTestForReminder(reminder: ReminderNotice): Future[Unit]
  def processNextExpiredTest(): Future[Unit]
  def commitExpiredProgressStatus(expiringTest: ExpiringOnlineTest): Future[Unit]
}

class OnlineTestExpiryServiceImpl(
  appRepository: GeneralApplicationRepository,
  otRepository: Phase1TestRepository,
  cdRepository: ContactDetailsRepository,
  emailClient: CSREmailClient,
  auditService: AuditService,
  newHeaderCarrier: => HeaderCarrier
)(implicit executor: ExecutionContext) extends OnlineTestExpiryService {

  private implicit def headerCarrier = newHeaderCarrier

  override def processNextTestForReminder(reminder: ReminderNotice): Future[Unit] = {
    otRepository.nextTestForReminder(reminder).flatMap {
      case Some(expiringTest) => processReminder(expiringTest, reminder)
      case None => Future.successful(())
    }
  }

  override def processNextExpiredTest(): Future[Unit] = {
    otRepository.nextExpiringApplication.flatMap {
      case Some(expiredTest) => processExpiredTest(expiredTest)
      case None => Future.successful(())
    }
  }

  override def commitExpiredProgressStatus(expiringTest: ExpiringOnlineTest): Future[Unit] =
    applicationRepository.addProgressStatusAndUpdateAppStatus(expiringTest.applicationId, PHASE1_TESTS_EXPIRED).map { _ =>
      audit("ExpiredOnlineTest", expiringTest)
    }

  private def processReminder(expiringTest: NotificationExpiringOnlineTest, reminder: ReminderNotice): Future[Unit] =
    for {
      emailAddress <- candidateEmailAddress(expiringTest.userId)
      _ <- commitNotificationExpiringTestProgressStatus(expiringTest, reminder)
      _ <- emailCandidateForExpiringTestReminder(expiringTest, emailAddress, reminder)
    } yield ()

  private def processExpiredTest(expiringTest: ExpiringOnlineTest): Future[Unit] =
    for {
      emailAddress <- candidateEmailAddress(expiringTest.userId)
      _ <- emailCandidate(expiringTest, emailAddress)
      _ <- commitExpiredProgressStatus(expiringTest)
    } yield ()

  private def emailCandidate(expiringTest: ExpiringOnlineTest, emailAddress: String): Future[Unit] =
    emailClient.sendOnlineTestExpired(emailAddress, expiringTest.preferredName).map { _ =>
      audit("ExpiredOnlineTestNotificationEmailed", expiringTest, Some(emailAddress))
    }

  private def emailCandidateForExpiringTestReminder(expiringTest: NotificationExpiringOnlineTest,
                                                    emailAddress: String, reminder: ReminderNotice): Future[Unit] = {
    emailClient.sendTestExpiringReminder(emailAddress, expiringTest.preferredName,
      reminder.hoursBeforeReminder, reminder.timeUnit, expiringTest.expiryDate).map { _ =>
      audit(s"ReminderExpiringOnlineTestNotificationBefore${reminder.hoursBeforeReminder}HoursEmailed",
        ExpiringOnlineTest(expiringTest.applicationId, expiringTest.userId, expiringTest.preferredName), Some(emailAddress))
    }
  }

  private def commitNotificationExpiringTestProgressStatus(
                                                            expiringTest: NotificationExpiringOnlineTest,
                                                            reminder: ReminderNotice): Future[Unit] = {

    applicationRepository.addProgressStatusAndUpdateAppStatus(expiringTest.applicationId, reminder.progressStatuses).map { _ =>
      reminder.progressStatuses match {
        case PHASE1_TESTS_FIRST_REMINDER => audit(s"FirstReminderFor${reminder.hoursBeforeReminder}HoursAddedToProgress",
          ExpiringOnlineTest(expiringTest.applicationId, expiringTest.userId, expiringTest.preferredName))
        case PHASE1_TESTS_SECOND_REMINDER => audit(s"SecondReminderFor${reminder.hoursBeforeReminder}HoursAddedToProgress",
          ExpiringOnlineTest(expiringTest.applicationId, expiringTest.userId, expiringTest.preferredName))
      }
    }
  }


  private def candidateEmailAddress(userId: String): Future[String] =
    cdRepository.find(userId).map(_.email)

  private def audit(event: String, expiringTest: ExpiringOnlineTest, emailAddress: Option[String] = None): Unit = {
    // Only log user ID (not email).
    Logger.info(s"$event for user ${expiringTest.userId}")
    auditService.logEventNoRequest(
      event,
      Map("userId" -> expiringTest.userId) ++ emailAddress.map("email" -> _).toMap
    )
  }
}

object OnlineTestExpiryService extends OnlineTestExpiryServiceImpl(
applicationRepository, phase1TestRepository, contactDetailsRepository, CSREmailClient, AuditService, HeaderCarrier()
)