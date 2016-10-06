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

import connectors.EmailClient
import model.ApplicationStatus._
import model.persisted.ApplicationForNotification
import play.api.Logger
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.Phase1TestRepository
import services.AuditService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

trait OnlineTestFailureService {
  def processNextFailedTest(): Future[Unit]
  def processFailedTest(failedTest: ApplicationForNotification): Future[Unit]
  def emailCandidate(failedTest: ApplicationForNotification, emailAddress: String): Future[Unit]
  def commitNotifiedStatus(failedTest: ApplicationForNotification): Future[Unit]
}

class OnlineTestFailureServiceImpl(
  appRepository: GeneralApplicationRepository,
  otRepository: Phase1TestRepository,
  cdRepository: ContactDetailsRepository,
  emailClient: EmailClient,
  auditService: AuditService,
  newHeaderCarrier: => HeaderCarrier
)(implicit executor: ExecutionContext) extends OnlineTestFailureService {

  private implicit def headerCarrier = newHeaderCarrier

  override def processNextFailedTest(): Future[Unit] = {
    //TODO FAST STREAM FIX ME
    Future.successful(Unit)
    //otRepository.nextApplicationPendingFailure.flatMap {
    //  case Some(failedTest) => processFailedTest(failedTest)
    //  case None => Future.successful(())
    //}
  }

  override def processFailedTest(failedTest: ApplicationForNotification): Future[Unit] =
    for {
      emailAddress <- candidateEmailAddress(failedTest.userId)
      _ <- emailCandidate(failedTest, emailAddress)
      _ <- commitNotifiedStatus(failedTest)
    } yield ()

  override def emailCandidate(failedTest: ApplicationForNotification, emailAddress: String): Future[Unit] =
    emailClient.sendOnlineTestFailed(emailAddress, failedTest.preferredName).map { _ =>
      audit("FailedOnlineTestNotificationEmailed", failedTest, Some(emailAddress))
    }

  override def commitNotifiedStatus(failedTest: ApplicationForNotification): Future[Unit] = {
    appRepository.updateStatus(failedTest.userId, ONLINE_TEST_FAILED_NOTIFIED).map { _ =>
      audit("FailedOnlineTest", failedTest)
    }
  }

  private def candidateEmailAddress(userId: String): Future[String] =
    cdRepository.find(userId).map(_.email)

  private def audit(event: String, failedTest: ApplicationForNotification, emailAddress: Option[String] = None): Unit = {
    // Only log user ID (not email).
    Logger.info(s"$event for user ${failedTest.userId}")
    auditService.logEventNoRequest(
      event,
      Map("userId" -> failedTest.userId) ++ emailAddress.map("email" -> _).toMap
    )
  }
}
