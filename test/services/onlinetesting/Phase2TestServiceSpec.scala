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

import config._
import connectors.ExchangeObjects.{Invitation, InviteApplicant, Registration}
import connectors.{CSREmailClient, CubiksGatewayClient}
import factories.{DateTimeFactory, UUIDFactory}
import model.{Address, ApplicationStatus}
import model.OnlineTestCommands.OnlineTestApplication
import model.PersistedObjects.ContactDetails
import model.persisted.Phase2TestGroup
import org.joda.time.DateTime
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import repositories.ContactDetailsRepository
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.Phase2TestRepository
import services.AuditService
import services.events.{EventService, EventServiceFixture}
import testkit.ExtendedTimeout
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class Phase2TestServiceSpec extends PlaySpec with MockitoSugar with ScalaFutures with ExtendedTimeout {

  "Register applicants" should {
    "correctly register a batch of candidates" in new Phase2TestServiceFixture {

      val result = phase2TestService.registerApplicants(candidates, tokens).futureValue
      result.size mustBe 2
      val head = result.values.head
      val last = result.values.last
      head._1 mustBe onlineTestApplication
      head._2 mustBe tokens.head
      head._3 mustBe registrations.head
      last._1 mustBe onlineTestApplication2
      last._2 mustBe tokens.last
      last._3 mustBe registrations.last

      verify(auditServiceMock, times(2)).logEventNoRequest(eqTo("Phase2TestRegistered"), any[Map[String, String]])
    }
  }

  "Invite applicants" must {
    "correctly invite a batch of candidates" in new Phase2TestServiceFixture {
      val result = phase2TestService.inviteApplicants(registeredMap).futureValue

      result mustBe List(phase2TestService.Phase2TestInviteData(onlineTestApplication, tokens.head,
        registrations.head, invites.head),
        phase2TestService.Phase2TestInviteData(onlineTestApplication2, tokens.last,
          registrations.last, invites.last)
      )

      verify(auditServiceMock, times(2)).logEventNoRequest(eqTo("Phase2TestInvited"), any[Map[String, String]])
    }
  }

  "Register and Invite applicants" must {
    "email the candidate and send audit events" in new Phase2TestServiceFixture {
      phase2TestService.registerAndInviteForTestGroup(candidates).futureValue

      verify(auditServiceMock, times(2)).logEventNoRequest(eqTo("Phase2TestRegistered"), any[Map[String, String]])
      verify(auditServiceMock, times(2)).logEventNoRequest(eqTo("Phase2TestInvited"), any[Map[String, String]])
      verify(auditServiceMock, times(2)).logEventNoRequest(eqTo("Phase2TestInvitationProcessComplete"), any[Map[String, String]])
      verify(otRepositoryMock, times(2)).insertOrUpdateTestGroup(any[String], any[Phase2TestGroup])
    }

    "process adjustment candidates first and individually" in new Phase2TestServiceFixture {
     val adjustmentCandidates = candidates :+ adjustmentApplication :+ adjustmentApplication2

      when(cubiksGatewayClientMock.registerApplicants(any[Int])(any[HeaderCarrier]))
        .thenReturn(Future.successful(List(registrations.head)))

      when(cubiksGatewayClientMock.inviteApplicants(any[List[InviteApplicant]])(any[HeaderCarrier]))
        .thenReturn(Future.successful(List(invites.head)))

      phase2TestService.registerAndInviteForTestGroup(adjustmentCandidates).futureValue

      verify(auditServiceMock).logEventNoRequest(eqTo("Phase2TestRegistered"), any[Map[String, String]])
      verify(auditServiceMock).logEventNoRequest(eqTo("Phase2TestInvited"), any[Map[String, String]])
      verify(auditServiceMock).logEventNoRequest(eqTo("Phase2TestInvitationProcessComplete"), any[Map[String, String]])
      verify(otRepositoryMock).insertOrUpdateTestGroup(any[String], any[Phase2TestGroup])
    }
  }

  trait Phase2TestServiceFixture {

    implicit val hc = mock[HeaderCarrier]

    val gatewayConfigMock =  CubiksGatewayConfig(
      "",
      Phase1TestsConfig(expiryTimeInDays = 7,
        scheduleIds = Map("sjq" -> 16196, "bq" -> 16194),
        List("sjq", "bq"),
        List("sjq")
      ),
      competenceAssessment = CubiksGatewayStandardAssessment(31, 32),
      situationalAssessment = CubiksGatewayStandardAssessment(41, 42),
      phase2Tests = Phase2TestsConfig(expiryTimeInDays = 7, scheduleName = "e-tray", scheduleId = 123, assessmentId = 158),
      reportConfig = ReportConfig(1, 2, "en-GB"),
      candidateAppUrl = "http://localhost:9284",
      emailDomain = "test.com"
    )

    val appRepositoryMock = mock[GeneralApplicationRepository]
    val cdRepositoryMock = mock[ContactDetailsRepository]
    val otRepositoryMock = mock[Phase2TestRepository]
    val cubiksGatewayClientMock = mock[CubiksGatewayClient]
    val emailClientMock = mock[CSREmailClient]
    var auditServiceMock = mock[AuditService]
    val tokenFactoryMock = UUIDFactory
    val eventServiceMock = mock[EventService]
    val tokens = UUIDFactory.generateUUID :: UUIDFactory.generateUUID :: Nil
    val registrations = Registration(123) :: Registration(456) :: Nil
    val onlineTestApplication = OnlineTestApplication(applicationId = "appId",
      applicationStatus = ApplicationStatus.SUBMITTED,
      userId = "userId",
      guaranteedInterview = false,
      needsAdjustments = false,
      preferredName = "Optimus Prime",
      timeAdjustments = None
    )

    val onlineTestApplication2 = onlineTestApplication.copy(applicationId = "appId2", userId = "userId2")
    val adjustmentApplication = onlineTestApplication.copy(applicationId = "appId3", userId = "userId3", needsAdjustments = true)
    val adjustmentApplication2 = onlineTestApplication.copy(applicationId = "appId4", userId = "userId4", needsAdjustments = true)
    val candidates = List(onlineTestApplication, onlineTestApplication2)

    val registeredMap = Map(
      registrations.head.userId -> (onlineTestApplication, tokens.head, registrations.head),
      registrations.last.userId -> (onlineTestApplication2, tokens.last, registrations.last)
    )

    val invites = List(Invitation(userId = registrations.head.userId, email = "email@test.com", accessCode = "accessCode",
       logonUrl = "logon.com", authenticateUrl = "authenticated", participantScheduleId = 999
      ),
      Invitation(userId = registrations.last.userId, email = "email@test.com", accessCode = "accessCode", logonUrl = "logon.com",
        authenticateUrl = "authenticated", participantScheduleId = 888
    ))

    when(cubiksGatewayClientMock.registerApplicants(any[Int])(any[HeaderCarrier]))
      .thenReturn(Future.successful(registrations))

    when(cubiksGatewayClientMock.inviteApplicants(any[List[InviteApplicant]])(any[HeaderCarrier]))
      .thenReturn(Future.successful(invites))

    when(otRepositoryMock.insertOrUpdateTestGroup(any[String], any[Phase2TestGroup]))
        .thenReturn(Future.successful(()))

    when(cdRepositoryMock.find(any[String])).thenReturn(Future.successful(ContactDetails(
      Address("Aldwych road"), "QQ1 1QQ", "email@test.com", Some("111111")
    )))

    when(emailClientMock.sendOnlineTestInvitation(any[String], any[String], any[DateTime])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))

    val phase2TestService = new Phase2TestService with EventServiceFixture {
      val appRepository = appRepositoryMock
      val cdRepository = cdRepositoryMock
      val phase2TestRepo = otRepositoryMock
      val cubiksGatewayClient = cubiksGatewayClientMock
      val emailClient = emailClientMock
      val auditService = auditServiceMock
      val tokenFactory = tokenFactoryMock
      val dateTimeFactory = DateTimeFactory
      val gatewayConfig = gatewayConfigMock
    }
  }
}
