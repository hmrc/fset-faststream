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
import connectors.ExchangeObjects.{ toString => _, _ }
import connectors.{ CSREmailClient, OnlineTestsGatewayClient }
import factories.{ DateTimeFactory, UUIDFactory }
import model.Commands.PostCode
import model.Exceptions._
import model.OnlineTestCommands.OnlineTestApplication
import model.Phase2TestExamples._
import model.ProgressStatuses.{ toString => _, _ }
import model._
import model.command.{ Phase2ProgressResponse, ProgressResponse }
import model.exchange.PsiRealTimeResults
import model.persisted.{ ContactDetails, Phase2TestGroup2, Phase2TestGroupWithAppId2, _ }
import org.joda.time.{ DateTime, DateTimeZone, LocalDate }
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.onlinetesting.{ Phase2TestRepository, Phase2TestRepository2 }
import services.AuditService
import services.onlinetesting.Exceptions.{ TestCancellationException, TestRegistrationException }
import services.onlinetesting.phase3.Phase3TestService
import services.sift.ApplicationSiftService
import services.stc.StcEventServiceFixture
import testkit.MockitoImplicits._
import testkit.{ ExtendedTimeout, UnitSpec }
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import scala.language.postfixOps

class Phase2TestService2Spec extends UnitSpec with ExtendedTimeout {

  "Verify access code" should {
    "return an invigilated test url for a valid candidate" in new Phase2TestServiceFixture {
      when(cdRepositoryMock.findUserIdByEmail(any[String])).thenReturnAsync(userId)

      val accessCode = "TEST-CODE"
      val phase2TestGroup = Phase2TestGroup2(
        expirationDate,
        List(phase2Test.copy(invigilatedAccessCode = Some(accessCode)))
      )
      when(otRepositoryMock2.getTestGroupByUserId(any[String])).thenReturnAsync(Some(phase2TestGroup))

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
      when(otRepositoryMock2.getTestGroupByUserId(any[String])).thenReturnAsync(Some(phase2TestGroup))

      val result = phase2TestService.verifyAccessCode("test-email.com", "I-DO-NOT-MATCH").failed.futureValue
      result mustBe an[InvalidTokenException]
    }

    "return a Failure if the user cannot be located by email" in new Phase2TestServiceFixture {
      when(cdRepositoryMock.findUserIdByEmail(any[String])).thenReturn(Future.failed(ContactDetailsNotFoundForEmail()))

      val result = phase2TestService.verifyAccessCode("test-email.com", "ANY-CODE").failed.futureValue
      result mustBe an[ContactDetailsNotFoundForEmail]
    }

    "return A Failure if the test is Expired" in new Phase2TestServiceFixture {
      when(cdRepositoryMock.findUserIdByEmail(any[String])).thenReturnAsync(authenticateUrl)

      val accessCode = "TEST-CODE"
      val phase2TestGroup = Phase2TestGroup2(
        expiredDate,
        List(phase2Test.copy(invigilatedAccessCode = Some(accessCode)))
      )
      when(otRepositoryMock2.getTestGroupByUserId(any[String])).thenReturnAsync(Some(phase2TestGroup))

      val result = phase2TestService.verifyAccessCode("test-email.com", accessCode).failed.futureValue
      result mustBe an[ExpiredTestForTokenException]
    }
  }

