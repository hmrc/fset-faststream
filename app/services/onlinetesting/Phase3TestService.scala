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
import config.LaunchpadGatewayConfig
import connectors.ExchangeObjects._
import connectors.LaunchpadGatewayClient.{ InviteApplicantRequest, InviteApplicantResponse, RegisterApplicantRequest }
import connectors.{ CSREmailClient, EmailClient, LaunchpadGatewayClient }
import factories.{ DateTimeFactory, UUIDFactory }
import model.OnlineTestCommands._
import model.ProgressStatuses
import model.persisted.Phase1TestProfileWithAppId
import model.persisted.phase3tests.{ Phase3Test, Phase3TestGroup }
import org.joda.time.DateTime
import play.api.Logger
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.Phase3TestRepository
import services.events.{ EventService, EventSink }
import services.onlinetesting.Phase3TestService.InviteLinkCouldNotBeCreatedSuccessfully
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global

object Phase3TestService extends Phase3TestService {
  import config.MicroserviceAppConfig._
  val appRepository = applicationRepository
  val p3TestRepository = phase3TestRepository
  val cdRepository = faststreamContactDetailsRepository
  val launchpadGatewayClient = LaunchpadGatewayClient
  val tokenFactory = UUIDFactory
  val dateTimeFactory = DateTimeFactory
  val emailClient = CSREmailClient
  val auditService = AuditService
  val gatewayConfig = launchpadGatewayConfig
  val eventService = EventService

  case class InviteLinkCouldNotBeCreatedSuccessfully(message: String) extends Exception(message)
}

trait Phase3TestService extends OnlineTestService with ResetPhase3Test with EventSink {
  val appRepository: GeneralApplicationRepository
  val p3TestRepository: Phase3TestRepository
  val cdRepository: contactdetails.ContactDetailsMongoRepository
  val launchpadGatewayClient: LaunchpadGatewayClient
  val tokenFactory: UUIDFactory
  val dateTimeFactory: DateTimeFactory
  val emailClient: EmailClient
  val auditService: AuditService
  val gatewayConfig: LaunchpadGatewayConfig

  override def nextApplicationReadyForOnlineTesting: Future[Option[OnlineTestApplication]] = {
    p3TestRepository.nextApplicationReadyForOnlineTesting
  }

  override def registerAndInviteForTestGroup(application: List[OnlineTestApplication])(implicit hc: HeaderCarrier): Future[Unit] = ???

  override def registerAndInviteForTestGroup(application: OnlineTestApplication)(implicit hc: HeaderCarrier): Future[Unit] = {
    registerAndInviteForTestGroup(application, getInterviewIdForApplication(application))
  }

  def registerAndInviteForTestGroup(application: OnlineTestApplication, interviewId: Int)
    (implicit hc: HeaderCarrier): Future[Unit] = {
    val (invitationDate, expirationDate) =
      dateTimeFactory.nowLocalTimeZone -> dateTimeFactory.nowLocalTimeZone.plusDays(gatewayConfig.phase3Tests.timeToExpireInDays)

    for {
      emailAddress <- candidateEmailAddress(application)
      phase3Test <- registerAndInviteApplicant(application, emailAddress, interviewId, invitationDate, expirationDate)
      // _ <- emailInviteToApplicant(application, emailAddress, invitationDate, expirationDate)
      _ <- markAsInvited(application)(Phase3TestGroup(expirationDate = expirationDate, tests = List(phase3Test)))
    } yield audit("Phase3TestInvitationProcessComplete", application.userId)
  }

  private def registerAndInviteApplicant(application: OnlineTestApplication, emailAddress: String, interviewId: Int, invitationDate: DateTime,
    expirationDate: DateTime
  )(implicit hc: HeaderCarrier): Future[Phase3Test] = {
    val customCandidateId = "FSCND-" + tokenFactory.generateUUID()

    for {
      candidateId <- registerApplicant(application, emailAddress, customCandidateId)
      invitation <- inviteApplicant(application, interviewId, candidateId)
    } yield {
      Phase3Test(interviewId = interviewId,
        usedForResults = true,
        testUrl = invitation.link.url,
        token = invitation.custom_invite_id,
        candidateId = candidateId,
        customCandidateId = invitation.custom_candidate_id,
        invitationDate = invitationDate,
        startedDateTime = None,
        completedDateTime = None
      )
    }
  }

