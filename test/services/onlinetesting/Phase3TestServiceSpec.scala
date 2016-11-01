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
import connectors.launchpadgateway.LaunchpadGatewayClient
import connectors.launchpadgateway.exchangeobjects._
import connectors.CSREmailClient
import connectors.launchpadgateway.exchangeobjects.out.{ InviteApplicantRequest, InviteApplicantResponse, RegisterApplicantRequest, RegisterApplicantResponse }
import factories.{ DateTimeFactory, UUIDFactory }
import model.OnlineTestCommands.OnlineTestApplication
import model.events.{ AuditEvents, DataStoreEvents }
import model.events.AuditEvents.VideoInterviewRegistrationAndInviteComplete
import model.persisted.ContactDetails
import model.persisted.phase3tests.{ LaunchpadTest, Phase3TestGroup }
import model.{ Address, ApplicationStatus }
import org.joda.time.DateTime
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.onlinetesting.Phase3TestRepository
import services.AuditService
import services.events.{ EventService, EventServiceFixture }
import testkit.ExtendedTimeout
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class Phase3TestServiceSpec extends PlaySpec with MockitoSugar with ScalaFutures with ExtendedTimeout {

  "Register and invite for multiple applicants (batch invite)" should {
    "throw a not implemented error" in new Phase3TestServiceFixture {
      val ex = phase3TestService.registerAndInviteForTestGroup(List()).failed.futureValue
      ex.getCause mustBe a[NotImplementedError]
    }
  }

  "Register and Invite an applicant" should {
    "send audit events" in new Phase3TestServiceFixture {
      phase3TestService.registerAndInviteForTestGroup(onlineTestApplication, testInterviewId).futureValue

      verifyDataStoreEvents(3,
        List("VideoInterviewCandidateRegistered",
          "VideoInterviewInvited",
          "VideoInterviewRegistrationAndInviteComplete")
      )

      verifyAuditEvents(3,
        List("VideoInterviewCandidateRegistered",
          "VideoInterviewInvited",
          "VideoInterviewRegistrationAndInviteComplete")
      )
    }

    "insert a valid test group" in new Phase3TestServiceFixture {
      phase3TestService.registerAndInviteForTestGroup(onlineTestApplication, testInterviewId).futureValue

      verify(p3TestRepositoryMock).insertOrUpdateTestGroup(eqTo(onlineTestApplication.applicationId), eqTo(Phase3TestGroup(
        testExpiryTime,
        List(
          testPhase3Test
        ),
        None
      )))
    }

    "call the register and invite methods of the launchpad gateway only once and with the correct arguments" in new Phase3TestServiceFixture {
      phase3TestService.registerAndInviteForTestGroup(onlineTestApplication, testInterviewId).futureValue

      verify(launchpadGatewayClientMock).registerApplicant(eqTo(
        RegisterApplicantRequest(
          testEmail,
          "FSCND-" + tokens.head,
          testFirstName,
          testLastName
        )
      ))

      val expectedCustomInviteId = "FSINV-" + tokens.head
      verify(launchpadGatewayClientMock).inviteApplicant(eqTo(
        InviteApplicantRequest(
          testInterviewId,
          testLaunchpadCandidateId,
          expectedCustomInviteId,
          s"http://www.foo.com/test/interview/fset-fast-stream/phase3-tests/complete/$expectedCustomInviteId"
        )
      ))
    }
  }

  trait Phase3TestServiceFixture extends EventServiceFixture {

    implicit val hc = mock[HeaderCarrier]
    implicit val rh = mock[RequestHeader]

    val appRepositoryMock = mock[GeneralApplicationRepository]
    val cdRepositoryMock = mock[ContactDetailsRepository]
    val p3TestRepositoryMock = mock[Phase3TestRepository]
    val launchpadGatewayClientMock = mock[LaunchpadGatewayClient]
    val emailClientMock = mock[CSREmailClient]
    var auditServiceMock = mock[AuditService]
    val tokenFactoryMock = mock[UUIDFactory]
    val dateTimeFactoryMock = mock[DateTimeFactory]
    val tokens = UUIDFactory.generateUUID() :: Nil

    val testFirstName = "Optimus"
    val testLastName = "Prime"

    val onlineTestApplication = OnlineTestApplication(applicationId = "appId",
      applicationStatus = ApplicationStatus.SUBMITTED,
      userId = "userId",
      guaranteedInterview = false,
      needsAdjustments = false,
      preferredName = testFirstName,
      lastName = testLastName,
      timeAdjustments = None
    )
    val onlineTestApplication2 = onlineTestApplication.copy(applicationId = "appId2", userId = "userId2")

    val testInterviewId = 123
    val testTimeNow = DateTime.parse("2016-10-01T00:00:01Z")
    val testExpiryTime = testTimeNow.plusDays(7)
    val testLaunchpadCandidateId = "CND_123"
    val testFaststreamCustomCandidateId = "FSCND_456"
    val testInviteId = "INV_123"
    val testCandidateRedirectUrl = "http://www.foo.com/test/interview"
    val testEmail = "foo@bar.com"

    val gatewayConfigMock = LaunchpadGatewayConfig(
      "localhost",
      Phase3TestsConfig(timeToExpireInDays = 7,
        candidateCompletionRedirectUrl = testCandidateRedirectUrl,
        Map(
          "0%" -> 12345
        )
      )
    )

    val testPhase3Test = LaunchpadTest(
      testInterviewId,
      usedForResults = true,
      "launchpad",
      testCandidateRedirectUrl,
      testInviteId,
      testLaunchpadCandidateId,
      testFaststreamCustomCandidateId,
      testTimeNow,
      None,
      None
    )

    when(tokenFactoryMock.generateUUID()).thenReturn(tokens.head.toString)

    when(dateTimeFactoryMock.nowLocalTimeZone).thenReturn(testTimeNow)

    when(cdRepositoryMock.find(any())).thenReturn {
      Future.successful(ContactDetails(
        outsideUk = false,
        Address("123, Fake Street"),
        Some("AB1 2CD"),
        None,
        testEmail,
        "12345678"
      ))
    }

    when(launchpadGatewayClientMock.registerApplicant(any())).thenReturn {
      Future.successful(RegisterApplicantResponse(
        testLaunchpadCandidateId,
        testFaststreamCustomCandidateId
      ))
    }

    when(launchpadGatewayClientMock.inviteApplicant(any())).thenReturn {
      Future.successful(InviteApplicantResponse(
        testInviteId,
        testLaunchpadCandidateId,
        testFaststreamCustomCandidateId,
        testCandidateRedirectUrl,
        "Tomorrow"
      ))
    }

    when(p3TestRepositoryMock.getTestGroup(any())).thenReturn(Future.successful(None))

    when(p3TestRepositoryMock.insertOrUpdateTestGroup(any(), any())).thenReturn(Future.successful(()))

    when(appRepositoryMock.removeProgressStatuses(any(), any())).thenReturn(Future.successful(()))

    val phase3TestService = new Phase3TestService {
      val appRepository = appRepositoryMock
      val phase3TestRepo = p3TestRepositoryMock
      val cdRepository = cdRepositoryMock
      val launchpadGatewayClient = launchpadGatewayClientMock
      val tokenFactory = tokenFactoryMock
      val dateTimeFactory = dateTimeFactoryMock
      val emailClient = emailClientMock
      val auditService = auditServiceMock
      val gatewayConfig = gatewayConfigMock
      val eventService = eventServiceMock
    }
  }

}
