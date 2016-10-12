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
import connectors.ExchangeObjects.{ Invitation, InviteApplicant, Registration }
import connectors.LaunchpadGatewayClient.{ InviteApplicantResponse, RegisterApplicantResponse, SeamlessLoginLink }
import connectors.{ CSREmailClient, CubiksGatewayClient, LaunchpadGatewayClient }
import factories.{ DateTimeFactory, UUIDFactory }
import model.OnlineTestCommands.OnlineTestApplication
import model.persisted.ContactDetails
import model.persisted.phase3tests.{ Phase3Test, Phase3TestGroup }
import model.{ Address, ApplicationStatus }
import org.joda.time.DateTime
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.onlinetesting.Phase3TestRepository
import services.AuditService
import services.events.{ EventService, EventServiceFixture }
import testkit.ExtendedTimeout
import uk.gov.hmrc.play.http.{ HeaderCarrier, NotImplementedException }

import scala.concurrent.Future
import scala.util.Failure

class Phase3TestServiceSpec extends PlaySpec with MockitoSugar with ScalaFutures with ExtendedTimeout {

  "Register and invite for multiple applicants (batch invite)" should {
    "throw a not implemented error" in new Phase3TestServiceFixture {
      val ex = phase3TestService.registerAndInviteForTestGroup(List()).failed.futureValue
      ex.getCause mustBe a[NotImplementedError]
    }
  }

  "Register and Invite an applicant" should {
    "email the candidate and send audit events" in new Phase3TestServiceFixture {
      phase3TestService.registerAndInviteForTestGroup(onlineTestApplication, testInterviewId).futureValue

      verify(auditServiceMock, times(1)).logEventNoRequest(eqTo("Phase3UserRegistered"), any[Map[String, String]])
      verify(auditServiceMock, times(1)).logEventNoRequest(eqTo("Phase3TestInvited"), any[Map[String, String]])
      verify(auditServiceMock, times(1)).logEventNoRequest(eqTo("Phase3TestInvitationProcessComplete"), any[Map[String, String]])
    }
  }

  trait Phase3TestServiceFixture {

    implicit val hc = mock[HeaderCarrier]

    val gatewayConfigMock =  LaunchpadGatewayConfig(
      "localhost",
      Phase3TestsConfig(timeToExpireInDays = 7,
        candidateCompletionRedirectUrl = "test.com",
        1,
        2
      )
    )

    val appRepositoryMock = mock[GeneralApplicationRepository]
    val cdRepositoryMock = mock[ContactDetailsRepository]
    val p3TestRepositoryMock = mock[Phase3TestRepository]
    val launchpadGatewayClientMock = mock[LaunchpadGatewayClient]
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
      preferredName = "Optimus",
      lastName = "Prime",
      timeAdjustments = None
    )
    val onlineTestApplication2 = onlineTestApplication.copy(applicationId = "appId2", userId = "userId2")

    val testInterviewId = 123

    when(cdRepositoryMock.find(any())).thenReturn {
      Future.successful(ContactDetails(
        outsideUk = false,
        Address("123, Fake Street"),
        Some("AB1 2CD"),
        None,
        "foo@bar.com",
        "12345678"
      ))
    }

    when(launchpadGatewayClientMock.registerApplicant(any())(any[HeaderCarrier]())).thenReturn {
      Future.successful(RegisterApplicantResponse(
        "CND_123",
        "FSCND_456"
      ))
    }

    when(launchpadGatewayClientMock.inviteApplicant(any())(any[HeaderCarrier]())).thenReturn {
      Future.successful(InviteApplicantResponse(
        "INV_123",
        "CND_123",
        "FSCND_456",
        SeamlessLoginLink("http://www.foo.com", "success", "registered successfully"),
        "Tomorrow"
      ))
    }

    when(p3TestRepositoryMock.getTestGroup(any())).thenReturn(Future.successful(None))

    when(p3TestRepositoryMock.insertOrUpdateTestGroup(any(), any())).thenReturn(Future.successful(()))

    when(appRepositoryMock.removeProgressStatuses(any(), any())).thenReturn(Future.successful(()))

    /*
    Some(Phase3TestGroup(
          DateTime.parse("2030-10-15 00:00:01"),
          List(
            Phase3Test(
              123,
              true,
              "launchpad",
              "test.com",
              "abc123",
              "CND_123",
              "FSCND_456",
              DateTime.parse("2016-10-01 00:00:01"),
              None,
              None
            )
          )
        )
     */

    val phase3TestService = new Phase3TestService with EventServiceFixture {
      val appRepository = appRepositoryMock
      val p3TestRepository = p3TestRepositoryMock
      val cdRepository = cdRepositoryMock
      val launchpadGatewayClient = launchpadGatewayClientMock
      val tokenFactory = tokenFactoryMock
      val dateTimeFactory = DateTimeFactory
      val emailClient = emailClientMock
      val auditService = auditServiceMock
      val gatewayConfig = gatewayConfigMock
    }
  }
}