  def registerApplicant(application: OnlineTestApplication,
                        emailAddress: String, customCandidateId: String)(implicit hc: HeaderCarrier): Future[String] = {
    val registerApplicant = RegisterApplicantRequest(emailAddress, customCandidateId, application.preferredName, application.lastName)
    print("RA = " + registerApplicant + "\n")
    launchpadGatewayClient.registerApplicant(registerApplicant).map { registration =>
      audit("UserRegisteredForPhase3Test", application.userId)
      print("RCND = " + registration.candidate_id + "\n")
      registration.candidate_id
    }
  }

  private def inviteApplicant(application: OnlineTestApplication, interviewId: Int, candidateId: String)
    (implicit hc: HeaderCarrier): Future[InviteApplicantResponse] = {

    val customInviteId = "FSINV-" + tokenFactory.generateUUID()

    val completionRedirectUrl = s"${gatewayConfig.phase3Tests.candidateCompletionRedirectUrl}/fset-fast-stream/" +
      s"phase3-tests/complete/$customInviteId"

    val inviteApplicant = InviteApplicantRequest(interviewId, candidateId, customInviteId, completionRedirectUrl)
    launchpadGatewayClient.inviteApplicant(inviteApplicant).map { invitation =>
      invitation.link.status match {
        case "success" =>
          audit("UserInvitedToPhase3Test", application.userId)
          invitation
        case _ =>
          throw InviteLinkCouldNotBeCreatedSuccessfully(s"Status of invite for " +
            s"candidate $candidateId to $interviewId was ${invitation.link.status} (Invite ID: $customInviteId)")
      }
    }
  }

  private def emailInviteToApplicant(application: OnlineTestApplication, emailAddress: String,
    invitationDate: DateTime, expirationDate: DateTime
  )(implicit hc: HeaderCarrier): Future[Unit] = {
    val preferredName = application.preferredName
    emailClient.sendOnlineTestInvitation(emailAddress, preferredName, expirationDate).map { _ =>
      audit("Phase3TestInvitationEmailSent", application.userId, Some(emailAddress))
    }
  }

  private def merge(currentTestGroup: Option[Phase3TestGroup],
                    newTestGroup: Phase3TestGroup): Phase3TestGroup = currentTestGroup match {
    case None =>
      newTestGroup
    case Some(profile) =>
      val interviewIdsToArchive = newTestGroup.tests.map(_.interviewId)
      val existingTestsAfterUpdate = profile.tests.map { t =>
        if (interviewIdsToArchive.contains(t.interviewId)) {
          t.copy(usedForResults = false)
        } else {
          t
        }
      }
      Phase3TestGroup(newTestGroup.expirationDate, existingTestsAfterUpdate ++ newTestGroup.tests)
  }

  private def markAsInvited(application: OnlineTestApplication)
                           (newPhase3TestGroup: Phase3TestGroup): Future[Unit] = {
    Logger.debug("====== In Invited!")
    for {
      currentPhase3TestGroup <- p3TestRepository.getTestGroup(application.applicationId)
      updatedPhase3TestGroup = merge(currentPhase3TestGroup, newPhase3TestGroup)
      _ <- p3TestRepository.insertOrUpdateTestGroup(application.applicationId, updatedPhase3TestGroup)
      _ <- appRepository.removeProgressStatuses(application.applicationId, determineStatusesToRemove(updatedPhase3TestGroup))
    } yield {
      Logger.debug("====== Wrote PHASE 3 " + updatedPhase3TestGroup)
      audit("Phase3TestInvited", application.userId)
    }
  }

  private def candidateEmailAddress(application: OnlineTestApplication): Future[String] =
    cdRepository.find(application.userId).map(_.email)

  // TODO: This needs to cater for 10% extra, 33% extra etc
  private def getInterviewIdForApplication(application: OnlineTestApplication): Int = {
      gatewayConfig.phase3Tests.mainInterviewId
  }
}

trait ResetPhase3Test {
  import ProgressStatuses._

  // TODO: Implement
  def determineStatusesToRemove(testGroup: Phase3TestGroup): List[ProgressStatus] = {
    /*(if (testGroup.hasNotStartedYet) List(PHASE1_TESTS_STARTED) else List()) ++
      (if (testGroup.hasNotCompletedYet) List(PHASE1_TESTS_COMPLETED) else List()) ++
      (if (testGroup.hasNotResultReadyToDownloadForAllTestsYet) List(PHASE1_TESTS_RESULTS_RECEIVED) else List())
      */
      List(PHASE1_TESTS_INVITED)
    }
}