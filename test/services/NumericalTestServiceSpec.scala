/*
 * Copyright 2020 HM Revenue & Customs
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

package services

import config.{ OnlineTestsGatewayConfig, NumericalTestSchedule, NumericalTestsConfig }
import connectors.ExchangeObjects.{ Invitation, InviteApplicant, Registration }
import connectors.{ OnlineTestsGatewayClient, EmailClient }
import factories.{ DateTimeFactory, UUIDFactory }
import model.EvaluationResults.Green
import model.Exceptions.UnexpectedException
import model.ProgressStatuses.ProgressStatus
import model._
import model.command.ProgressResponseExamples
import model.persisted.sift.{ NotificationExpiringSift, SiftTestGroup }
import model.persisted.{ ContactDetails, CubiksTest, SchemeEvaluationResult }
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import repositories.SchemeRepository
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.sift.ApplicationSiftRepository
import services.stc.StcEventServiceFixture
import testkit.MockitoImplicits._
import testkit.{ ExtendedTimeout, UnitSpec }
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionException

class NumericalTestServiceSpec extends UnitSpec with ExtendedTimeout {

  trait TestFixture extends StcEventServiceFixture {

    val mockAppRepo: GeneralApplicationRepository = mock[GeneralApplicationRepository]
    val mockSiftRepo: ApplicationSiftRepository = mock[ApplicationSiftRepository]
    val mockContactDetailsRepo: ContactDetailsRepository = mock[ContactDetailsRepository]
    val mockEmailClient: EmailClient = mock[EmailClient]

    val mockOnlineTestsGatewayConfig = mock[OnlineTestsGatewayConfig]
    when(mockOnlineTestsGatewayConfig.candidateAppUrl).thenReturn("localhost")
    val mockNumericalTestsConfig = NumericalTestsConfig(Map(NumericalTestsConfig.numericalTestScheduleName -> NumericalTestSchedule(1, 1)))

    val mockOnlineTestsGatewayClient: OnlineTestsGatewayClient = mock[OnlineTestsGatewayClient]
    val mockDateTimeFactory: DateTimeFactory = mock[DateTimeFactory]

    val mockSchemeRepo = new SchemeRepository {
      override lazy val schemes: Seq[Scheme] = Seq(
        Scheme("DigitalAndTechnology", "DaT", "Digital and Technology", civilServantEligible = false, None, Some(SiftRequirement.FORM),
          siftEvaluationRequired = false, fsbType = None, schemeGuide = None, schemeQuestion = None
        ),
        Scheme("Commercial", "GCS", "Commercial", civilServantEligible = false, None, Some(SiftRequirement.NUMERIC_TEST),
          siftEvaluationRequired = true, fsbType = None, schemeGuide = None, schemeQuestion = None
        )
      )
    }

    val service = new NumericalTestService {
      override def applicationRepo: GeneralApplicationRepository = mockAppRepo
      override def applicationSiftRepo: ApplicationSiftRepository = mockSiftRepo
      val tokenFactory: UUIDFactory = UUIDFactory
      val gatewayConfig: OnlineTestsGatewayConfig = mockOnlineTestsGatewayConfig
      override def testConfig: NumericalTestsConfig = mockNumericalTestsConfig
      val onlineTestsGatewayClient: OnlineTestsGatewayClient = mockOnlineTestsGatewayClient
      val dateTimeFactory: DateTimeFactory = mockDateTimeFactory
      override def schemeRepository: SchemeRepository = mockSchemeRepo
      val eventService = eventServiceMock
      override def emailClient = mockEmailClient
      override def contactDetailsRepo = mockContactDetailsRepo
    }

    val appId = "appId"
    implicit val hc = HeaderCarrier()
    implicit val rh = mock[RequestHeader]
    val invite = Invitation(userId = 1, "test@test.com", accessCode = "123", logonUrl = "", authenticateUrl = "",
      participantScheduleId = 1)

    val contactDetails = ContactDetails(outsideUk = false, Address("line1"), postCode = None, country = None,
      email = "test@test.com", "0800900900")

    val siftTestGroupNoTests = SiftTestGroup(expirationDate = DateTime.now(), tests = None)

    val app = NumericalTestApplication(appId, "userId", ApplicationStatus.SIFT, needsOnlineAdjustments = false,
      eTrayAdjustments = None, currentSchemeStatus = Seq(SchemeEvaluationResult("Commercial", Green.toString)))
    val applications = List(app)
  }

  "NumericalTestService.registerAndInviteForTests" must {
    "handle an empty list of applications" in new TestFixture {
      service.registerAndInviteForTests(Nil).futureValue
      verifyZeroInteractions(service.onlineTestsGatewayClient)
    }

    "throw an exception if no SIFT_PHASE test group is found" in new TestFixture {
      when(mockOnlineTestsGatewayClient.registerApplicants(any[Int])).thenReturnAsync(List(Registration(userId = 1)))

      when(mockOnlineTestsGatewayClient.inviteApplicants(any[List[InviteApplicant]])).thenReturnAsync(List(invite))

      when(mockSiftRepo.getTestGroup(any[String])).thenReturnAsync(None) // This will result in exception being thrown

      val failedFuture = service.registerAndInviteForTests(applications).failed.futureValue
      failedFuture mustBe a[UnexpectedException]
      failedFuture.getMessage mustBe s"Application $appId should have a SIFT_PHASE testGroup at this point"
    }

    //TODO: take a closer look at the NotImplementedError
    "throw an exception if no SIFT_PHASE test group is found and the tests have already been populated" in new TestFixture {
      when(mockOnlineTestsGatewayClient.registerApplicants(any[Int])).thenReturnAsync(List(Registration(userId = 1)))

      when(mockOnlineTestsGatewayClient.inviteApplicants(any[List[InviteApplicant]])).thenReturnAsync(List(invite))

      val cubiksTest = CubiksTest(
        scheduleId = 1,
        usedForResults = true,
        cubiksUserId = 2,
        token = "abc",
        testUrl = "test@test.com",
        invitationDate = DateTime.now(),
        participantScheduleId = 1
      )

      val siftTestGroup = SiftTestGroup(expirationDate = DateTime.now(), tests = Some(List(cubiksTest)))
      when(mockSiftRepo.getTestGroup(any[String])).thenReturnAsync(Some(siftTestGroup)) // This will result in exception being thrown

      val failedFuture = service.registerAndInviteForTests(applications).failed.futureValue
      failedFuture mustBe a[ExecutionException]
      failedFuture.getCause.getMessage mustBe "Test may have been reset, change the active test here"
    }

    "throw an exception if no notification expiring sift details are found" in new TestFixture {
      when(mockOnlineTestsGatewayClient.registerApplicants(any[Int])).thenReturnAsync(List(Registration(userId = 1)))

      when(mockOnlineTestsGatewayClient.inviteApplicants(any[List[InviteApplicant]])).thenReturnAsync(List(invite))

      when(mockSiftRepo.getTestGroup(any[String])).thenReturnAsync(Some(siftTestGroupNoTests))
      when(mockSiftRepo.insertNumericalTests(any[String], any[List[CubiksTest]])).thenReturnAsync()

      when(mockContactDetailsRepo.find(any[String])).thenReturnAsync(contactDetails)

      when(mockSiftRepo.getNotificationExpiringSift(any[String])).thenReturnAsync(None) // This will result in exception being thrown

      val failedFuture = service.registerAndInviteForTests(applications).failed.futureValue
      failedFuture mustBe a[IllegalStateException]
      failedFuture.getMessage mustBe s"No sift notification details found for candidate $appId"
    }

    "successfully process a candidate" in new TestFixture {
      when(mockOnlineTestsGatewayClient.registerApplicants(any[Int])).thenReturnAsync(List(Registration(userId = 1)))

      when(mockOnlineTestsGatewayClient.inviteApplicants(any[List[InviteApplicant]])).thenReturnAsync(List(invite))

      when(mockSiftRepo.getTestGroup(any[String])).thenReturnAsync(Some(siftTestGroupNoTests))
      when(mockSiftRepo.insertNumericalTests(any[String], any[List[CubiksTest]])).thenReturnAsync()

      when(mockContactDetailsRepo.find(any[String])).thenReturnAsync(contactDetails)

      val notificationExpiringSift = NotificationExpiringSift(appId, "userId", "Jo", DateTime.now())
      when(mockSiftRepo.getNotificationExpiringSift(any[String])).thenReturnAsync(Some(notificationExpiringSift))

      when(mockEmailClient.sendSiftNumericTestInvite(any[String], any[String], any[DateTime])(any[HeaderCarrier])).thenReturnAsync()

      when(mockAppRepo.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()

      service.registerAndInviteForTests(applications).futureValue
    }
  }

  "NumericalTestService.nextApplicationWithResultsReceived" must {
    "handle no application to process" in new TestFixture {
      when(mockSiftRepo.nextApplicationWithResultsReceived).thenReturnAsync(None)

      val result = service.nextApplicationWithResultsReceived.futureValue
      result mustBe None

      verifyZeroInteractions(service.applicationRepo)
    }

    // the current scheme status is Commercial, which has no FORM requirement only NUMERIC_TEST requirement
    // so candidate can be progressed (moved to SIFT_READY)
    "return the candidate for progression who has no schemes that require a form to be filled" in new TestFixture {
      when(mockSiftRepo.nextApplicationWithResultsReceived).thenReturnAsync(Some(appId))
      when(mockAppRepo.findProgress(eqTo(appId))).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      val currentSchemeStatus = Seq(SchemeEvaluationResult(SchemeId("Commercial"), Green.toString))
      when(mockAppRepo.getCurrentSchemeStatus(eqTo(appId))).thenReturnAsync(currentSchemeStatus)

      val result = service.nextApplicationWithResultsReceived.futureValue
      result mustBe Some(appId)
    }

    "return the candidate for progression who has schemes that require a form to be filled in and has already " +
      "satisfied that requirement" in new TestFixture {
      when(mockSiftRepo.nextApplicationWithResultsReceived).thenReturnAsync(Some(appId))
      when(mockAppRepo.findProgress(eqTo(appId))).thenReturnAsync(ProgressResponseExamples.InSiftFormsCompleteNumericTestPendingProgress)

      val currentSchemeStatus = Seq(SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), Green.toString))
      when(mockAppRepo.getCurrentSchemeStatus(eqTo(appId))).thenReturnAsync(currentSchemeStatus)

      val result = service.nextApplicationWithResultsReceived.futureValue
      result mustBe Some(appId)
    }

    "not return the candidate for progression who has schemes that require a form to be filled in but has not yet filled " +
      "in those forms" in new TestFixture {
      when(mockSiftRepo.nextApplicationWithResultsReceived).thenReturnAsync(Some(appId))
      when(mockAppRepo.findProgress(eqTo(appId))).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      val currentSchemeStatus = Seq(SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), Green.toString))
      when(mockAppRepo.getCurrentSchemeStatus(eqTo(appId))).thenReturnAsync(currentSchemeStatus)

      val result = service.nextApplicationWithResultsReceived.futureValue
      result mustBe None
    }
  }
}
