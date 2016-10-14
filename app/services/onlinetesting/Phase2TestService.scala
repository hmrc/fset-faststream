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
import config.{ CubiksGatewayConfig, Phase2TestsConfig }
import connectors.ExchangeObjects._
import connectors.{ CubiksGatewayClient, Phase2OnlineTestEmailClient }
import factories.{ DateTimeFactory, UUIDFactory }
import model.OnlineTestCommands._
import model.ReminderNotice
import model.exchange.Phase2TestGroupWithNames
import model.persisted.{ CubiksTest, NotificationExpiringOnlineTest, Phase2TestGroup }
import org.joda.time.DateTime
import repositories._
import repositories.onlinetesting.Phase2TestRepository
import services.events.EventService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.language.postfixOps

object Phase2TestService extends Phase2TestService {
  import config.MicroserviceAppConfig._
  val appRepository = applicationRepository
  val cdRepository = faststreamContactDetailsRepository
  val phase2TestRepo = phase2TestRepository
  val cubiksGatewayClient = CubiksGatewayClient
  val tokenFactory = UUIDFactory
  val dateTimeFactory = DateTimeFactory
  val emailClient = Phase2OnlineTestEmailClient
  val auditService = AuditService
  val gatewayConfig = cubiksGatewayConfig
  val actor = ActorSystem()
  val eventService = EventService

}

trait Phase2TestService extends OnlineTestService {

  val phase2TestRepo: Phase2TestRepository
  val cubiksGatewayClient: CubiksGatewayClient
  val gatewayConfig: CubiksGatewayConfig


  def testConfig: Phase2TestsConfig = gatewayConfig.phase2Tests

  case class Phase2TestInviteData(application: OnlineTestApplication,
    token: String,
    registration: Registration,
    invitation: Invitation
  )

  def getTestProfile(applicationId: String): Future[Option[Phase2TestGroupWithNames]] = {
    for {
      phase2Opt <- phase2TestRepo.getTestGroup(applicationId)
    } yield phase2Opt.map { phase2 =>
        val tests = phase2.activeTests
        Phase2TestGroupWithNames(
          phase2.expirationDate,
          tests
        )
    }
  }

  override def nextApplicationReadyForOnlineTesting: Future[List[OnlineTestApplication]] = {
    phase2TestRepo.nextApplicationsReadyForOnlineTesting
  }

  override def processNextTestForReminder(reminder: ReminderNotice)(implicit hc: HeaderCarrier): Future[Unit] = {
    phase2TestRepo.nextTestForReminder(reminder).flatMap {
      case Some(expiringTest) => processReminder(expiringTest, reminder)
      case None => Future.successful(())
    }
  }

  override def emailCandidateForExpiringTestReminder(expiringTest: NotificationExpiringOnlineTest,
                                                     emailAddress: String,
                                                     reminder: ReminderNotice)(implicit hc: HeaderCarrier): Future[Unit] = {
    emailClient.sendTestExpiringReminder(emailAddress, expiringTest.preferredName,
      reminder.hoursBeforeReminder, reminder.timeUnit, expiringTest.expiryDate).map { _ =>
      audit(s"ReminderPhase2ExpiringOnlineTestNotificationBefore${reminder.hoursBeforeReminder}HoursEmailed",
        expiringTest.userId, Some(emailAddress))
    }
  }

  override def registerAndInviteForTestGroup(application: OnlineTestApplication)
    (implicit hc: HeaderCarrier): Future[Unit] = {
    registerAndInviteForTestGroup(List(application))
  }

  def registerApplicants(candidates: List[OnlineTestApplication], tokens: Seq[String])
    (implicit hc: HeaderCarrier): Future[Map[Int, (OnlineTestApplication, String, Registration)]] = {
    cubiksGatewayClient.registerApplicants(candidates.size).map( _.zipWithIndex.map { case (registration, idx) =>
      val candidate = candidates(idx)
      audit("Phase2TestRegistered", candidate.userId)
      (registration.userId, (candidate, tokens(idx), registration))
    }.toMap)
  }

  def inviteApplicants(candidateData: Map[Int, (OnlineTestApplication, String, Registration)])
    (implicit hc: HeaderCarrier): Future[List[Phase2TestInviteData]] = {
    val invites = candidateData.values.map { case (application, token, registration) =>
      buildInviteApplication(application, token, registration.userId, testConfig.scheduleId)
    }.toList

    cubiksGatewayClient.inviteApplicants(invites).map(_.map { invitation =>
      val (application, token, registration) = candidateData(invitation.userId)
      audit("Phase2TestInvited", application.userId)
      Phase2TestInviteData(application, token, registration, invitation)
    })
  }

  override def registerAndInviteForTestGroup(applications: List[OnlineTestApplication])
    (implicit hc: HeaderCarrier): Future[Unit] = {

    val candidatesToProcess = filterCandidates(applications)
    val tokens = for (i <- 1 to candidatesToProcess.size) yield tokenFactory.generateUUID()
    implicit val (invitationDate, expirationDate) = calcOnlineTestDates(gatewayConfig.phase2Tests.expiryTimeInDays)

    for {
      registeredApplicants <- registerApplicants(candidatesToProcess, tokens)
      invitedApplicants <- inviteApplicants(registeredApplicants)
      _ <- insertPhase2TestGroups(invitedApplicants)(invitationDate, expirationDate)
      _ <- emailInviteToApplicants(candidatesToProcess)(hc, invitationDate, expirationDate)
    } yield candidatesToProcess.foreach { candidate =>
      audit("Phase2TestInvitationProcessComplete", candidate.userId)
    }
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

  def insertPhase2TestGroups(o: List[Phase2TestInviteData])
    (implicit invitationDate: DateTime, expirationDate: DateTime): Future[Unit] = Future.sequence(o.map { completedInvite =>
    val testGroup = Phase2TestGroup(expirationDate = expirationDate,
      List(CubiksTest(scheduleId = testConfig.scheduleId,
        usedForResults = true,
        cubiksUserId = completedInvite.registration.userId,
        token = completedInvite.token,
        testUrl = completedInvite.invitation.authenticateUrl,
        invitationDate = invitationDate,
        participantScheduleId = completedInvite.invitation.participantScheduleId
      ))
    )

    phase2TestRepo.insertOrUpdateTestGroup(completedInvite.application.applicationId, testGroup)
  }).map( _ => () )

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
    (implicit hc: HeaderCarrier, invitationDate: DateTime, expirationDate: DateTime): Future[Unit] =
  Future.sequence(candidates.map { candidate =>
    candidateEmailAddress(candidate.userId).flatMap(emailInviteToApplicant(candidate, _ , invitationDate, expirationDate))
  }).map( _ => () )


  private def candidateEmailAddress(userId: String): Future[String] = cdRepository.find(userId).map(_.email)

}
