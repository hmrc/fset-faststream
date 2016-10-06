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

import _root_.services.AuditService
import akka.actor.ActorSystem
import config.CubiksGatewayConfig
import connectors.ExchangeObjects._
import connectors.{ CSREmailClient, CubiksGatewayClient, EmailClient }
import factories.{ DateTimeFactory, UUIDFactory }
import model.OnlineTestCommands._
import org.joda.time.DateTime
import play.api.Logger
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.Phase2TestRepository
import services.events.{ EventService, EventSink }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps

object Phase2TestService extends Phase2TestService {
  import config.MicroserviceAppConfig._
  val appRepository = applicationRepository
  val cdRepository = contactDetailsRepository
  val phase2TestRepo = phase2TestRepository
  val cubiksGatewayClient = CubiksGatewayClient
  val tokenFactory = UUIDFactory
  val dateTimeFactory = DateTimeFactory
  val emailClient = CSREmailClient
  val auditService = AuditService
  val gatewayConfig = cubiksGatewayConfig
  val actor = ActorSystem()
  val eventService = EventService

}

trait Phase2TestService extends OnlineTestService {
  val actor: ActorSystem

  val appRepository: GeneralApplicationRepository
  val cdRepository: ContactDetailsRepository
  val phase2TestRepo: Phase2TestRepository
  val cubiksGatewayClient: CubiksGatewayClient
  val gatewayConfig: CubiksGatewayConfig

  override def nextApplicationReadyForOnlineTesting: Future[Option[OnlineTestApplication]] = {
    phase2TestRepo.nextApplicationReadyForOnlineTesting
  }

  override def registerAndInviteForTestGroup(application: OnlineTestApplication)(implicit hc: HeaderCarrier): Future[Unit] = {
    registerAndInviteForTestGroup(application)
  }


  //private def registerAndInviteApplicant(application: OnlineTestApplication, scheduleId: Int, invitationDate: DateTime,
  //  expirationDate: DateTime
  //)(implicit hc: HeaderCarrier): Future[CubiksTest] = {
  //  val authToken = tokenFactory.generateUUID()

  //  for {
  //    userId <- registerApplicant(application, authToken)
  //    invitation <- inviteApplicant(application, authToken, userId, scheduleId)
  //    _ <- trRepository.remove(application.applicationId)
  //  } yield {
  //    CubiksTest(scheduleId = scheduleId,
  //      usedForResults = true,
  //      cubiksUserId = invitation.userId,
  //      token = authToken,
  //      invitationDate = invitationDate,
  //      participantScheduleId = invitation.participantScheduleId,
  //      testUrl = invitation.authenticateUrl
  //    )
  //  }
  //}

  def registerApplicant(application: OnlineTestApplication, token: String)(implicit hc: HeaderCarrier): Future[Int] = {
    val preferredName = CubiksSanitizer.sanitizeFreeText(application.preferredName)
    val registerApplicant = RegisterApplicant(preferredName, "", token + "@" + gatewayConfig.emailDomain)
    cubiksGatewayClient.registerApplicant(registerApplicant).map { registration =>
      audit("UserRegisteredForOnlineTest", application.userId)
      registration.userId
    }
  }

  //private def inviteApplicant(application: OnlineTestApplication, authToken: String, userId: Int, scheduleId: Int)
  //  (implicit hc: HeaderCarrier): Future[Invitation] = {

  //  val inviteApplicant = buildInviteApplication(application, authToken, userId, scheduleId)
  //  cubiksGatewayClient.inviteApplicant(inviteApplicant).map { invitation =>
  //    audit("UserInvitedToOnlineTest", application.userId)
  //    invitation
  //  }
  //}

  //private def markAsInvited(application: OnlineTestApplication)
  //                         (newOnlineTestProfile: Phase1TestProfile): Future[Unit] = for {
  //  currentOnlineTestProfile <- phase1TestRepo.getTestGroup(application.applicationId)
  //  updatedOnlineTestProfile = merge(currentOnlineTestProfile, newOnlineTestProfile)
  //  _ <- phase1TestRepo.insertOrUpdateTestGroup(application.applicationId, updatedOnlineTestProfile)
  //  _ <- phase1TestRepo.removeTestProfileProgresses(application.applicationId, determineStatusesToRemove(updatedOnlineTestProfile))
  //} yield {
  //  audit("OnlineTestInvited", application.userId)
  //}

  private def candidateEmailAddress(application: OnlineTestApplication): Future[String] =
    cdRepository.find(application.userId).map(_.email)

  private def calcOnlineTestDates: (DateTime, DateTime) = {
    val invitationDate = dateTimeFactory.nowLocalTimeZone
    val expirationDate = invitationDate.plusDays(gatewayConfig.phase1Tests.expiryTimeInDays)
    (invitationDate, expirationDate)
  }



  private[services] def getAdjustedTime(minimum: Int, maximum: Int, percentageToIncrease: Int) = {
    val adjustedValue = math.ceil(minimum.toDouble * (1 + percentageToIncrease / 100.0))
    math.min(adjustedValue, maximum).toInt
  }

  //private[services] def buildInviteApplication(application: OnlineTestApplication, token: String, userId: Int, scheduleId: Int) = {
  //  val scheduleCompletionBaseUrl = s"${gatewayConfig.candidateAppUrl}/fset-fast-stream/online-tests/phase1"
  //  if (application.guaranteedInterview) {
  //    InviteApplicant(scheduleId,
  //      userId,
  //      s"$scheduleCompletionBaseUrl/complete/$token",
  //      resultsURL = None
  //    )
  //  } else {
  //    val scheduleCompletionUrl = if (scheduleIdByName("sjq") == scheduleId) {
  //      s"$scheduleCompletionBaseUrl/continue/$token"
  //    } else {
  //      s"$scheduleCompletionBaseUrl/complete/$token"
  //    }

  //    InviteApplicant(scheduleId, userId, scheduleCompletionUrl, resultsURL = None)
  //  }
  //}
}
