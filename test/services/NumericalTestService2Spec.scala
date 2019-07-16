/*
 * Copyright 2019 HM Revenue & Customs
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

import config._
import connectors.ExchangeObjects.{ AssessmentOrderAcknowledgement, Invitation, RegisterCandidateRequest }
import connectors.{ EmailClient, OnlineTestsGatewayClient }
import factories.{ DateTimeFactory, UUIDFactory }
import model.EvaluationResults.Green
import model.Exceptions.UnexpectedException
import model.ProgressStatuses.ProgressStatus
import model._
import model.command.ProgressResponseExamples
import model.persisted.sift.{ NotificationExpiringSift, SiftTestGroup2 }
import model.persisted.{ ContactDetails, PsiTest, SchemeEvaluationResult }
import org.joda.time.{ DateTime, LocalDate }
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

import scala.concurrent.{ ExecutionException, Future }

class NumericalTestService2Spec extends UnitSpec with ExtendedTimeout {

  trait TestFixture extends StcEventServiceFixture {

    val mockAppRepo: GeneralApplicationRepository = mock[GeneralApplicationRepository]
    val mockSiftRepo: ApplicationSiftRepository = mock[ApplicationSiftRepository]
    val mockContactDetailsRepo: ContactDetailsRepository = mock[ContactDetailsRepository]
    val mockEmailClient: EmailClient = mock[EmailClient]

    val mockOnlineTestsGatewayConfig = mock[OnlineTestsGatewayConfig]
    when(mockOnlineTestsGatewayConfig.candidateAppUrl).thenReturn("localhost")

    val inventoryIds = Map[String, String]("test1" -> "test1-uuid")
    def testIds(idx: Int): PsiTestIds =
      PsiTestIds(s"inventory-id-$idx", Option(s"assessment-id-$idx"), Option(s"report-id-$idx"), Option(s"norm-id-$idx"))

    val tests = Map[String, PsiTestIds](
      "test1" -> testIds(1),
      "test2" -> testIds(2),
      "test3" -> testIds(3),
      "test4" -> testIds(4)
    )

    val mockPhase1TestConfig = Phase1TestsConfig2(
      5, tests, List("test1", "test2", "test3", "test4"), List("test1", "test4")
    )
    val mockPhase2TestConfig = Phase2TestsConfig2(
      5, 90, tests, List("test1", "test4")
    )

    val mockNumericalTestsConfig2 = NumericalTestsConfig2(tests, List("test1"))
    val integrationConfig = TestIntegrationGatewayConfig(
      url = "",
      phase1Tests = mockPhase1TestConfig,
      phase2Tests = mockPhase2TestConfig,
      numericalTests = NumericalTestsConfig2(tests, List("test1")),
      reportConfig = ReportConfig(1, 2, "en-GB"),
      candidateAppUrl = "http://localhost:9284",
      emailDomain = "test.com"
    )

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

    val service = new NumericalTestService2 {
      override def applicationRepo: GeneralApplicationRepository = mockAppRepo
      override def applicationSiftRepo: ApplicationSiftRepository = mockSiftRepo
      val tokenFactory: UUIDFactory = UUIDFactory
      val gatewayConfig: OnlineTestsGatewayConfig = mockOnlineTestsGatewayConfig
      val onlineTestsGatewayClient: OnlineTestsGatewayClient = mockOnlineTestsGatewayClient
      val dateTimeFactory: DateTimeFactory = mockDateTimeFactory
      override def schemeRepository: SchemeRepository = mockSchemeRepo
      val eventService = eventServiceMock
      override def emailClient = mockEmailClient
      override def contactDetailsRepo = mockContactDetailsRepo

      override val integrationGatewayConfig: TestIntegrationGatewayConfig = integrationConfig
    }

    val appId = "appId"
    implicit val hc = HeaderCarrier()
    implicit val rh = mock[RequestHeader]
    val invite = Invitation(userId = 1, "test@test.com", accessCode = "123", logonUrl = "", authenticateUrl = "",
      participantScheduleId = 1)

    val contactDetails = ContactDetails(outsideUk = false, Address("line1"), postCode = None, country = None,
      email = "test@test.com", "0800900900")

    val siftTestGroupNoTests = SiftTestGroup2(expirationDate = DateTime.now(), tests = None)

    val app = NumericalTestApplication2(appId, "userId", "testAccountId", ApplicationStatus.SIFT,
      "PrefName", "LastName", Seq(SchemeEvaluationResult("Commercial", Green.toString)))
    val applications = List(app)

    val authenticateUrl = "http://localhost/authenticate"

    def aoa = AssessmentOrderAcknowledgement(
      customerId = "cust-id", receiptId = "receipt-id", orderId = "orderId", testLaunchUrl = authenticateUrl,
      status = AssessmentOrderAcknowledgement.acknowledgedStatus, statusDetails = "", statusDate = LocalDate.now())

    when(service.onlineTestsGatewayClient.psiRegisterApplicant(any[RegisterCandidateRequest]))
      .thenReturn(Future.successful(aoa))
  }

  "NumericalTestService.registerAndInviteForTests" must {
    "handle an empty list of applications" in new TestFixture {
      service.registerAndInviteForTests(Nil).futureValue
      verifyZeroInteractions(service.onlineTestsGatewayClient)
    }

    "throw an exception if no SIFT_PHASE test group is found" in new TestFixture {
      when(mockSiftRepo.getTestGroup2(any[String])).thenReturnAsync(None) // This will result in exception being thrown

      val failedFuture = service.registerAndInviteForTests(applications).failed.futureValue
      failedFuture mustBe a[UnexpectedException]
      failedFuture.getMessage mustBe s"Application $appId should have a SIFT_PHASE testGroup at this point"
    }

    //TODO: take a closer look at the NotImplementedError
    "throw an exception if no SIFT_PHASE test group is found and the tests have already been populated" in new TestFixture {
      val test = PsiTest(inventoryId = "inventoryUuid", orderId = "inventoryUuid", usedForResults = true,
        testUrl = authenticateUrl, invitationDate = DateTimeFactory.nowLocalTimeZone)

      val siftTestGroup = SiftTestGroup2(expirationDate = DateTime.now(), tests = Some(List(test)))
      // This will result in exception being thrown
      when(mockSiftRepo.getTestGroup2(any[String])).thenReturnAsync(Some(siftTestGroup))
      when(mockSiftRepo.insertNumericalTests2(any[String], any[List[PsiTest]])).thenReturnAsync()

      val failedFuture = service.registerAndInviteForTests(applications).failed.futureValue
      failedFuture mustBe a[ExecutionException]
      failedFuture.getCause.getMessage mustBe "Test may have been reset, change the active test here"
    }

    "throw an exception if no notification expiring sift details are found" in new TestFixture {
      when(mockSiftRepo.getTestGroup2(any[String])).thenReturnAsync(Some(siftTestGroupNoTests))
      when(mockSiftRepo.insertNumericalTests2(any[String], any[List[PsiTest]])).thenReturnAsync()

      when(mockContactDetailsRepo.find(any[String])).thenReturnAsync(contactDetails)

      when(mockSiftRepo.getNotificationExpiringSift(any[String])).thenReturnAsync(None) // This will result in exception being thrown

      val failedFuture = service.registerAndInviteForTests(applications).failed.futureValue
      failedFuture mustBe a[IllegalStateException]
      failedFuture.getMessage mustBe s"No sift notification details found for candidate $appId"
    }

    "successfully process a candidate" in new TestFixture {

      when(mockSiftRepo.getTestGroup2(any[String])).thenReturnAsync(Some(siftTestGroupNoTests))
      when(mockSiftRepo.insertNumericalTests2(any[String], any[List[PsiTest]])).thenReturnAsync()

      when(mockContactDetailsRepo.find(any[String])).thenReturnAsync(contactDetails)

      val notificationExpiringSift = NotificationExpiringSift(appId, "userId", "Jo", DateTime.now())
      when(mockSiftRepo.getNotificationExpiringSift(any[String])).thenReturnAsync(Some(notificationExpiringSift))

      when(mockEmailClient.sendSiftNumericTestInvite(any[String], any[String], any[DateTime])(any[HeaderCarrier])).thenReturnAsync()

      when(mockAppRepo.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()

      service.registerAndInviteForTests(applications).futureValue

      verify(mockAppRepo, times(1)).addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_TEST_INVITED)
      verify(mockEmailClient, times(1))
        .sendSiftNumericTestInvite(eqTo(contactDetails.email), eqTo(notificationExpiringSift.preferredName),
          eqTo(notificationExpiringSift.expiryDate))(any[HeaderCarrier])
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
