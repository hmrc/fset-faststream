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
import config.{CubiksGatewayConfig, Phase2TestsConfig}
import connectors.ExchangeObjects._
import connectors.{CSREmailClient, CubiksGatewayClient, EmailClient}
import factories.{DateTimeFactory, UUIDFactory}
import model.OnlineTestCommands._
import model.persisted.{CubiksTest, Phase2TestGroup, Phase2TestGroupWithAppId}
import org.joda.time.DateTime
import play.api.Logger
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.Phase2TestRepository
import services.events.{EventService, EventSink}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
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

  val (invitationDate, expirationDate) = calcOnlineTestDates(gatewayConfig.phase2Tests.expiryTimeInDays)

  def testConfig: Phase2TestsConfig = gatewayConfig.phase2Tests

  case class Phase2TestRegistrationAndInvitation(application: OnlineTestApplication,
    registration: Registration,
    invitation: Invitation,
    token: String
  )

  override def nextApplicationReadyForOnlineTesting: Future[Option[OnlineTestApplication]] = {
    phase2TestRepo.nextApplicationReadyForOnlineTesting
  }

  override def registerAndInviteForTestGroup(application: OnlineTestApplication)
    (implicit hc: HeaderCarrier): Future[Unit] = {
    registerAndInviteForTestGroup(List(application))
  }

  override def registerAndInviteForTestGroup(applications: List[OnlineTestApplication])
    (implicit hc: HeaderCarrier): Future[Unit] = {

    val candidatesToProcess = filterCandidates(applications)
    val registrations = cubiksGatewayClient.registerApplicants(candidatesToProcess.size)
    val tokens = for (i <- 1 to candidatesToProcess.size) yield tokenFactory.generateUUID()

    val invitationProcess = (registrations: Future[List[Registration]]) => registrations.flatMap { eventualRegistrations =>
      val invites = eventualRegistrations.zipWithIndex.map {  case (registration, idx) =>
        buildInviteApplication(candidatesToProcess(idx), tokens(idx), registration.userId, testConfig.scheduleId)
      }

      require(eventualRegistrations.size == invites.size == tokens.size,
        s"Size mismatch between generated tokens, invitations and completed registrations")

      cubiksGatewayClient.inviteApplicants(invites)
    }

    for {
      invitedApplicants <- invitationProcess(registrations)
      phase2TestGroupsWithAppId = invitedApplicants.zipWithIndex.map { case (invite, idx) =>
        buildPhase2TestGroupWithAppId(candidatesToProcess(idx), tokens(idx), invite) }
      _ <- insertPhase2TestGroups(phase2TestGroupsWithAppId)
      _ <- emailInviteToApplicants(candidatesToProcess)
    } yield {
     ()
    }
  }

  def buildPhase2TestGroupWithAppId(application: OnlineTestApplication, token: String, invite: Invitation): Phase2TestGroupWithAppId = {
    Phase2TestGroupWithAppId(application.applicationId,
      Phase2TestGroup(expirationDate = expirationDate,
        tests = List(CubiksTest(scheduleId = testConfig.scheduleId,
          usedForResults = true,
          cubiksUserId = invite.userId,
          testProvider = "cubiks",
          token = token,
          testUrl = "",
          invitationDate = invitationDate,
          participantScheduleId = invite.participantScheduleId
        ))
      )
    )
  }

  def buildInviteApplication(application: OnlineTestApplication, token: String, userId: Int, scheduleId: Int) = {
    val scheduleCompletionBaseUrl = s"${gatewayConfig.candidateAppUrl}/fset-fast-stream/online-tests/phase2"

    InviteApplicant(scheduleId,
      userId,
      s"$scheduleCompletionBaseUrl/complete/$token",
      resultsURL = None,
      timeAdjustments = buildTimeAdjustments(application.needsAdjustments)
    )
  }

  def insertPhase2TestGroups(groups: List[Phase2TestGroupWithAppId]): Future[Unit] = Future.sequence(
    groups.map( o => phase2TestRepo.insertOrUpdateTestGroup(o.applicationId, o.phase2TestGroup))
  ).map( _ => () )

  //TODO Once the time adjustments ticket has been done then this should be updated to apply the etray adjustment settings.
  def buildTimeAdjustments(needsAdjustment: Boolean) = if (needsAdjustment) {
    Some(List(TimeAdjustments(testConfig.assessmentId, sectionId = 1, absoluteTime = 100)))
  } else {
    None
  }

  private def filterCandidates(candidates: List[OnlineTestApplication]): List[OnlineTestApplication] =
    candidates.find(_.needsAdjustments) match {
      case Some(candidate) => List(candidate)
      case None => candidates
  }

  def emailInviteToApplicants(candidates: List[OnlineTestApplication])
    (implicit hc: HeaderCarrier): Future[Unit] = Future.sequence(candidates.map { candidate =>
    candidateEmailAddress(candidate.userId).flatMap(emailInviteToApplicant(candidate, _ , invitationDate, expirationDate))
  }).map( _ => () )


  private def candidateEmailAddress(userId: String): Future[String] = cdRepository.find(userId).map(_.email)

}
