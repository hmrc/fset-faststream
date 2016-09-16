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
import model.PersistedObjects.ExpiringOnlineTest
import play.api.Logger
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.onlinetests.OnlineTestRepository
import services.AuditService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

trait OnlineTestExpiryService {
  def processNextExpiredTest(): Future[Unit]
  def processExpiredTest(expiringTest: ExpiringOnlineTest): Future[Unit]
  def emailCandidate(expiringTest: ExpiringOnlineTest, emailAddress: String): Future[Unit]
  def commitExpiredStatus(expiringTest: ExpiringOnlineTest): Future[Unit]
}

class OnlineTestExpiryServiceImpl(
  appRepository: GeneralApplicationRepository,
  otRepository: OnlineTestRepository,
  cdRepository: ContactDetailsRepository,
  emailClient: EmailClient,
  auditService: AuditService,
  newHeaderCarrier: => HeaderCarrier
)(implicit executor: ExecutionContext) extends OnlineTestExpiryService {

  private final val ExpiredStatus = "ONLINE_TEST_EXPIRED"
  private implicit def headerCarrier = newHeaderCarrier

  override def processNextExpiredTest(): Future[Unit] = {
    // TODO FAST STREAM FIX ME
    Future.successful(Unit)
    //otRepository.nextApplicationPendingExpiry.flatMap {
    //  case Some(expiringTest) => processExpiredTest(expiringTest)
    //  case None => Future.successful(())
    //}
  }

  override def processExpiredTest(expiringTest: ExpiringOnlineTest): Future[Unit] =
    for {
      emailAddress <- candidateEmailAddress(expiringTest.userId)
      _ <- emailCandidate(expiringTest, emailAddress)
      _ <- commitExpiredStatus(expiringTest)
    } yield ()

  override def emailCandidate(expiringTest: ExpiringOnlineTest, emailAddress: String): Future[Unit] =
    emailClient.sendOnlineTestExpired(emailAddress, expiringTest.preferredName).map { _ =>
      audit("ExpiredOnlineTestNotificationEmailed", expiringTest, Some(emailAddress))
    }

  override def commitExpiredStatus(expiringTest: ExpiringOnlineTest): Future[Unit] =
    applicationRepository.updateStatus(expiringTest.userId, ExpiredStatus).map { _ =>
      audit("ExpiredOnlineTest", expiringTest)
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
