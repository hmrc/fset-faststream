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

package services.onlinetesting.phase2

import akka.actor.ActorSystem
import config._
import connectors.ExchangeObjects.{ AssessmentOrderAcknowledgement, RegisterCandidateRequest, toString => _ }
import connectors.{ CSREmailClient, OnlineTestsGatewayClient }
import factories.{ DateTimeFactory, UUIDFactory }
import model.Commands.PostCode
import model.Exceptions.{ ContactDetailsNotFoundForEmail, ExpiredTestForTokenException, InvalidTokenException }
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses.{ toString => _, _ }
import model._
import model.command.{ Phase2ProgressResponse, ProgressResponse }
import model.persisted.{ ContactDetails, Phase2TestGroup2, Phase2TestGroupWithAppId2, _ }
import org.joda.time.{ DateTime, DateTimeZone, LocalDate }
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.onlinetesting.{ Phase2TestRepository, Phase2TestRepository2 }
import services.AuditService
import services.onlinetesting.phase3.Phase3TestService
import services.sift.ApplicationSiftService
import services.stc.{ StcEventService, StcEventServiceFixture }
import testkit.{ ExtendedTimeout, UnitSpec }
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import scala.language.postfixOps

class Phase2TestService2Spec extends UnitSpec with ExtendedTimeout {

  "Verify access code" should {
    "return an invigilated test url for a valid candidate" in new Phase2TestServiceFixture {
      when(cdRepositoryMock.findUserIdByEmail(any[String])).thenReturn(Future.successful(userId))

      val accessCode = "TEST-CODE"
      val phase2TestGroup = Phase2TestGroup2(
        expirationDate,
        List(phase2Test.copy(invigilatedAccessCode = Some(accessCode)))
      )
      when(otRepositoryMock2.getTestGroupByUserId(any[String])).thenReturn(Future.successful(Some(phase2TestGroup)))

      val result = phase2TestService.verifyAccessCode("test-email.com", accessCode).futureValue
      result mustBe authenticateUrl
    }

    "return a Failure if the access code does not match" in new Phase2TestServiceFixture {
      when(cdRepositoryMock.findUserIdByEmail(any[String])).thenReturn(Future.successful(authenticateUrl))

      val accessCode = "TEST-CODE"
      val phase2TestGroup = Phase2TestGroup2(
        expirationDate,
        phase2Test.copy(invigilatedAccessCode = Some(accessCode)) :: Nil
      )
      when(otRepositoryMock2.getTestGroupByUserId(any[String])).thenReturn(Future.successful(Some(phase2TestGroup)))

      val result = phase2TestService.verifyAccessCode("test-email.com", "I-DO-NOT-MATCH").failed.futureValue
      result mustBe an[InvalidTokenException]
    }

    "return a Failure if the user cannot be located by email" in new Phase2TestServiceFixture {
      when(cdRepositoryMock.findUserIdByEmail(any[String])).thenReturn(Future.failed(ContactDetailsNotFoundForEmail()))

      val result = phase2TestService.verifyAccessCode("test-email.com", "ANY-CODE").failed.futureValue
      result mustBe an[ContactDetailsNotFoundForEmail]
    }

    "return A Failure if the test is Expired" in new Phase2TestServiceFixture {
      when(cdRepositoryMock.findUserIdByEmail(any[String])).thenReturn(Future.successful(authenticateUrl))

      val accessCode = "TEST-CODE"
      val phase2TestGroup = Phase2TestGroup2(
        expiredDate,
        List(phase2Test.copy(invigilatedAccessCode = Some(accessCode)))
      )
      when(otRepositoryMock2.getTestGroupByUserId(any[String])).thenReturn(Future.successful(Some(phase2TestGroup)))

      val result = phase2TestService.verifyAccessCode("test-email.com", accessCode).failed.futureValue
      result mustBe an[ExpiredTestForTokenException]
    }
  }

