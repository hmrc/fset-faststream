/*
 * Copyright 2023 HM Revenue & Customs
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
import connectors.ExchangeObjects.{AssessmentOrderAcknowledgement, RegisterCandidateRequest}
import connectors.{OnlineTestEmailClient, OnlineTestsGatewayClient}
import factories._
import model.EvaluationResults.Green
import model.Exceptions._
import model.ProgressStatuses.ProgressStatus
import model._
import model.command.ProgressResponseExamples
import model.exchange.PsiRealTimeResults
import model.persisted.sift.{MaybeSiftTestGroupWithAppId, NotificationExpiringSift, SiftTestGroup}
import model.persisted.{ContactDetails, PsiTest, PsiTestResult, SchemeEvaluationResult}
import org.joda.time.{DateTime, LocalDate}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import repositories.SchemeRepository
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.sift.ApplicationSiftRepository
import services.stc.StcEventServiceFixture
import testkit.MockitoImplicits._
import testkit.{ExtendedTimeout, UnitSpec}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{LocalTime, OffsetDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, ExecutionException, Future}

class NumericalTestServiceSpec extends UnitSpec with ExtendedTimeout {

  trait TestFixture extends StcEventServiceFixture {

    val now: OffsetDateTime = OffsetDateTime.now
    val inventoryIds: Map[String, String] = Map[String, String]("test1" -> "test1-uuid")
    def testIds(idx: Int): PsiTestIds =
      PsiTestIds(s"inventory-id-$idx", s"assessment-id-$idx", s"report-id-$idx", s"norm-id-$idx")

    val tests: Map[String, PsiTestIds] = Map[String, PsiTestIds](
      "test1" -> testIds(1),
      "test2" -> testIds(2),
      "test3" -> testIds(3),
      "test4" -> testIds(4)
    )

    val mockPhase1TestConfig = Phase1TestsConfig(
      expiryTimeInDays = 5, gracePeriodInSecs = 0, testRegistrationDelayInSecs = 1, tests, standard = List("test1", "test2", "test3", "test4"),
      gis = List("test1", "test4")
    )

    val mockPhase2TestConfig = Phase2TestsConfig(
      expiryTimeInDays = 5, expiryTimeInDaysForInvigilatedETray = 90, gracePeriodInSecs = 0, testRegistrationDelayInSecs = 1,
      tests, standard = List("test1", "test2")
    )

    val mockNumericalTestsConfig = NumericalTestsConfig(gracePeriodInSecs = 0, tests = tests, standard = List("test1"))
    val integrationConfig = OnlineTestsGatewayConfig(
      url = "",
      phase1Tests = mockPhase1TestConfig,
      phase2Tests = mockPhase2TestConfig,
      numericalTests = NumericalTestsConfig(gracePeriodInSecs = 0, tests = tests, standard = List("test1")),
      reportConfig = ReportConfig(1, 2, "en-GB"),
      candidateAppUrl = "http://localhost:9284",
      emailDomain = "test.com"
    )

    def uuid: String = UUIDFactory.generateUUID()
    val orderId: String = uuid
    val realTimeResults = PsiRealTimeResults(tScore = 10.0, rawScore = 20.0, reportUrl = None)


    val mockAppRepo = mock[GeneralApplicationRepository]
    val mockSiftRepo = mock[ApplicationSiftRepository]
    val mockOnlineTestsGatewayClient = mock[OnlineTestsGatewayClient]
    val mockMicroserviceAppConfig = mock[MicroserviceAppConfig]
    when(mockMicroserviceAppConfig.onlineTestsGatewayConfig).thenReturn(integrationConfig)
    val tokenFactory: UUIDFactory = new UUIDFactoryImpl
    val mockDateTimeFactory = mock[DateTimeFactory]
    val mockSchemeRepository = mock[SchemeRepository]
    when(mockSchemeRepository.schemes).thenReturn(
      Seq(
        Scheme("DigitalDataTechnologyAndCyber", "DDTaC", "Digital, Data, Technology & Cyber", civilServantEligible = false,
          degree = None, Some(SiftRequirement.FORM), siftEvaluationRequired = false, fsbType = None, schemeGuide = None,
          schemeQuestion = None
        ),
        Scheme("Commercial", "GCS", "Commercial", civilServantEligible = false, degree = None, Some(SiftRequirement.NUMERIC_TEST),
          siftEvaluationRequired = true, fsbType = None, schemeGuide = None, schemeQuestion = None
        )
      )
    )
    val mockEmailClient = mock[OnlineTestEmailClient]
    val mockContactDetailsRepo = mock[ContactDetailsRepository]

    val service = new NumericalTestService(
      mockAppRepo,
      mockSiftRepo,
      mockOnlineTestsGatewayClient,
      tokenFactory,
      mockMicroserviceAppConfig,
      mockDateTimeFactory,
      mockSchemeRepository,
      mockEmailClient,
      mockContactDetailsRepo,
      stcEventServiceMock
    )

    val appId = "appId"
    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val rh: RequestHeader = mock[RequestHeader]
//    val invite = Invitation(userId = 1, "test@test.com", accessCode = "123", logonUrl = "", authenticateUrl = "",
//      participantScheduleId = 1)

    val contactDetails = ContactDetails(outsideUk = false, Address("line1"), postCode = None, country = None,
      email = "test@test.com", "0800900900")

    val siftTestGroupNoTests = SiftTestGroup(expirationDate = OffsetDateTime.now(), tests = None)

    val app = NumericalTestApplication(appId, "userId", "testAccountId", ApplicationStatus.SIFT,
      "PrefName", "LastName", Seq(SchemeEvaluationResult("Commercial", Green.toString)))
    val applications = List(app)

    val authenticateUrl = "http://localhost/authenticate"

    def aoa = AssessmentOrderAcknowledgement(
      customerId = "cust-id", receiptId = "receipt-id", orderId = "orderId", testLaunchUrl = authenticateUrl,
      status = AssessmentOrderAcknowledgement.acknowledgedStatus, statusDetails = "", statusDate = LocalDate.now())

    when(mockOnlineTestsGatewayClient.psiRegisterApplicant(any[RegisterCandidateRequest])(any[ExecutionContext]))
      .thenReturnAsync(aoa)
  }

  "NumericalTestService.registerAndInviteForTests" must {
    "handle an empty list of applications" in new TestFixture {
      service.registerAndInviteForTests(Nil).futureValue
      verifyZeroInteractions(mockOnlineTestsGatewayClient)
    }

    "throw an exception if no SIFT_PHASE test group is found" in new TestFixture {
      when(mockSiftRepo.getTestGroup(any[String])).thenReturnAsync(None) // This will result in exception being thrown

      val failedFuture = service.registerAndInviteForTests(applications).failed.futureValue
      failedFuture mustBe a[UnexpectedException]
      failedFuture.getMessage mustBe s"Application $appId should have a SIFT_PHASE testGroup at this point"
    }

    //TODO: take a closer look at the NotImplementedError
    "throw an exception if no SIFT_PHASE test group is found and the tests have already been populated" in new TestFixture {
      val test = PsiTest(inventoryId = "inventoryUuid", orderId = "orderUuid", assessmentId = "assessmentUuid",
        reportId = "reportUuid", normId = "normUuid", usedForResults = true,
        testUrl = authenticateUrl, invitationDate = DateTimeFactoryMock.nowLocalTimeZoneJavaTime)

      val siftTestGroup = SiftTestGroup(expirationDate = OffsetDateTime.now(), tests = Some(List(test)))
      // This will result in exception being thrown
      when(mockSiftRepo.getTestGroup(any[String])).thenReturnAsync(Some(siftTestGroup))
      when(mockSiftRepo.insertNumericalTests(any[String], any[List[PsiTest]])).thenReturnAsync()

      val failedFuture = service.registerAndInviteForTests(applications).failed.futureValue
      failedFuture mustBe a[ExecutionException]
      failedFuture.getCause.getMessage mustBe "Test may have been reset, change the active test here"
    }

    "throw an exception if no notification expiring sift details are found" in new TestFixture {
      when(mockSiftRepo.getTestGroup(any[String])).thenReturnAsync(Some(siftTestGroupNoTests))
      when(mockSiftRepo.insertNumericalTests(any[String], any[List[PsiTest]])).thenReturnAsync()

      when(mockContactDetailsRepo.find(any[String])).thenReturnAsync(contactDetails)

      when(mockSiftRepo.getNotificationExpiringSift(any[String])).thenReturnAsync(None) // This will result in exception being thrown

      val failedFuture = service.registerAndInviteForTests(applications).failed.futureValue
      failedFuture mustBe a[IllegalStateException]
      failedFuture.getMessage mustBe s"No sift notification details found for candidate $appId"
    }

    "successfully process a candidate" in new TestFixture {
      when(mockSiftRepo.getTestGroup(any[String])).thenReturnAsync(Some(siftTestGroupNoTests))
      when(mockSiftRepo.insertNumericalTests(any[String], any[List[PsiTest]])).thenReturnAsync()

      when(mockContactDetailsRepo.find(any[String])).thenReturnAsync(contactDetails)

      val notificationExpiringSift = NotificationExpiringSift(appId, "userId", "Jo", OffsetDateTime.now())
      when(mockSiftRepo.getNotificationExpiringSift(any[String])).thenReturnAsync(Some(notificationExpiringSift))

      when(mockEmailClient.sendSiftNumericTestInvite(any[String], any[String], any[OffsetDateTime])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturnAsync()

      when(mockAppRepo.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatus])).thenReturnAsync()

      service.registerAndInviteForTests(applications).futureValue

      verify(mockAppRepo, times(1)).addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_TEST_INVITED)
      verify(mockEmailClient, times(1))
        .sendSiftNumericTestInvite(eqTo(contactDetails.email), eqTo(notificationExpiringSift.preferredName),
          eqTo(notificationExpiringSift.expiryDate))(any[HeaderCarrier], any[ExecutionContext])
    }
  }

  "NumericalTestService.nextApplicationWithResultsReceived" must {
    "handle no application to process" in new TestFixture {
      when(mockSiftRepo.nextApplicationWithResultsReceived).thenReturnAsync(None)

      val result = service.nextApplicationWithResultsReceived.futureValue
      result mustBe None

      verifyZeroInteractions(mockAppRepo)
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

      val currentSchemeStatus = Seq(SchemeEvaluationResult(SchemeId("DigitalDataTechnologyAndCyber"), Green.toString))
      when(mockAppRepo.getCurrentSchemeStatus(eqTo(appId))).thenReturnAsync(currentSchemeStatus)

      val result = service.nextApplicationWithResultsReceived.futureValue
      result mustBe Some(appId)
    }

    "not return the candidate for progression who has schemes that require a form to be filled in but has not yet filled " +
      "in those forms" in new TestFixture {
      when(mockSiftRepo.nextApplicationWithResultsReceived).thenReturnAsync(Some(appId))
      when(mockAppRepo.findProgress(eqTo(appId))).thenReturnAsync(ProgressResponseExamples.InSiftEntered)

      val currentSchemeStatus = Seq(SchemeEvaluationResult(SchemeId("DigitalDataTechnologyAndCyber"), Green.toString))
      when(mockAppRepo.getCurrentSchemeStatus(eqTo(appId))).thenReturnAsync(currentSchemeStatus)

      val result = service.nextApplicationWithResultsReceived.futureValue
      result mustBe None
    }
  }

  "store real time results" should {
    "handle not finding an application for the given order id" in new TestFixture {
      when(mockSiftRepo.getApplicationIdForOrderId(any[String])).thenReturn(Future.failed(CannotFindApplicationByOrderIdException("Boom")))

      val result = service.storeRealTimeResults(orderId, realTimeResults)

      val exception = result.failed.futureValue
      exception mustBe an[CannotFindApplicationByOrderIdException]
    }

    "handle not finding a test profile for the given order id" in new TestFixture {
      when(mockSiftRepo.getApplicationIdForOrderId(any[String])).thenReturnAsync(appId)

      when(mockSiftRepo.getTestGroupByOrderId(any[String])).thenReturn(Future.failed(
        CannotFindTestByOrderIdException(s"Cannot find test group by orderId=$orderId")
      ))

      val result = service.storeRealTimeResults(orderId, realTimeResults)

      val exception = result.failed.futureValue
      exception mustBe an[CannotFindTestByOrderIdException]
      exception.getMessage mustBe s"Cannot find test group by orderId=$orderId"
    }

    "handle not finding the test group when checking to update the progress status" in new TestFixture {
      when(mockSiftRepo.getApplicationIdForOrderId(any[String])).thenReturnAsync(appId)

      val numericTestCompleted = PsiTest(inventoryId = uuid, orderId = orderId, assessmentId = uuid, reportId = uuid,
        normId = uuid, usedForResults = true,
        testUrl = authenticateUrl, invitationDate = OffsetDateTime.of(
          java.time.LocalDate.of(2016, 5, 11), java.time.LocalTime.of(0, 0), ZoneOffset.UTC),
        completedDateTime = Some(OffsetDateTime.now))

      val maybeSiftTestGroupWithAppId = MaybeSiftTestGroupWithAppId(
        applicationId = appId,
        expirationDate = OffsetDateTime.now,
        tests = Some(List(numericTestCompleted)))

      when(mockSiftRepo.getTestGroupByOrderId(any[String])).thenReturnAsync(maybeSiftTestGroupWithAppId)
      when(mockSiftRepo.insertPsiTestResult(any[String], any[PsiTest], any[PsiTestResult])).thenReturnAsync()
      when(mockSiftRepo.getTestGroup(any[String])).thenReturnAsync(None)

      val result = service.storeRealTimeResults(orderId, realTimeResults)

      val exception = result.failed.futureValue
      exception mustBe an[Exception]
      exception.getMessage mustBe s"No sift test group returned for $appId"

      verify(mockSiftRepo, never()).updateTestCompletionTime(any[String], any[DateTime])
      verify(mockAppRepo, never()).addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatuses.ProgressStatus])
    }

    "process the real time results and update the progress status" in new TestFixture {
      when(mockSiftRepo.getApplicationIdForOrderId(any[String])).thenReturnAsync(appId)

      val numericTestCompletedWithScores = PsiTest(inventoryId = uuid, orderId = orderId, assessmentId = uuid, reportId = uuid,
        normId = uuid, usedForResults = true,
        testUrl = authenticateUrl, invitationDate = OffsetDateTime.of(
          java.time.LocalDate.of(2016, 5, 11), java.time.LocalTime.of(0, 0), ZoneOffset.UTC),
        completedDateTime = Some(now),
        testResult = Some(PsiTestResult(tScore = 20.0, rawScore = 40.0, testReportUrl = None))
      )

      val maybeSiftTestGroupWithAppId = MaybeSiftTestGroupWithAppId(
        applicationId = appId,
        expirationDate = now,
        tests = Some(List(numericTestCompletedWithScores)))

      when(mockSiftRepo.getTestGroupByOrderId(any[String])).thenReturnAsync(maybeSiftTestGroupWithAppId)
      when(mockSiftRepo.insertPsiTestResult(any[String], any[PsiTest], any[PsiTestResult])).thenReturnAsync()

      val siftTestGroup = SiftTestGroup(expirationDate = now, tests = Some(List(numericTestCompletedWithScores)))
      when(mockSiftRepo.getTestGroup(any[String])).thenReturnAsync(Some(siftTestGroup))
      when(mockAppRepo.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatuses.ProgressStatus])).thenReturnAsync()

      service.storeRealTimeResults(orderId, realTimeResults).futureValue

      verify(mockSiftRepo, never()).updateTestCompletionTime(any[String], any[DateTime])
      verify(mockAppRepo, times(1)).addProgressStatusAndUpdateAppStatus(any[String],
        eqTo(ProgressStatuses.SIFT_TEST_RESULTS_RECEIVED))
    }

    "process the real time results, mark the test as completed and update the progress status" in new TestFixture {
      when(mockSiftRepo.getApplicationIdForOrderId(any[String])).thenReturnAsync(appId)

      val numericTestNotCompletedWithScores = PsiTest(inventoryId = uuid, orderId = orderId, assessmentId = uuid, reportId = uuid,
        normId = uuid, usedForResults = true,
        testUrl = authenticateUrl, invitationDate = OffsetDateTime.of(java.time.LocalDate.of(2016, 5, 11),
          java.time.LocalTime.of(0, 0), ZoneOffset.UTC),
        testResult = Some(PsiTestResult(tScore = 20.0, rawScore = 40.0, testReportUrl = None))
      )

      val maybeSiftTestGroupWithAppIdTestNotCompleted = MaybeSiftTestGroupWithAppId(
        applicationId = appId,
        expirationDate = now,
        tests = Some(List(numericTestNotCompletedWithScores)))

      val numericTestCompletedWithScores = PsiTest(inventoryId = uuid, orderId = orderId, assessmentId = uuid, reportId = uuid,
        normId = uuid, usedForResults = true,
        testUrl = authenticateUrl,
        invitationDate = OffsetDateTime.of(java.time.LocalDate.of(2016, 5, 11),
          java.time.LocalTime.of(0, 0), ZoneOffset.UTC),
        completedDateTime = Some(now),
        testResult = Some(PsiTestResult(tScore = 20.0, rawScore = 40.0, testReportUrl = None))
      )

      val maybeSiftTestGroupWithAppIdTestCompleted = MaybeSiftTestGroupWithAppId(
        applicationId = appId,
        expirationDate = now,
        tests = Some(List(numericTestCompletedWithScores)))

      //First call return tests that are not completed second call return tests that are are completed
      when(mockSiftRepo.getTestGroupByOrderId(any[String]))
        .thenReturnAsync(maybeSiftTestGroupWithAppIdTestNotCompleted)
        .thenReturnAsync(maybeSiftTestGroupWithAppIdTestCompleted)

      when(mockSiftRepo.updateTestCompletionTime(any[String], any[DateTime])).thenReturnAsync()
      when(mockSiftRepo.insertPsiTestResult(any[String], any[PsiTest], any[model.persisted.PsiTestResult])).thenReturnAsync()

      when(mockAppRepo.addProgressStatusAndUpdateAppStatus(any[String], eqTo(ProgressStatuses.SIFT_TEST_COMPLETED))).thenReturnAsync()

      val siftTestGroup = SiftTestGroup(expirationDate = now, tests = Some(List(numericTestCompletedWithScores)))

      when(mockSiftRepo.getTestGroup(any[String])).thenReturnAsync(Some(siftTestGroup))
      when(mockAppRepo.addProgressStatusAndUpdateAppStatus(any[String], eqTo(ProgressStatuses.SIFT_TEST_RESULTS_RECEIVED))).thenReturnAsync()

      service.storeRealTimeResults(orderId, realTimeResults).futureValue

      verify(mockSiftRepo, times(1)).updateTestCompletionTime(any[String], any[DateTime])
      verify(mockAppRepo, times(1)).addProgressStatusAndUpdateAppStatus(any[String], eqTo(ProgressStatuses.SIFT_TEST_COMPLETED))
      verify(mockAppRepo, times(1)).addProgressStatusAndUpdateAppStatus(any[String], eqTo(ProgressStatuses.SIFT_TEST_RESULTS_RECEIVED))
    }
  }
}
