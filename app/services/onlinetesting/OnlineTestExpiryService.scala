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
import model.persisted.ExpiringOnlineTest
import model.ProgressStatuses.PHASE1_TESTS_EXPIRED
import play.api.Logger
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.Phase1TestRepository
import services.AuditService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

@deprecated("remaining methods to be moved in phase1/2 services")
trait OnlineTestExpiryService {
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