  "Invite applicants to PHASE 2" must {
    "save tests for a candidate after registration" in new Phase2TestServiceFixture {
      when(otRepositoryMock2.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase2TestProfile)))
      when(otRepositoryMock2.insertPsiTests(any[String], any[Phase2TestGroup2])).thenReturn(Future.successful(()))
      when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest]))
          .thenReturn(Future.successful(aoa))

      phase2TestService.registerAndInviteForPsi(candidates).futureValue

      verify(otRepositoryMock2, times(4)).insertPsiTests(any[String], any[Phase2TestGroup2])
      verify(onlineTestsGatewayClientMock, times(4)).psiRegisterApplicant(any[RegisterCandidateRequest])
      verify(otRepositoryMock2, times(0)).insertOrUpdateTestGroup(any[String], any[Phase2TestGroup2])
    }
  }

  "mark as started" should {
    "change progress to started" in new Phase2TestServiceFixture {
      when(otRepositoryMock2.updateTestStartTime(any[String], any[DateTime])).thenReturn(Future.successful(()))
      when(otRepositoryMock2.getTestProfileByOrderId(orderId))
        .thenReturn(Future.successful(Phase2TestGroupWithAppId2("appId123", phase2TestProfile)))
      when(otRepositoryMock2.updateProgressStatus("appId123", ProgressStatuses.PHASE2_TESTS_STARTED))
        .thenReturn(Future.successful(()))
      phase2TestService.markAsStarted2(orderId).futureValue

      verify(otRepositoryMock2).updateProgressStatus("appId123", ProgressStatuses.PHASE2_TESTS_STARTED)
    }
  }

  "mark as completed" should {
    "change progress to completed if there are all tests completed" in new Phase2TestServiceFixture {
      when(otRepositoryMock2.updateTestCompletionTime2(any[String], any[DateTime])).thenReturn(Future.successful(()))
      val phase2Tests = phase2TestProfile.copy(tests = phase2TestProfile.tests.map(t => t.copy(completedDateTime = Some(DateTime.now()))),
        expirationDate = DateTime.now().plusDays(2)
      )

      when(otRepositoryMock2.getTestProfileByOrderId(orderId))
        .thenReturn(Future.successful(Phase2TestGroupWithAppId2("appId123", phase2Tests)))
      when(otRepositoryMock2.updateProgressStatus("appId123", ProgressStatuses.PHASE2_TESTS_COMPLETED))
        .thenReturn(Future.successful(()))

      phase2TestService.markAsCompleted2(orderId).futureValue

      verify(otRepositoryMock2).updateProgressStatus("appId123", ProgressStatuses.PHASE2_TESTS_COMPLETED)
    }
  }


  "processNextExpiredTest" should {
    "do nothing if there is no expired application to process" in new Phase2TestServiceFixture {
      when(otRepositoryMock2.nextExpiringApplication(Phase2ExpirationEvent)).thenReturn(Future.successful(None))
      phase2TestService.processNextExpiredTest(Phase2ExpirationEvent).futureValue mustBe unit
    }

    "update progress status and send an email to the user when a Faststream application is expired" in new Phase2TestServiceFixture {
      when(otRepositoryMock2.nextExpiringApplication(Phase2ExpirationEvent))
        .thenReturn(Future.successful(Some(expiredApplication)))
      when(cdRepositoryMock.find(any[String])).thenReturn(Future.successful(contactDetails))
      when(appRepositoryMock.getApplicationRoute(any[String])).thenReturn(Future.successful(ApplicationRoute.Faststream))

      val results = List(SchemeEvaluationResult("Commercial", "Green"))

      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatuses.ProgressStatus])).thenReturn(success)

      when(emailClientMock.sendEmailWithName(any[String], any[String], any[String])(any[HeaderCarrier])).thenReturn(success)

      val result = phase2TestService.processNextExpiredTest(Phase2ExpirationEvent)

      result.futureValue mustBe unit

      verify(cdRepositoryMock).find(userId)
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(applicationId, PHASE2_TESTS_EXPIRED)
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(applicationId, SIFT_ENTERED)
      verify(appRepositoryMock, never()).getCurrentSchemeStatus(applicationId)
      verify(appRepositoryMock, never()).updateCurrentSchemeStatus(applicationId, results)
      verify(siftServiceMock, never()).sendSiftEnteredNotification(applicationId)
      verify(emailClientMock).sendEmailWithName(emailContactDetails, preferredName, Phase2ExpirationEvent.template)
    }

    "update progress status and send an email to the user when an sdip faststream application is expired" in new Phase2TestServiceFixture {
      when(otRepositoryMock2.nextExpiringApplication(Phase2ExpirationEvent))
        .thenReturn(Future.successful(Some(expiredApplication)))
      when(cdRepositoryMock.find(any[String])).thenReturn(Future.successful(contactDetails))
      when(appRepositoryMock.getApplicationRoute(any[String])).thenReturn(Future.successful(ApplicationRoute.SdipFaststream))

      val results = List(SchemeEvaluationResult("Sdip", "Green"), SchemeEvaluationResult("Commercial", "Green"))

      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatuses.ProgressStatus])).thenReturn(success)
      when(emailClientMock.sendEmailWithName(any[String], any[String], any[String])(any[HeaderCarrier])).thenReturn(success)

      val result = phase2TestService.processNextExpiredTest(Phase2ExpirationEvent)

      result.futureValue mustBe unit

      verify(cdRepositoryMock).find(userId)
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(applicationId, PHASE2_TESTS_EXPIRED)
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(applicationId, SIFT_ENTERED)
      verify(appRepositoryMock, never()).getCurrentSchemeStatus(applicationId)
      verify(appRepositoryMock, never()).updateCurrentSchemeStatus(applicationId, results)
      verify(siftServiceMock, never()).sendSiftEnteredNotification(applicationId)
      verify(emailClientMock).sendEmailWithName(emailContactDetails, preferredName, Phase2ExpirationEvent.template)
    }
  }

  private def phase2Progress(phase2ProgressResponse: Phase2ProgressResponse) =
    ProgressResponse("appId", phase2ProgressResponse = phase2ProgressResponse)

  trait Phase2TestServiceFixture {

    implicit val hc: HeaderCarrier = mock[HeaderCarrier]
    implicit val rh: RequestHeader = mock[RequestHeader]

    val clock = mock[DateTimeFactory]
    val now = DateTimeFactory.nowLocalTimeZone.withZone(DateTimeZone.UTC)
    when(clock.nowLocalTimeZone).thenReturn(now)

    val scheduleCompletionBaseUrl = "http://localhost:9284/fset-fast-stream/online-tests/phase2"
    val inventoryIds: Map[String, String] = Map[String, String]("test3" -> "test3-uuid", "test4" -> "test4-uuid")
    val testIds = NumericalTestIds("inventory-id", Option("assessment-id"), Option("report-id"), Option("norm-id"))
    val tests = Map[String, NumericalTestIds]("test1" -> testIds)

    val mockNumericalTestsConfig2 = NumericalTestsConfig2(tests, List("test1"))
    val integrationConfigMock = TestIntegrationGatewayConfig(
      url = "",
      phase1Tests = Phase1TestsConfig2(
        5, inventoryIds, List("test1", "test2", "test2", "test4"), List("test1", "test4")
      ),
      phase2Tests = Phase2TestsConfig2(5, 90, inventoryIds, List("test3", "test4")),
      numericalTests = mockNumericalTestsConfig2,
      reportConfig = ReportConfig(1, 2, "en-GB"),
      candidateAppUrl = "http://localhost:9284",
      emailDomain = "test.com"
    )

    val orderId = uuid
    val token = "token"
    val authenticateUrl = "http://localhost/authenticate"
    val invitationDate = now
    val startedDate = invitationDate.plusDays(1)
    val expirationDate = invitationDate.plusDays(5)
    val expiredDate = now.minusMinutes(1)
    val invigilatedExpirationDate = invitationDate.plusDays(90)
    val applicationId = "appId"
    val userId = "userId"
    val preferredName = "Preferred\tName"
    val expiredApplication = ExpiringOnlineTest(applicationId, userId, preferredName)

    val aoa = AssessmentOrderAcknowledgement(
      customerId = "cust-id", receiptId = "receipt-id", orderId = orderId, testLaunchUrl = authenticateUrl,status =
        AssessmentOrderAcknowledgement.acknowledgedStatus, statusDetails = "", statusDate = LocalDate.now())

    val postcode : Option[PostCode]= Some("WC2B 4")
    val emailContactDetails = "emailfjjfjdf@mailinator.com"
    val contactDetails = ContactDetails(outsideUk = false, Address("Aldwych road"), postcode, Some("UK"), emailContactDetails, "111111")

    val success = Future.successful(unit)
    val appRepositoryMock = mock[GeneralApplicationRepository]
    val cdRepositoryMock = mock[ContactDetailsRepository]
    val otRepositoryMock = mock[Phase2TestRepository]
    val otRepositoryMock2 = mock[Phase2TestRepository2]
    val onlineTestsGatewayClientMock = mock[OnlineTestsGatewayClient]
    val emailClientMock = mock[CSREmailClient]
    val auditServiceMock = mock[AuditService]
    val tokenFactoryMock = mock[UUIDFactory]
    val eventServiceMock = mock[StcEventService]
    val phase3TestServiceMock = mock[Phase3TestService]
    val siftServiceMock = mock[ApplicationSiftService]

    val onlineTestApplication = OnlineTestApplication(applicationId = "appId",
      applicationStatus = ApplicationStatus.SUBMITTED,
      userId = "userId",
      testAccountId = "testAccountId",
      guaranteedInterview = false,
      needsOnlineAdjustments = false,
      needsAtVenueAdjustments = false,
      preferredName = "Optimus",
      lastName = "Prime",
      None,
      None
    )

    val preferredNameSanitized = "Preferred Name"
    val lastName = ""
    val onlineTestApplication2 = onlineTestApplication.copy(applicationId = "appId2", userId = "userId2")
    val adjustmentApplication = onlineTestApplication.copy(applicationId = "appId3", userId = "userId3", needsOnlineAdjustments = true)
    val adjustmentApplication2 = onlineTestApplication.copy(applicationId = "appId4", userId = "userId4", needsOnlineAdjustments = true)
    val candidates = List(onlineTestApplication, onlineTestApplication2)

    def uuid: String = UUIDFactory.generateUUID()

    val phase2Test = PsiTest(
      inventoryId = uuid, orderId = uuid, usedForResults = true, testUrl = authenticateUrl,
      invitationDate = invitationDate
    )

    val phase2TestProfile = Phase2TestGroup2(expirationDate,
      List(phase2Test, phase2Test.copy(inventoryId = uuid))
    )
    val invigilatedTestProfile = Phase2TestGroup2(
      invigilatedExpirationDate, List(phase2Test.copy(inventoryId = uuid, invigilatedAccessCode = Some("accessCode")))
    )

    val testResult = OnlineTestCommands.TestResult(status = "Completed",
      norm = "some norm",
      tScore = Some(23.9999d),
      percentile = Some(22.4d),
      raw = Some(66.9999d),
      sten = Some(1.333d)
    )

    val savedResult = model.persisted.TestResult(status = "Completed",
      norm = "some norm",
      tScore = Some(23.9999d),
      percentile = Some(22.4d),
      raw = Some(66.9999d),
      sten = Some(1.333d)
    )

    val invigilatedETrayApp = onlineTestApplication.copy(
      needsOnlineAdjustments = true,
      eTrayAdjustments = Some(AdjustmentDetail(invigilatedInfo = Some("e-tray help needed")))
    )
    val nonInvigilatedETrayApp = onlineTestApplication.copy(needsOnlineAdjustments = false)

    when(otRepositoryMock2.insertOrUpdateTestGroup(any[String], any[Phase2TestGroup2]))
        .thenReturn(Future.successful(()))

    when(otRepositoryMock2.getTestGroup(any[String]))
      .thenReturn(Future.successful(Some(phase2TestProfile)))

    when(otRepositoryMock2.resetTestProfileProgresses(any[String], any[List[ProgressStatus]]))
      .thenReturn(Future.successful(()))

    when(cdRepositoryMock.find(any[String])).thenReturn(Future.successful(
      ContactDetails(outsideUk = false, Address("Aldwych road"), Some("QQ1 1QQ"), Some("UK"), "email@test.com", "111111")))

    when(emailClientMock.sendOnlineTestInvitation(any[String], any[String], any[DateTime])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))

    when(otRepositoryMock2.updateGroupExpiryTime(any[String], any[DateTime], any[String]))
      .thenReturn(Future.successful(()))
    when(appRepositoryMock.removeProgressStatuses(any[String], any[List[ProgressStatus]]))
      .thenReturn(Future.successful(()))
    when(otRepositoryMock2.phaseName).thenReturn("phase2")


    val phase2TestService = new Phase2TestService2 with StcEventServiceFixture {
      val appRepository = appRepositoryMock
      val cdRepository = cdRepositoryMock
      val testRepository = otRepositoryMock

      val testRepository2: Phase2TestRepository2 = otRepositoryMock2
      val integrationGatewayConfig: TestIntegrationGatewayConfig = integrationConfigMock

      val onlineTestsGatewayClient = onlineTestsGatewayClientMock
      val emailClient = emailClientMock
      val auditService = auditServiceMock
      val tokenFactory = tokenFactoryMock
      val dateTimeFactory = clock
      val eventService = eventServiceMock
      val actor = ActorSystem()
      val authProvider = authProviderClientMock
      val phase3TestService = phase3TestServiceMock
      val siftService = siftServiceMock
    }
  }
}
