/*
 * Copyright 2022 HM Revenue & Customs
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
import connectors.{ OnlineTestEmailClient, OnlineTestsGatewayClient }
import factories.{ DateTimeFactory, DateTimeFactoryMock, UUIDFactory }
import model.Commands.PostCode
import model.Exceptions._
import model.OnlineTestCommands.OnlineTestApplication
import model.Phase2TestExamples._
import model.ProgressStatuses.{ toString => _, _ }
import model._
import model.command.{ Phase2ProgressResponse, ProgressResponse }
import model.exchange.PsiRealTimeResults
import model.persisted.{ ContactDetails, Phase2TestGroup, Phase2TestGroupWithAppId, _ }
import org.joda.time.{ DateTime, DateTimeZone, LocalDate }
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.onlinetesting.Phase2TestRepository
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

class Phase2TestServiceSpec extends UnitSpec with ExtendedTimeout {

  "Verify access code" should {
    "return an invigilated test url for a valid candidate" in new TestFixture {
      when(cdRepositoryMock.findUserIdByEmail(any[String])).thenReturnAsync(userId)

      val accessCode = "TEST-CODE"
      val phase2TestGroup = Phase2TestGroup(
        expirationDate,
        List(phase2Test.copy(invigilatedAccessCode = Some(accessCode)))
      )
      when(phase2TestRepositoryMock.getTestGroupByUserId(any[String])).thenReturnAsync(Some(phase2TestGroup))

      val result = phase2TestService.verifyAccessCode("test-email.com", accessCode).futureValue
      result mustBe authenticateUrl
    }

    "return a Failure if the access code does not match" in new TestFixture {
      when(cdRepositoryMock.findUserIdByEmail(any[String])).thenReturn(Future.successful(authenticateUrl))

      val accessCode = "TEST-CODE"
      val phase2TestGroup = Phase2TestGroup(
        expirationDate,
        phase2Test.copy(invigilatedAccessCode = Some(accessCode)) :: Nil
      )
      when(phase2TestRepositoryMock.getTestGroupByUserId(any[String])).thenReturnAsync(Some(phase2TestGroup))

      val result = phase2TestService.verifyAccessCode("test-email.com", "I-DO-NOT-MATCH").failed.futureValue
      result mustBe an[InvalidTokenException]
    }

    "return a Failure if the user cannot be located by email" in new TestFixture {
      when(cdRepositoryMock.findUserIdByEmail(any[String])).thenReturn(Future.failed(ContactDetailsNotFoundForEmail()))

      val result = phase2TestService.verifyAccessCode("test-email.com", "ANY-CODE").failed.futureValue
      result mustBe an[ContactDetailsNotFoundForEmail]
    }

    "return A Failure if the test is Expired" in new TestFixture {
      when(cdRepositoryMock.findUserIdByEmail(any[String])).thenReturnAsync(authenticateUrl)

      val accessCode = "TEST-CODE"
      val phase2TestGroup = Phase2TestGroup(
        expiredDate,
        List(phase2Test.copy(invigilatedAccessCode = Some(accessCode)))
      )
      when(phase2TestRepositoryMock.getTestGroupByUserId(any[String])).thenReturnAsync(Some(phase2TestGroup))

      val result = phase2TestService.verifyAccessCode("test-email.com", accessCode).failed.futureValue
      result mustBe an[ExpiredTestForTokenException]
    }
  }

  "Invite applicants to PHASE 2" must {
    "successfully register 2 candidates" in new TestFixture {
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestProfile))
      when(phase2TestRepositoryMock.insertOrUpdateTestGroup(any[String], any[Phase2TestGroup])).thenReturnAsync()
      when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest]))
          .thenReturnAsync(aoa)

      phase2TestService.registerAndInvite(candidates).futureValue

      verify(onlineTestsGatewayClientMock, times(4)).psiRegisterApplicant(any[RegisterCandidateRequest])
      verify(phase2TestRepositoryMock, times(4)).insertOrUpdateTestGroup(any[String], any[Phase2TestGroup])
      verify(emailClientMock, times(2)).sendOnlineTestInvitation(any[String], any[String], any[DateTime])(any[HeaderCarrier])
    }

    "deal with a failed registration when registering a single candidate" in new TestFixture {
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestProfile))
      when(phase2TestRepositoryMock.insertOrUpdateTestGroup(any[String], any[Phase2TestGroup])).thenReturnAsync()
      when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest]))
          .thenReturn(Future.failed(new Exception("Dummy error for test")))

      phase2TestService.registerAndInvite(List(onlineTestApplication)).futureValue

      verify(onlineTestsGatewayClientMock, times(2)).psiRegisterApplicant(any[RegisterCandidateRequest])
      verify(phase2TestRepositoryMock, never).insertOrUpdateTestGroup(any[String], any[Phase2TestGroup])
      verify(emailClientMock, never).sendOnlineTestInvitation(any[String], any[String], any[DateTime])(any[HeaderCarrier])
    }

    "first candidate registers successfully, 2nd candidate fails" in new TestFixture {
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestProfile))
      when(phase2TestRepositoryMock.insertOrUpdateTestGroup(any[String], any[Phase2TestGroup])).thenReturnAsync()
      when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest]))
        .thenReturnAsync(aoa) // candidate 1 test 1
        .thenReturnAsync(aoa) // candidate 1 test 2
        .thenReturnAsync(aoa) // candidate 2 test 1
        .thenReturn(Future.failed(new Exception("Dummy error for test"))) // candidate 2 test 2

      phase2TestService.registerAndInvite(candidates).futureValue

      verify(onlineTestsGatewayClientMock, times(4)).psiRegisterApplicant(any[RegisterCandidateRequest])
      // Called 2 times for 1st candidate who registered successfully for both tests and once for 2nd candidate whose
      // 1st registration was successful only
      verify(phase2TestRepositoryMock, times(3)).insertOrUpdateTestGroup(any[String], any[Phase2TestGroup])
      // Called for 1st candidate only who registered successfully
      verify(emailClientMock, times(1)).sendOnlineTestInvitation(any[String], any[String], any[DateTime])(any[HeaderCarrier])
    }

    "first candidate fails registration, 2nd candidate is successful" in new TestFixture {
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestProfile))
      when(phase2TestRepositoryMock.insertOrUpdateTestGroup(any[String], any[Phase2TestGroup])).thenReturnAsync()
      when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest]))
        .thenReturnAsync(aoa) // candidate 1 test 1
        .thenReturn(Future.failed(new Exception("Dummy error for test"))) // candidate 1 test 2
        .thenReturnAsync(aoa) // candidate 2 test 1
        .thenReturnAsync(aoa) // candidate 2 test 2

      phase2TestService.registerAndInvite(candidates).futureValue

      verify(onlineTestsGatewayClientMock, times(4)).psiRegisterApplicant(any[RegisterCandidateRequest])
      // Called once for 1st candidate who registered successfully for 1st test only tests and twice for 2nd candidate whose
      // registrations were both successful
      verify(phase2TestRepositoryMock, times(3)).insertOrUpdateTestGroup(any[String], any[Phase2TestGroup])
      // Called for 2nd candidate only who registered successfully
      verify(emailClientMock, times(1)).sendOnlineTestInvitation(any[String], any[String], any[DateTime])(any[HeaderCarrier])
    }
  }

  "processNextExpiredTest" should {
    val phase2ExpirationEvent = Phase2ExpirationEvent(gracePeriodInSecs = 0)

    "do nothing if there is no expired application to process" in new TestFixture {
      when(phase2TestRepositoryMock.nextExpiringApplication(phase2ExpirationEvent)).thenReturnAsync(None)
      phase2TestService.processNextExpiredTest(phase2ExpirationEvent).futureValue mustBe unit
    }

    "update progress status and send an email to the user when a Faststream application is expired" in new TestFixture {
      when(phase2TestRepositoryMock.nextExpiringApplication(phase2ExpirationEvent))
        .thenReturnAsync(Some(expiredApplication))
      when(cdRepositoryMock.find(any[String])).thenReturnAsync(contactDetails)
      when(appRepositoryMock.getApplicationRoute(any[String])).thenReturnAsync(ApplicationRoute.Faststream)

      val results = List(SchemeEvaluationResult("Commercial", "Green"))

      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatuses.ProgressStatus])).thenReturn(success)

      when(emailClientMock.sendEmailWithName(any[String], any[String], any[String])(any[HeaderCarrier])).thenReturn(success)

      val result = phase2TestService.processNextExpiredTest(phase2ExpirationEvent)

      result.futureValue mustBe unit

      verify(cdRepositoryMock).find(userId)
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(applicationId, PHASE2_TESTS_EXPIRED)
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(applicationId, SIFT_ENTERED)
      verify(appRepositoryMock, never()).getCurrentSchemeStatus(applicationId)
      verify(appRepositoryMock, never()).updateCurrentSchemeStatus(applicationId, results)
      verify(siftServiceMock, never()).sendSiftEnteredNotification(eqTo(applicationId), any[DateTime])(any[HeaderCarrier])
      verify(emailClientMock).sendEmailWithName(emailContactDetails, preferredName, TestExpirationEmailTemplates.phase2ExpirationTemplate)
    }

    "update progress status and send an email to the user when an sdip faststream application is expired" in new TestFixture {
      when(phase2TestRepositoryMock.nextExpiringApplication(phase2ExpirationEvent))
        .thenReturnAsync(Some(expiredApplication))
      when(cdRepositoryMock.find(any[String])).thenReturnAsync(contactDetails)
      when(appRepositoryMock.getApplicationRoute(any[String])).thenReturn(Future.successful(ApplicationRoute.SdipFaststream))

      val results = List(SchemeEvaluationResult("Sdip", "Green"), SchemeEvaluationResult("Commercial", "Green"))

      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatuses.ProgressStatus])).thenReturn(success)
      when(emailClientMock.sendEmailWithName(any[String], any[String], any[String])(any[HeaderCarrier])).thenReturn(success)

      val result = phase2TestService.processNextExpiredTest(phase2ExpirationEvent)

      result.futureValue mustBe unit

      verify(cdRepositoryMock).find(userId)
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(applicationId, PHASE2_TESTS_EXPIRED)
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(applicationId, SIFT_ENTERED)
      verify(appRepositoryMock, never()).getCurrentSchemeStatus(applicationId)
      verify(appRepositoryMock, never()).updateCurrentSchemeStatus(applicationId, results)
      verify(siftServiceMock, never()).sendSiftEnteredNotification(eqTo(applicationId), any[DateTime])(any[HeaderCarrier])
      verify(emailClientMock).sendEmailWithName(emailContactDetails, preferredName, TestExpirationEmailTemplates.phase2ExpirationTemplate)
    }
  }

  "mark as started" should {
    "change progress to started" in new TestFixture {
      when(phase2TestRepositoryMock.updateTestStartTime(any[String], any[DateTime])).thenReturnAsync()
      when(phase2TestRepositoryMock.getTestProfileByOrderId(orderId))
        .thenReturnAsync(Phase2TestGroupWithAppId("appId123", phase2TestProfile))
      when(phase2TestRepositoryMock.updateProgressStatus("appId123", ProgressStatuses.PHASE2_TESTS_STARTED))
        .thenReturnAsync()
      when(appRepositoryMock.getProgressStatusTimestamps(anyString())).thenReturnAsync(Nil)

      phase2TestService.markAsStarted2(orderId).futureValue

      verify(phase2TestRepositoryMock, times(1)).updateProgressStatus("appId123", ProgressStatuses.PHASE2_TESTS_STARTED)
    }

    //TODO: add back in at end of campaign 2019
    "not change progress to started if status exists" ignore new TestFixture {
      when(phase2TestRepositoryMock.updateTestStartTime(any[String], any[DateTime])).thenReturnAsync()
      when(phase2TestRepositoryMock.getTestProfileByOrderId(orderId))
        .thenReturnAsync(Phase2TestGroupWithAppId("appId123", phase2TestProfile))
      when(phase2TestRepositoryMock.updateProgressStatus("appId123", ProgressStatuses.PHASE2_TESTS_STARTED))
        .thenReturnAsync()
      when(appRepositoryMock.getProgressStatusTimestamps(anyString()))
        .thenReturnAsync(List(("FAKE_STATUS", DateTime.now()), ("PHASE2_TESTS_STARTED", DateTime.now())))

      phase2TestService.markAsStarted2(orderId).futureValue

      verify(phase2TestRepositoryMock, never()).updateProgressStatus("appId123", ProgressStatuses.PHASE2_TESTS_STARTED)
    }
  }

  "mark as completed" should {
    "change progress to completed if there are all tests completed" in new TestFixture {
      when(phase2TestRepositoryMock.updateTestCompletionTime2(any[String], any[DateTime])).thenReturnAsync()
      val phase2Tests = phase2TestProfile.copy(tests = phase2TestProfile.tests.map(t => t.copy(completedDateTime = Some(DateTime.now()))),
        expirationDate = DateTime.now().plusDays(2)
      )

      when(phase2TestRepositoryMock.getTestProfileByOrderId(orderId))
        .thenReturnAsync(Phase2TestGroupWithAppId("appId123", phase2Tests))
      when(phase2TestRepositoryMock.updateProgressStatus("appId123", ProgressStatuses.PHASE2_TESTS_COMPLETED))
        .thenReturnAsync()

      phase2TestService.markAsCompleted2(orderId).futureValue

      verify(phase2TestRepositoryMock).updateProgressStatus("appId123", ProgressStatuses.PHASE2_TESTS_COMPLETED)
    }
  }

  "Reset tests" should {
    "throw exception if test group cannot be found" in new TestFixture {
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(None)

      val result = phase2TestService.resetTest(onlineTestApplication, phase2Test.orderId, "")

      result.failed.futureValue mustBe an[CannotFindTestGroupByApplicationIdException]
    }

    "throw exception if test by orderId cannot be found" in new TestFixture {
      val newTests = phase2Test.copy(orderId = "unknown-uuid") :: Nil
      when(phase2TestRepositoryMock.getTestGroup(any[String]))
        .thenReturnAsync(Some(phase2TestProfile.copy(tests = newTests)))

      val result = phase2TestService.resetTest(onlineTestApplication, phase2Test.orderId, "")

      result.failed.futureValue mustBe an[CannotFindTestByOrderIdException]
    }

    // we are not sending a cancellation request anymore so this test should be ignored for now
    "not register candidate if cancellation request fails" ignore new TestFixture {
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestProfile))
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

    "throw exception if config can't be found" in new TestFixture {
      val newTests = phase2Test.copy(inventoryId = "unknown-uuid") :: Nil
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestProfile.copy(tests = newTests)))
      when(onlineTestsGatewayClientMock.psiCancelTest(any[CancelCandidateTestRequest]))
        .thenReturnAsync(acaCompleted)

      val result = phase2TestService.resetTest(onlineTestApplication, phase2Test.orderId, "")

      result.failed.futureValue mustBe a[CannotFindTestByInventoryIdException]
    }

    "not complete invitation if re-registration request connection fails"  in new TestFixture {
      val newTests = phase2Test.copy(inventoryId = "inventory-id-1") :: Nil
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestProfile.copy(tests = newTests)))
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

    "not complete invitation if re-registration fails"  in new TestFixture {
      val newTests = phase2Test.copy(inventoryId = "inventory-id-1") :: Nil
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestProfile.copy(tests = newTests)))

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

    "complete reset successfully" in new TestFixture {
      val newTests = phase2Test.copy(inventoryId = "inventory-id-1") :: Nil
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestProfile.copy(tests = newTests)))

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

  "Extend time for expired test" should {
    val progress = phase2Progress(
      Phase2ProgressResponse(
        phase2TestsExpired = true,
        phase2TestsFirstReminder = true,
        phase2TestsSecondReminder = true
      ))

    "extend the test to 5 days from now and remove: expired and two reminder progress statuses" in new TestFixture {
      when(appRepositoryMock.findProgress(any[String])).thenReturn(Future.successful(progress))
      val phase2TestProfileWithExpirationInPast = Phase2TestGroup(now.minusDays(1), List(phase2Test))
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase2TestProfileWithExpirationInPast)))

      phase2TestService.extendTestGroupExpiryTime("appId", 5, "admin").futureValue

      verify(phase2TestRepositoryMock).updateGroupExpiryTime("appId", now.plusDays(5), "phase2")
      verify(appRepositoryMock).removeProgressStatuses("appId", List(
        PHASE2_TESTS_EXPIRED, PHASE2_TESTS_SECOND_REMINDER, PHASE2_TESTS_FIRST_REMINDER)
      )
    }

    "extend the test to 3 days from now and remove: expired and only one reminder progress status" in new TestFixture {
      when(appRepositoryMock.findProgress(any[String])).thenReturn(Future.successful(progress))
      val phase2TestProfileWithExpirationInPast = Phase2TestGroup(now.minusDays(1), List(phase2Test))
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase2TestProfileWithExpirationInPast)))

      phase2TestService.extendTestGroupExpiryTime("appId", 3, "admin").futureValue

      verify(phase2TestRepositoryMock).updateGroupExpiryTime("appId", now.plusDays(3), "phase2")
      verify(appRepositoryMock).removeProgressStatuses("appId", List(
        PHASE2_TESTS_EXPIRED, PHASE2_TESTS_SECOND_REMINDER)
      )
    }

    "extend the test to 1 day from now and remove: expired progress status" in new TestFixture {
      when(appRepositoryMock.findProgress(any[String])).thenReturn(Future.successful(progress))
      val phase2TestProfileWithExpirationInPast = Phase2TestGroup(now.minusDays(1), List(phase2Test))
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase2TestProfileWithExpirationInPast)))

      phase2TestService.extendTestGroupExpiryTime("appId", 1, "admin").futureValue

      verify(phase2TestRepositoryMock).updateGroupExpiryTime("appId", now.plusDays(1), "phase2")
      verify(appRepositoryMock).removeProgressStatuses("appId", List(PHASE2_TESTS_EXPIRED))
    }
  }

  "Extend time for test which has not expired yet" should {
    "extend the test to 5 days from expiration date which is in 1 day, remove two reminder progresses" in new TestFixture {
      val progress = phase2Progress(
        Phase2ProgressResponse(
          phase2TestsFirstReminder = true,
          phase2TestsSecondReminder = true
        ))

      when(appRepositoryMock.findProgress(any[String])).thenReturn(Future.successful(progress))
      val phase2TestProfileWithExpirationInPast = Phase2TestGroup(now.plusDays(1), List(phase2Test))
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase2TestProfileWithExpirationInPast)))

      phase2TestService.extendTestGroupExpiryTime("appId", 5, "admin").futureValue

      verify(phase2TestRepositoryMock).updateGroupExpiryTime("appId", now.plusDays(6), "phase2")
      verify(appRepositoryMock).removeProgressStatuses("appId", List(
        PHASE2_TESTS_SECOND_REMINDER, PHASE2_TESTS_FIRST_REMINDER)
      )
    }

    "extend the test to 2 days from expiration date which is in 1 day, remove one reminder progress" in new TestFixture {
      val progress = phase2Progress(
        Phase2ProgressResponse(
          phase2TestsFirstReminder = true,
          phase2TestsSecondReminder = true
        ))

      when(appRepositoryMock.findProgress(any[String])).thenReturn(Future.successful(progress))
      val phase2TestProfileWithExpirationInPast = Phase2TestGroup(now.plusDays(1), List(phase2Test))
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase2TestProfileWithExpirationInPast)))

      phase2TestService.extendTestGroupExpiryTime("appId", 2, "admin").futureValue

      val newExpirationDate = now.plusDays(3)
      verify(phase2TestRepositoryMock).updateGroupExpiryTime("appId", newExpirationDate, "phase2")
      verify(appRepositoryMock).removeProgressStatuses("appId", List(PHASE2_TESTS_SECOND_REMINDER))
    }

    "extend the test to 1 day from expiration date which is set to today, does not remove any progresses" in new TestFixture {
      val progress = phase2Progress(
        Phase2ProgressResponse(
          phase2TestsFirstReminder = true,
          phase2TestsSecondReminder = true
        ))

      when(appRepositoryMock.findProgress(any[String])).thenReturn(Future.successful(progress))
      val phase2TestProfileWithExpirationInPast = Phase2TestGroup(now, List(phase2Test))
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase2TestProfileWithExpirationInPast)))

      phase2TestService.extendTestGroupExpiryTime("appId", 1, "admin").futureValue

      val newExpirationDate = now.plusDays(1)
      verify(phase2TestRepositoryMock).updateGroupExpiryTime("appId", newExpirationDate, "phase2")
      verify(appRepositoryMock, never).removeProgressStatuses(any[String], any[List[ProgressStatus]])
    }
  }

  "build time adjustments" should {
    "return Nil when there is no need for adjustments and no gis" in new TestFixture {
      val onlineTestApplicationWithNoAdjustments = OnlineTestApplication("appId1", "PHASE1_TESTS", "userId1", "testAccountId",
        guaranteedInterview = false, needsOnlineAdjustments = false, needsAtVenueAdjustments = false, preferredName = "PrefName1",
        lastName = "LastName1", eTrayAdjustments = None, videoInterviewAdjustments = None)
      val result = phase2TestService.buildTimeAdjustments(5, onlineTestApplicationWithNoAdjustments)
      result mustBe List()
    }

    "return time adjustments when gis" in new TestFixture {
      val onlineTestApplicationGisWithAdjustments = OnlineTestApplication("appId1", "PHASE1_TESTS", "userId1", "testAccountId",
        guaranteedInterview = true, needsOnlineAdjustments = false, needsAtVenueAdjustments = false, preferredName = "PrefName1",
        lastName = "LastName1", eTrayAdjustments = Some(AdjustmentDetail(Some(25), None, None)), videoInterviewAdjustments = None)
      val result = phase2TestService.buildTimeAdjustments(5, onlineTestApplicationGisWithAdjustments)
      result mustBe List(TimeAdjustments(5, 1, 100))
    }

    "return time adjustments when adjustments needed" in new TestFixture {
      val onlineTestApplicationGisWithAdjustments = OnlineTestApplication("appId1", "PHASE1_TESTS", "userId1", "testAccountId",
        guaranteedInterview = false, needsOnlineAdjustments = true, needsAtVenueAdjustments = false, preferredName = "PrefName1",
        lastName = "LastName1", eTrayAdjustments = Some(AdjustmentDetail(Some(50), None, None)), videoInterviewAdjustments = None)
      val result = phase2TestService.buildTimeAdjustments(5, onlineTestApplicationGisWithAdjustments)
      result mustBe List(TimeAdjustments(5, 1, 120))
    }
  }

  "calculate absolute time with adjustments" should {
    "return 140 when adjustment is 75%" in new TestFixture {
      val onlineTestApplicationGisWithAdjustments = OnlineTestApplication("appId1", "PHASE1_TESTS", "userId1", "testAccountId",
        guaranteedInterview = true, needsOnlineAdjustments = true, needsAtVenueAdjustments = false, preferredName = "PrefName1",
        lastName = "LastName1", eTrayAdjustments = Some(AdjustmentDetail(Some(75), None, None)), videoInterviewAdjustments = None)
      val result = phase2TestService.calculateAbsoluteTimeWithAdjustments(onlineTestApplicationGisWithAdjustments)
      result mustBe 140
    }

    "return 80 when no adjustments needed" in new TestFixture {
      val onlineTestApplicationGisWithNoAdjustments = OnlineTestApplication("appId1", "PHASE1_TESTS", "userId1", "testAccountId",
        guaranteedInterview = true, needsOnlineAdjustments = false, needsAtVenueAdjustments = false, preferredName = "PrefName1",
        lastName = "LastName1", eTrayAdjustments = None, videoInterviewAdjustments = None)
      val result = phase2TestService.calculateAbsoluteTimeWithAdjustments(onlineTestApplicationGisWithNoAdjustments)
      result mustBe 80
    }
  }

  "email Invite to Applicants" should {
    "not be sent for invigilated e-tray" in new TestFixture {
      override val candidates = List(OnlineTestApplicationExamples.InvigilatedETrayCandidate)
      implicit val date: DateTime = invitationDate
      phase2TestService.emailInviteToApplicants(candidates).futureValue
      verifyNoInteractions(emailClientMock)
    }
  }

  // PSI specific no cubiks equivalent
  "store real time results" should {
    "handle not finding an application for the given order id" in new TestFixture {
      when(phase2TestRepositoryMock.getApplicationIdForOrderId(any[String], any[String])).thenReturnAsync(None)

      val result = phase2TestService.storeRealTimeResults(orderId, realTimeResults)

      val exception = result.failed.futureValue
      exception mustBe an[CannotFindTestByOrderIdException]
      exception.getMessage mustBe s"Application not found for test for orderId=$orderId"
    }

    "handle not finding a test profile for the given order id" in new TestFixture {
      when(phase2TestRepositoryMock.getApplicationIdForOrderId(any[String], any[String])).thenReturnAsync(Some(applicationId))

      when(phase2TestRepositoryMock.getTestProfileByOrderId(any[String])).thenReturn(Future.failed(
        CannotFindTestByOrderIdException(s"Cannot find test group by orderId=$orderId")
      ))

      val result = phase2TestService.storeRealTimeResults(orderId, realTimeResults)

      val exception = result.failed.futureValue
      exception mustBe an[CannotFindTestByOrderIdException]
      exception.getMessage mustBe s"Cannot find test group by orderId=$orderId"
    }

    "handle not finding the test group when checking to update the progress status" in new TestFixture {
      when(phase2TestRepositoryMock.getApplicationIdForOrderId(any[String], any[String])).thenReturnAsync(Some(applicationId))

      when(phase2TestRepositoryMock.getTestProfileByOrderId(any[String])).thenReturnAsync(phase2CompletedTestGroupWithAppId)
      when(phase2TestRepositoryMock.insertTestResult2(any[String], any[PsiTest], any[model.persisted.PsiTestResult])).thenReturnAsync()
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(None)

      val result = phase2TestService.storeRealTimeResults(orderId, realTimeResults)

      val exception = result.failed.futureValue
      exception mustBe an[Exception]
      exception.getMessage mustBe s"No test profile returned for $applicationId"

      verify(phase2TestRepositoryMock, never()).updateTestCompletionTime2(any[String], any[DateTime])
      verify(phase2TestRepositoryMock, never()).updateProgressStatus(any[String], any[ProgressStatuses.ProgressStatus])
    }

    "process the real time results and update the progress status" in new TestFixture {
      when(phase2TestRepositoryMock.getApplicationIdForOrderId(any[String], any[String])).thenReturnAsync(Some(applicationId))

      when(phase2TestRepositoryMock.getTestProfileByOrderId(any[String])).thenReturnAsync(phase2CompletedTestGroupWithAppId)
      when(phase2TestRepositoryMock.insertTestResult2(any[String], any[PsiTest], any[model.persisted.PsiTestResult])).thenReturnAsync()

      val phase2TestGroup = Phase2TestGroup(expirationDate = now, tests = List(fifthPsiTest, sixthPsiTest))
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestGroup))
      when(phase2TestRepositoryMock.updateProgressStatus(any[String], any[ProgressStatuses.ProgressStatus])).thenReturnAsync()

      phase2TestService.storeRealTimeResults(orderId, realTimeResults).futureValue

      verify(phase2TestRepositoryMock, never()).updateTestCompletionTime2(any[String], any[DateTime])
      verify(phase2TestRepositoryMock, times(1)).updateProgressStatus(any[String],
        eqTo(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED))
    }

    "process the real time results, mark the test as completed and update the progress status" in new TestFixture {
      when(phase2TestRepositoryMock.getApplicationIdForOrderId(any[String], any[String])).thenReturnAsync(Some(applicationId))

      //First call return tests that are not completed second call return tests that are are completed
      when(phase2TestRepositoryMock.getTestProfileByOrderId(any[String]))
        .thenReturnAsync(phase2NotCompletedTestGroupWithAppId)
        .thenReturnAsync(phase2CompletedTestGroupWithAppId)

      when(phase2TestRepositoryMock.insertTestResult2(any[String], any[PsiTest], any[model.persisted.PsiTestResult])).thenReturnAsync()
      when(phase2TestRepositoryMock.updateTestCompletionTime2(any[String], any[DateTime])).thenReturnAsync()

      when(phase2TestRepositoryMock.updateProgressStatus(any[String], eqTo(ProgressStatuses.PHASE2_TESTS_COMPLETED))).thenReturnAsync()

      val phase2TestGroup = Phase2TestGroup(expirationDate = now, tests = List(fifthPsiTest, sixthPsiTest))
      val phase2TestsCompleted: Phase2TestGroup = phase2TestGroup.copy(
        tests = phase2TestGroup.tests.map(t => t.copy(orderId = orderId, completedDateTime = Some(DateTime.now()))),
        expirationDate = DateTime.now().plusDays(2)
      )

      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestsCompleted))
      when(phase2TestRepositoryMock.updateProgressStatus(any[String], eqTo(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED))).thenReturnAsync()

      phase2TestService.storeRealTimeResults(orderId, realTimeResults).futureValue

      verify(phase2TestRepositoryMock, times(1)).updateTestCompletionTime2(any[String], any[DateTime])
      verify(phase2TestRepositoryMock, times(1)).updateProgressStatus(any[String], eqTo(ProgressStatuses.PHASE2_TESTS_COMPLETED))
      verify(phase2TestRepositoryMock, times(1)).updateProgressStatus(any[String], eqTo(ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED))
    }
  }

  private def phase2Progress(phase2ProgressResponse: Phase2ProgressResponse) =
    ProgressResponse("appId", phase2ProgressResponse = phase2ProgressResponse)

  trait TestFixture extends StcEventServiceFixture {

    implicit val hc: HeaderCarrier = mock[HeaderCarrier]
    implicit val rh: RequestHeader = mock[RequestHeader]

    val dateTimeFactoryMock: DateTimeFactory = mock[DateTimeFactory]
    implicit val now: DateTime = DateTimeFactoryMock.nowLocalTimeZone.withZone(DateTimeZone.UTC)
    when(dateTimeFactoryMock.nowLocalTimeZone).thenReturn(now)

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

    val mockPhase1TestConfig = Phase1TestsConfig(
      expiryTimeInDays = 5, gracePeriodInSecs = 0, testRegistrationDelayInSecs = 1, tests, standard = List("test1", "test2", "test3", "test4"),
      gis = List("test1", "test4")
    )

    val mockPhase2TestConfig = Phase2TestsConfig(
      expiryTimeInDays = 5, expiryTimeInDaysForInvigilatedETray = 90, gracePeriodInSecs = 0, testRegistrationDelayInSecs = 1, tests,
      standard = List("test1", "test2")
    )

    val mockNumericalTestsConfig = NumericalTestsConfig(gracePeriodInSecs = 0, tests = tests, standard = List("test1"))
    val gatewayConfig = OnlineTestsGatewayConfig(
      url = "",
      phase1Tests = mockPhase1TestConfig,
      phase2Tests = mockPhase2TestConfig,
      numericalTests = mockNumericalTestsConfig,
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
    val phase2TestRepositoryMock = mock[Phase2TestRepository]
    val onlineTestsGatewayClientMock = mock[OnlineTestsGatewayClient]
    val tokenFactoryMock = mock[UUIDFactory]
    val emailClientMock = mock[OnlineTestEmailClient]
    val auditServiceMock = mock[AuditService]

    val phase3TestServiceMock = mock[Phase3TestService]
    val siftServiceMock = mock[ApplicationSiftService]

    val appConfigMock = mock[MicroserviceAppConfig]
    when(appConfigMock.onlineTestsGatewayConfig).thenReturn(gatewayConfig)

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

    val phase2TestProfile = Phase2TestGroup(expirationDate,
      List(phase2Test, phase2Test.copy(inventoryId = uuid))
    )

    val phase2TestProfileWithNoTest = Phase2TestGroup(expirationDate, Nil)

    val phase2CompletedTestGroupWithAppId: Phase2TestGroupWithAppId = Phase2TestGroupWithAppId(
      applicationId,
      testGroup = phase2TestProfile.copy(
        tests = phase2TestProfile.tests.map( t =>
          t.copy(orderId = orderId, completedDateTime = Some(DateTime.now()))
        )
      )
    )

    val phase2NotCompletedTestGroupWithAppId: Phase2TestGroupWithAppId = Phase2TestGroupWithAppId(
      applicationId,
      testGroup = phase2TestProfile.copy(
        tests = phase2TestProfile.tests.map( t =>
          t.copy(orderId = orderId, completedDateTime = None)
        )
      )
    )

    val invigilatedTestProfile = Phase2TestGroup(
      invigilatedExpirationDate, List(phase2Test.copy(inventoryId = uuid, invigilatedAccessCode = Some("accessCode")))
    )

    val invigilatedETrayApp = onlineTestApplication.copy(
      needsOnlineAdjustments = true,
      eTrayAdjustments = Some(AdjustmentDetail(invigilatedInfo = Some("e-tray help needed")))
    )
    val nonInvigilatedETrayApp = onlineTestApplication.copy(needsOnlineAdjustments = false)

    when(phase2TestRepositoryMock.insertOrUpdateTestGroup(any[String], any[Phase2TestGroup]))
        .thenReturnAsync()

    when(phase2TestRepositoryMock.getTestGroup(any[String]))
      .thenReturnAsync(Some(phase2TestProfile))

    when(phase2TestRepositoryMock.resetTestProfileProgresses(any[String], any[List[ProgressStatus]]))
      .thenReturnAsync()

//    when(cdRepositoryMock.find(any[String])).thenReturn(Future.successful(
//      ContactDetails(outsideUk = false, Address("Aldwych road"), Some("QQ1 1QQ"), Some("UK"), "email@test.com", "111111")))

    when(cdRepositoryMock.find(any[String])).thenReturnAsync(contactDetails)

    when(emailClientMock.sendOnlineTestInvitation(any[String], any[String], any[DateTime])(any[HeaderCarrier]))
        .thenReturnAsync()

    when(phase2TestRepositoryMock.updateGroupExpiryTime(any[String], any[DateTime], any[String]))
      .thenReturnAsync()
    when(appRepositoryMock.removeProgressStatuses(any[String], any[List[ProgressStatus]]))
      .thenReturnAsync()
    when(phase2TestRepositoryMock.phaseName).thenReturn("phase2")

    val realTimeResults = PsiRealTimeResults(tScore = 10.0, rawScore = 20.0, reportUrl = None)

    val actor = ActorSystem()

    val phase2TestService = new Phase2TestService(
      appRepositoryMock,
      cdRepositoryMock,
      phase2TestRepositoryMock,
      onlineTestsGatewayClientMock,
      tokenFactoryMock,
      dateTimeFactoryMock,
      emailClientMock,
      auditServiceMock,
      authProviderClientMock,
      phase3TestServiceMock,
      siftServiceMock,
      appConfigMock,
      stcEventServiceMock,
      actor
    )
  }
}