  "Invite applicants to PHASE 2" must {
    "successfully register 2 candidates" in new Phase2TestServiceFixture {
      when(otRepositoryMock2.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestProfile))
      when(otRepositoryMock2.insertOrUpdateTestGroup(any[String], any[Phase2TestGroup2])).thenReturnAsync()
      when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest]))
          .thenReturnAsync(aoa)

      phase2TestService.registerAndInvite(candidates).futureValue

      verify(onlineTestsGatewayClientMock, times(4)).psiRegisterApplicant(any[RegisterCandidateRequest])
      verify(otRepositoryMock2, times(4)).insertOrUpdateTestGroup(any[String], any[Phase2TestGroup2])
      verify(emailClientMock, times(2)).sendOnlineTestInvitation(any[String], any[String], any[DateTime])(any[HeaderCarrier])
    }

    "deal with a failed registration when registering a single candidate" in new Phase2TestServiceFixture {
      when(otRepositoryMock2.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestProfile))
      when(otRepositoryMock2.insertOrUpdateTestGroup(any[String], any[Phase2TestGroup2])).thenReturnAsync()
      when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest]))
          .thenReturn(Future.failed(new Exception("Dummy error for test")))

      phase2TestService.registerAndInvite(List(onlineTestApplication)).futureValue

      verify(onlineTestsGatewayClientMock, times(2)).psiRegisterApplicant(any[RegisterCandidateRequest])
      verify(otRepositoryMock2, never).insertOrUpdateTestGroup(any[String], any[Phase2TestGroup2])
      verify(emailClientMock, never).sendOnlineTestInvitation(any[String], any[String], any[DateTime])(any[HeaderCarrier])
    }

    "first candidate registers successfully, 2nd candidate fails" in new Phase2TestServiceFixture {
      when(otRepositoryMock2.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestProfile))
      when(otRepositoryMock2.insertOrUpdateTestGroup(any[String], any[Phase2TestGroup2])).thenReturnAsync()
      when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest]))
        .thenReturnAsync(aoa) // candidate 1 test 1
        .thenReturnAsync(aoa) // candidate 1 test 2
        .thenReturnAsync(aoa) // candidate 2 test 1
        .thenReturn(Future.failed(new Exception("Dummy error for test"))) // candidate 2 test 2

      phase2TestService.registerAndInvite(candidates).futureValue

      verify(onlineTestsGatewayClientMock, times(4)).psiRegisterApplicant(any[RegisterCandidateRequest])
      // Called 2 times for 1st candidate who registered successfully for both tests and once for 2nd candidate whose
      // 1st registration was successful only
      verify(otRepositoryMock2, times(3)).insertOrUpdateTestGroup(any[String], any[Phase2TestGroup2])
      // Called for 1st candidate only who registered successfully
      verify(emailClientMock, times(1)).sendOnlineTestInvitation(any[String], any[String], any[DateTime])(any[HeaderCarrier])
    }

    "first candidate fails registration, 2nd candidate is successful" in new Phase2TestServiceFixture {
      when(otRepositoryMock2.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestProfile))
      when(otRepositoryMock2.insertOrUpdateTestGroup(any[String], any[Phase2TestGroup2])).thenReturnAsync()
      when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest]))
        .thenReturnAsync(aoa) // candidate 1 test 1
        .thenReturn(Future.failed(new Exception("Dummy error for test"))) // candidate 1 test 2
        .thenReturnAsync(aoa) // candidate 2 test 1
        .thenReturnAsync(aoa) // candidate 2 test 2

      phase2TestService.registerAndInvite(candidates).futureValue

      verify(onlineTestsGatewayClientMock, times(4)).psiRegisterApplicant(any[RegisterCandidateRequest])
      // Called once for 1st candidate who registered successfully for 1st test only tests and twice for 2nd candidate whose
      // registrations were both successful
      verify(otRepositoryMock2, times(3)).insertOrUpdateTestGroup(any[String], any[Phase2TestGroup2])
      // Called for 2nd candidate only who registered successfully
      verify(emailClientMock, times(1)).sendOnlineTestInvitation(any[String], any[String], any[DateTime])(any[HeaderCarrier])
    }
  }

  "Reset tests" should {
    "throw exception if test group cannot be found" in new Phase2TestServiceFixture {
      when(otRepositoryMock2.getTestGroup(any[String])).thenReturnAsync(None)

      val result = phase2TestService.resetTest(onlineTestApplication, phase2Test.orderId, "")

      result.failed.futureValue mustBe an[CannotFindTestGroupByApplicationIdException]
    }

    "throw exception if test by orderId cannot be found" in new Phase2TestServiceFixture {
      val newTests = phase2Test.copy(orderId = "unknown-uuid") :: Nil
      when(otRepositoryMock2.getTestGroup(any[String]))
        .thenReturnAsync(Some(phase2TestProfile.copy(tests = newTests)))

      val result = phase2TestService.resetTest(onlineTestApplication, phase2Test.orderId, "")

      result.failed.futureValue mustBe an[CannotFindTestByOrderIdException]
    }

    // we are not sending a cancellation request anymore so this test should be ignored for now
    "not register candidate if cancellation request fails" ignore new Phase2TestServiceFixture {
      when(otRepositoryMock2.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestProfile))
      when(onlineTestsGatewayClientMock.psiCancelTest(any[CancelCandidateTestRequest]))
        .thenReturnAsync(acaError)

      val result = phase2TestService.resetTest(onlineTestApplication, phase2Test.orderId, "")

      result.failed.futureValue mustBe a[TestCancellationException]

      verify(onlineTestsGatewayClientMock, times(0)).psiRegisterApplicant(any[RegisterCandidateRequest])

      verify(auditServiceMock, times(0)).logEventNoRequest("TestCancelledForCandidate", auditDetails)
      verify(auditServiceMock, times(0)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(0)).logEventNoRequest("OnlineTestInvitationEmailSent", auditDetailsWithEmail)
      verify(auditServiceMock, times(0)).logEventNoRequest("OnlineTestInvited", auditDetails)
    }

    "throw exception if config cant be found" in new Phase2TestServiceFixture {
      val newTests = phase2Test.copy(inventoryId = "unknown-uuid") :: Nil
      when(otRepositoryMock2.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestProfile.copy(tests = newTests)))
      when(onlineTestsGatewayClientMock.psiCancelTest(any[CancelCandidateTestRequest]))
        .thenReturnAsync(acaCompleted)

      val result = phase2TestService.resetTest(onlineTestApplication, phase2Test.orderId, "")

      result.failed.futureValue mustBe a[CannotFindTestByInventoryIdException]
    }

    "not complete invitation if re-registration request connection fails"  in new Phase2TestServiceFixture {
      val newTests = phase2Test.copy(inventoryId = "inventory-id-1") :: Nil
      when(otRepositoryMock2.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestProfile.copy(tests = newTests)))
      when(onlineTestsGatewayClientMock.psiCancelTest(any[CancelCandidateTestRequest]))
        .thenReturnAsync(acaCompleted)
      when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest]))
        .thenReturn(Future.failed(new ConnectorException(connectorErrorMessage)))

      val result = phase2TestService.resetTest(onlineTestApplication, phase2Test.orderId, "")

      result.failed.futureValue mustBe a[ConnectorException]

      verify(onlineTestsGatewayClientMock, times(1)).psiRegisterApplicant(any[RegisterCandidateRequest])
      verify(emailClientMock, times(0))
        .sendOnlineTestInvitation(eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate))(any[HeaderCarrier])

      verify(auditServiceMock, times(0)).logEventNoRequest("TestCancelledForCandidate", auditDetails)
      verify(auditServiceMock, times(0)).logEventNoRequest("UserRegisteredForPhase2Test", auditDetails)
      verify(auditServiceMock, times(0)).logEventNoRequest("OnlineTestInvitationEmailSent", auditDetailsWithEmail)
      verify(auditServiceMock, times(0)).logEventNoRequest("OnlineTestInvited", auditDetails)
    }

    "not complete invitation if re-registration fails"  in new Phase2TestServiceFixture {
      val newTests = phase2Test.copy(inventoryId = "inventory-id-1") :: Nil
      when(otRepositoryMock2.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestProfile.copy(tests = newTests)))

      when(onlineTestsGatewayClientMock.psiCancelTest(any[CancelCandidateTestRequest]))
        .thenReturnAsync(acaCompleted)
      when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest]))
        .thenReturnAsync(aoaFailed)

      val result = phase2TestService.resetTest(onlineTestApplication, phase2Test.orderId, "")

      result.failed.futureValue mustBe a[TestRegistrationException]

      verify(onlineTestsGatewayClientMock, times(1)).psiRegisterApplicant(any[RegisterCandidateRequest])
      verify(emailClientMock, times(0))
        .sendOnlineTestInvitation(eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate))(any[HeaderCarrier])

      verify(auditServiceMock, times(0)).logEventNoRequest("TestCancelledForCandidate", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("UserRegisteredForPhase2Test", auditDetails)
      verify(auditServiceMock, times(0)).logEventNoRequest("OnlineTestInvitationEmailSent", auditDetailsWithEmail)
      verify(auditServiceMock, times(0)).logEventNoRequest("OnlineTestInvited", auditDetails)
    }

    "complete reset successfully" in new Phase2TestServiceFixture {
      val newTests = phase2Test.copy(inventoryId = "inventory-id-1") :: Nil
      when(otRepositoryMock2.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestProfile.copy(tests = newTests)))

      when(onlineTestsGatewayClientMock.psiCancelTest(any[CancelCandidateTestRequest]))
        .thenReturnAsync(acaCompleted)
      when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest]))
        .thenReturnAsync(aoa)

      val result = phase2TestService.resetTest(onlineTestApplication, phase2Test.orderId, "")

      result.futureValue mustBe unit

      verify(onlineTestsGatewayClientMock, times(1)).psiRegisterApplicant(any[RegisterCandidateRequest])
      verify(emailClientMock, times(1))
        .sendOnlineTestInvitation(eqTo(emailContactDetails), eqTo(onlineTestApplication.preferredName), eqTo(expirationDate))(any[HeaderCarrier])

      verify(auditServiceMock, times(0)).logEventNoRequest("TestCancelledForCandidate", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("UserRegisteredForPhase2Test", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationEmailSent", auditDetailsWithEmail)
    }
  }

  "mark as started" should {
    "change progress to started" in new Phase2TestServiceFixture {
      when(otRepositoryMock2.updateTestStartTime(any[String], any[DateTime])).thenReturnAsync()
      when(otRepositoryMock2.getTestProfileByOrderId(orderId))
        .thenReturnAsync(Phase2TestGroupWithAppId2("appId123", phase2TestProfile))
      when(otRepositoryMock2.updateProgressStatus("appId123", ProgressStatuses.PHASE2_TESTS_STARTED))
        .thenReturnAsync()
      phase2TestService.markAsStarted2(orderId).futureValue

      verify(otRepositoryMock2).updateProgressStatus("appId123", ProgressStatuses.PHASE2_TESTS_STARTED)
    }
  }

  "mark as completed" should {
    "change progress to completed if there are all tests completed" in new Phase2TestServiceFixture {
      when(otRepositoryMock2.updateTestCompletionTime2(any[String], any[DateTime])).thenReturnAsync()
      val phase2Tests = phase2TestProfile.copy(tests = phase2TestProfile.tests.map(t => t.copy(completedDateTime = Some(DateTime.now()))),
        expirationDate = DateTime.now().plusDays(2)
      )

      when(otRepositoryMock2.getTestProfileByOrderId(orderId))
        .thenReturnAsync(Phase2TestGroupWithAppId2("appId123", phase2Tests))
      when(otRepositoryMock2.updateProgressStatus("appId123", ProgressStatuses.PHASE2_TESTS_COMPLETED))
        .thenReturnAsync()

      phase2TestService.markAsCompleted2(orderId).futureValue

      verify(otRepositoryMock2).updateProgressStatus("appId123", ProgressStatuses.PHASE2_TESTS_COMPLETED)
    }
  }

  "processNextExpiredTest" should {
    "do nothing if there is no expired application to process" in new Phase2TestServiceFixture {
      when(otRepositoryMock2.nextExpiringApplication(Phase2ExpirationEvent)).thenReturnAsync(None)
      phase2TestService.processNextExpiredTest(Phase2ExpirationEvent).futureValue mustBe unit
    }

    "update progress status and send an email to the user when a Faststream application is expired" in new Phase2TestServiceFixture {
      when(otRepositoryMock2.nextExpiringApplication(Phase2ExpirationEvent))
        .thenReturnAsync(Some(expiredApplication))
      when(cdRepositoryMock.find(any[String])).thenReturnAsync(contactDetails)
      when(appRepositoryMock.getApplicationRoute(any[String])).thenReturnAsync(ApplicationRoute.Faststream)

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
        .thenReturnAsync(Some(expiredApplication))
      when(cdRepositoryMock.find(any[String])).thenReturnAsync(contactDetails)
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

  "store real time results" should {
    "handle not finding an application for the given order id" in new Phase2TestServiceFixture {
      when(otRepositoryMock2.getApplicationIdForOrderId(any[String], any[String])).thenReturnAsync(None)

      val result = phase2TestService.storeRealTimeResults(orderId, realTimeResults)

      val exception = result.failed.futureValue
      exception mustBe an[CannotFindTestByOrderIdException]
      exception.getMessage mustBe s"Application not found for test for orderId=$orderId"
    }

    "handle not finding a test profile for the given order id" in new Phase2TestServiceFixture {
      when(otRepositoryMock2.getApplicationIdForOrderId(any[String], any[String])).thenReturnAsync(Some(applicationId))

      when(otRepositoryMock2.getTestProfileByOrderId(any[String])).thenReturn(Future.failed(
        CannotFindTestByOrderIdException(s"Cannot find test group by orderId=$orderId")
      ))

      val result = phase2TestService.storeRealTimeResults(orderId, realTimeResults)

      val exception = result.failed.futureValue
      exception mustBe an[CannotFindTestByOrderIdException]
      exception.getMessage mustBe s"Cannot find test group by orderId=$orderId"
    }

    "handle not finding the test group when checking to update the progress status" in new Phase2TestServiceFixture {
      when(otRepositoryMock2.getApplicationIdForOrderId(any[String], any[String])).thenReturnAsync(Some(applicationId))

      when(otRepositoryMock2.getTestProfileByOrderId(any[String])).thenReturnAsync(phase2CompletedTestGroupWithAppId)
      when(otRepositoryMock2.insertTestResult2(any[String], any[PsiTest], any[model.persisted.PsiTestResult])).thenReturnAsync()
      when(otRepositoryMock2.getTestGroup(any[String])).thenReturnAsync(None)

      val result = phase2TestService.storeRealTimeResults(orderId, realTimeResults)

      val exception = result.failed.futureValue
      exception mustBe an[Exception]
      exception.getMessage mustBe s"No test profile returned for $applicationId"

      verify(otRepositoryMock2, never()).updateTestCompletionTime2(any[String], any[DateTime])
      verify(otRepositoryMock2, never()).updateProgressStatus(any[String], any[ProgressStatuses.ProgressStatus])
    }

    "process the real time results and update the progress status" in new Phase2TestServiceFixture {
      when(otRepositoryMock2.getApplicationIdForOrderId(any[String], any[String])).thenReturnAsync(Some(applicationId))

      when(otRepositoryMock2.getTestProfileByOrderId(any[String])).thenReturnAsync(phase2CompletedTestGroupWithAppId)
      when(otRepositoryMock2.insertTestResult2(any[String], any[PsiTest], any[model.persisted.PsiTestResult])).thenReturnAsync()

      val phase2TestGroup2 = Phase2TestGroup2(expirationDate = now, tests = List(fifthPsiTest, sixthPsiTest))
      when(otRepositoryMock2.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestGroup2))
      when(otRepositoryMock2.updateProgressStatus(any[String], any[ProgressStatuses.ProgressStatus])).thenReturnAsync()

      phase2TestService.storeRealTimeResults(orderId, realTimeResults).futureValue

      verify(otRepositoryMock2, never()).updateTestCompletionTime2(any[String], any[DateTime])
      verify(otRepositoryMock2, times(1)).updateProgressStatus(any[String],
        eqTo(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED))
    }

    "process the real time results, mark the test as completed and update the progress status" in new Phase2TestServiceFixture {
      when(otRepositoryMock2.getApplicationIdForOrderId(any[String], any[String])).thenReturnAsync(Some(applicationId))

      //First call return tests that are not completed second call return tests that are are completed
      when(otRepositoryMock2.getTestProfileByOrderId(any[String]))
        .thenReturnAsync(phase2NotCompletedTestGroupWithAppId)
        .thenReturnAsync(phase2CompletedTestGroupWithAppId)

      when(otRepositoryMock2.insertTestResult2(any[String], any[PsiTest], any[model.persisted.PsiTestResult])).thenReturnAsync()
      when(otRepositoryMock2.updateTestCompletionTime2(any[String], any[DateTime])).thenReturnAsync()

      when(otRepositoryMock2.updateProgressStatus(any[String], eqTo(ProgressStatuses.PHASE2_TESTS_COMPLETED))).thenReturnAsync()

      val phase2TestGroup2 = Phase2TestGroup2(expirationDate = now, tests = List(fifthPsiTest, sixthPsiTest))
      val phase2TestsCompleted: Phase2TestGroup2 = phase2TestGroup2.copy(
        tests = phase2TestGroup2.tests.map(t => t.copy(orderId = orderId, completedDateTime = Some(DateTime.now()))),
        expirationDate = DateTime.now().plusDays(2)
      )

      when(otRepositoryMock2.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestsCompleted))
      when(otRepositoryMock2.updateProgressStatus(any[String], eqTo(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED))).thenReturnAsync()

      phase2TestService.storeRealTimeResults(orderId, realTimeResults).futureValue

      verify(otRepositoryMock2, times(1)).updateTestCompletionTime2(any[String], any[DateTime])
      verify(otRepositoryMock2, times(1)).updateProgressStatus(any[String], eqTo(ProgressStatuses.PHASE2_TESTS_COMPLETED))
      verify(otRepositoryMock2, times(1)).updateProgressStatus(any[String], eqTo(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED))
    }
  }

  private def phase2Progress(phase2ProgressResponse: Phase2ProgressResponse) =
    ProgressResponse("appId", phase2ProgressResponse = phase2ProgressResponse)

  trait Phase2TestServiceFixture {

    implicit val hc: HeaderCarrier = mock[HeaderCarrier]
    implicit val rh: RequestHeader = mock[RequestHeader]

    val clock: DateTimeFactory = mock[DateTimeFactory]
    implicit val now: DateTime = DateTimeFactory.nowLocalTimeZone.withZone(DateTimeZone.UTC)
    when(clock.nowLocalTimeZone).thenReturn(now)

    val scheduleCompletionBaseUrl = "http://localhost:9284/fset-fast-stream/online-tests/phase2"
    val inventoryIds: Map[String, String] = Map[String, String]("test3" -> "test3-uuid", "test4" -> "test4-uuid")

    def testIds(idx: Int): PsiTestIds =
      PsiTestIds(s"inventory-id-$idx", s"assessment-id-$idx", s"report-id-$idx", s"norm-id-$idx")

    val tests = Map[String, PsiTestIds](
      "test1" -> testIds(1),
      "test2" -> testIds(2),
      "test3" -> testIds(3),
      "test4" -> testIds(4)
    )

    val mockPhase1TestConfig = Phase1TestsConfig2(
      expiryTimeInDays = 5, testRegistrationDelayInSecs = 1, tests, standard = List("test1", "test2", "test3", "test4"),
      gis = List("test1", "test4")
    )

    val mockPhase2TestConfig = Phase2TestsConfig2(
      expiryTimeInDays = 5, expiryTimeInDaysForInvigilatedETray = 90, testRegistrationDelayInSecs = 1, tests, standard = List("test1", "test2")
    )

    val mockNumericalTestsConfig2 = NumericalTestsConfig2(tests, List("test1"))
    val integrationConfigMock = TestIntegrationGatewayConfig(
      url = "",
      phase1Tests = mockPhase1TestConfig,
      phase2Tests = mockPhase2TestConfig,
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

    val aoaFailed = AssessmentOrderAcknowledgement(
      customerId = "cust-id", receiptId = "receipt-id", orderId = orderId, testLaunchUrl = authenticateUrl,
      status = AssessmentOrderAcknowledgement.errorStatus, statusDetails = "", statusDate = LocalDate.now())

    val acaCompleted = AssessmentCancelAcknowledgementResponse(
      AssessmentCancelAcknowledgementResponse.completedStatus,
      "Everything is fine!", statusDate = LocalDate.now()
    )

    val acaError = AssessmentCancelAcknowledgementResponse(
      AssessmentCancelAcknowledgementResponse.errorStatus,
      "Something went wrong!", LocalDate.now()
    )

    val postcode : Option[PostCode]= Some("WC2B 4")
    val emailContactDetails = "emailfjjfjdf@mailinator.com"
    val contactDetails = ContactDetails(outsideUk = false, Address("Aldwych road"), postcode, Some("UK"), emailContactDetails, "111111")

    val connectorErrorMessage = "Error in connector"
    val auditDetails = Map("userId" -> userId)
    val auditDetailsWithEmail = auditDetails + ("email" -> emailContactDetails)

    val success = Future.successful(unit)
    val appRepositoryMock = mock[GeneralApplicationRepository]
    val cdRepositoryMock = mock[ContactDetailsRepository]
    val otRepositoryMock = mock[Phase2TestRepository]
    val otRepositoryMock2 = mock[Phase2TestRepository2]
    val onlineTestsGatewayClientMock = mock[OnlineTestsGatewayClient]
    val emailClientMock = mock[CSREmailClient]
    val auditServiceMock = mock[AuditService]
    val tokenFactoryMock = mock[UUIDFactory]
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
      lastName = "Prime1",
      None,
      None
    )

    val preferredNameSanitized = "Preferred Name"
    val lastName = ""
    val onlineTestApplication2 = onlineTestApplication.copy(applicationId = "appId2", userId = "userId2", lastName = "Prime2")
    val adjustmentApplication = onlineTestApplication.copy(applicationId = "appId3", userId = "userId3", needsOnlineAdjustments = true)
    val adjustmentApplication2 = onlineTestApplication.copy(applicationId = "appId4", userId = "userId4", needsOnlineAdjustments = true)
    val candidates = List(onlineTestApplication, onlineTestApplication2)

    def uuid: String = UUIDFactory.generateUUID()

    val phase2Test = PsiTest(
      inventoryId = uuid, orderId = uuid, assessmentId = uuid, reportId = uuid, normId = uuid, usedForResults = true,
      testUrl = authenticateUrl, invitationDate = invitationDate
    )

    val phase2TestProfile = Phase2TestGroup2(expirationDate,
      List(phase2Test, phase2Test.copy(inventoryId = uuid))
    )

    val phase2CompletedTestGroupWithAppId: Phase2TestGroupWithAppId2 = Phase2TestGroupWithAppId2(
      applicationId,
      testGroup = phase2TestProfile.copy(
        tests = phase2TestProfile.tests.map( t =>
          t.copy(orderId = orderId, completedDateTime = Some(DateTime.now()))
        )
      )
    )

    val phase2NotCompletedTestGroupWithAppId: Phase2TestGroupWithAppId2 = Phase2TestGroupWithAppId2(
      applicationId,
      testGroup = phase2TestProfile.copy(
        tests = phase2TestProfile.tests.map( t =>
          t.copy(orderId = orderId, completedDateTime = None)
        )
      )
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
        .thenReturnAsync()

    when(otRepositoryMock2.getTestGroup(any[String]))
      .thenReturnAsync(Some(phase2TestProfile))

    when(otRepositoryMock2.resetTestProfileProgresses(any[String], any[List[ProgressStatus]]))
      .thenReturnAsync()

//    when(cdRepositoryMock.find(any[String])).thenReturn(Future.successful(
//      ContactDetails(outsideUk = false, Address("Aldwych road"), Some("QQ1 1QQ"), Some("UK"), "email@test.com", "111111")))

    when(cdRepositoryMock.find(any[String])).thenReturnAsync(contactDetails)

    when(emailClientMock.sendOnlineTestInvitation(any[String], any[String], any[DateTime])(any[HeaderCarrier]))
        .thenReturnAsync()

    when(otRepositoryMock2.updateGroupExpiryTime(any[String], any[DateTime], any[String]))
      .thenReturnAsync()
    when(appRepositoryMock.removeProgressStatuses(any[String], any[List[ProgressStatus]]))
      .thenReturnAsync()
    when(otRepositoryMock2.phaseName).thenReturn("phase2")

    val realTimeResults = PsiRealTimeResults(tScore = 10.0, rawScore = 20.0, reportUrl = None)

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
