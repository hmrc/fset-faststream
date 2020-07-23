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

package services.onlinetesting.phase2

import akka.actor.ActorSystem
import config.Phase2ScheduleExamples._
import config._
import connectors.ExchangeObjects.{ Invitation, InviteApplicant, RegisterApplicant, Registration, TimeAdjustments, toString => _ }
import connectors.{ OnlineTestsGatewayClient, Phase2OnlineTestEmailClient }
import factories.{ DateTimeFactory, DateTimeFactoryImpl, UUIDFactory }
import model.Commands.PostCode
import model.Exceptions.{ ContactDetailsNotFoundForEmail, ExpiredTestForTokenException, InvalidTokenException }
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses.{ toString => _, _ }
import model._
import model.command.{ Phase2ProgressResponse, Phase3ProgressResponse, ProgressResponse }
import model.exchange.CubiksTestResultReady
import model.persisted.{ ContactDetails, Phase2TestGroup, _ }
import model.stc.AuditEvents.Phase2TestInvitationProcessComplete
import model.stc.DataStoreEvents
import model.stc.DataStoreEvents.OnlineExerciseResultSent
import org.joda.time.{ DateTime, DateTimeZone }
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.onlinetesting.Phase2TestRepository
import services.AuditService
import services.onlinetesting.Exceptions.CannotResetPhase2Tests
import services.onlinetesting.phase3.Phase3TestService
import services.sift.ApplicationSiftService
import services.stc.{ StcEventService, StcEventServiceFixture }
import testkit.{ ExtendedTimeout, UnitSpec }
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.language.postfixOps

class Phase2TestServiceSpec extends UnitSpec with ExtendedTimeout {

  "Verify access code" should {
    "return an invigilated test url for a valid candidate" in new TestFixture {
      when(cdRepositoryMock.findUserIdByEmail(any[String])).thenReturn(Future.successful(authenticateUrl))

      val accessCode = "TEST-CODE"
      val phase2TestGroup = Phase2TestGroup(expirationDate, List(phase2Test.copy(invigilatedAccessCode = Some(accessCode))))
      when(phase2TestRepositoryMock.getTestGroupByUserId(any[String])).thenReturn(Future.successful(Some(phase2TestGroup)))

      val result = phase2TestService.verifyAccessCode("test-email.com", accessCode).futureValue
      result mustBe authenticateUrl
    }

    "return a Failure if the access code does not match" in new TestFixture {
      when(cdRepositoryMock.findUserIdByEmail(any[String])).thenReturn(Future.successful(authenticateUrl))

      val accessCode = "TEST-CODE"
      val phase2TestGroup = Phase2TestGroup(expirationDate, List(phase2Test.copy(invigilatedAccessCode = Some(accessCode))))
      when(phase2TestRepositoryMock.getTestGroupByUserId(any[String])).thenReturn(Future.successful(Some(phase2TestGroup)))

      val result = phase2TestService.verifyAccessCode("test-email.com", "I-DO-NOT-MATCH").failed.futureValue
      result mustBe an[InvalidTokenException]
    }

    "return a Failure if the user cannot be located by email" in new TestFixture {
      when(cdRepositoryMock.findUserIdByEmail(any[String])).thenReturn(Future.failed(ContactDetailsNotFoundForEmail()))

      val result = phase2TestService.verifyAccessCode("test-email.com", "ANY-CODE").failed.futureValue
      result mustBe an[ContactDetailsNotFoundForEmail]
    }

    "return A Failure if the test is Expired" in new TestFixture {
      when(cdRepositoryMock.findUserIdByEmail(any[String])).thenReturn(Future.successful(authenticateUrl))

      val accessCode = "TEST-CODE"
      val phase2TestGroup = Phase2TestGroup(expiredDate, List(phase2Test.copy(invigilatedAccessCode = Some(accessCode))))
      when(phase2TestRepositoryMock.getTestGroupByUserId(any[String])).thenReturn(Future.successful(Some(phase2TestGroup)))

      val result = phase2TestService.verifyAccessCode("test-email.com", accessCode).failed.futureValue
      result mustBe an[ExpiredTestForTokenException]
    }
  }

  "Register applicants" should {
    "correctly register a batch of candidates" in new TestFixture {
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
    "correctly invite a batch of candidates" in new TestFixture {
      override def availableSchedules = Map("daro" -> DaroSchedule)
      override val phase2TestProfile = Phase2TestGroup(expirationDate, List(phase2Test.copy(scheduleId = DaroSchedule.scheduleId)))
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase2TestProfile)))

      val result = phase2TestService.inviteApplicants(registeredMap, DaroSchedule).futureValue

      result mustBe List(phase2TestService.Phase2TestInviteData(onlineTestApplication, DaroSchedule.scheduleId, tokens.head,
        registrations.head, invites.head),
        phase2TestService.Phase2TestInviteData(onlineTestApplication2, scheduleId = DaroSchedule.scheduleId, tokens.last,
          registrations.last, invites.last)
      )
      verify(auditServiceMock, times(2)).logEventNoRequest(eqTo("Phase2TestInvited"), any[Map[String, String]])
    }
  }

  "Register and Invite applicants" must {
    "email the candidate and send audit events" in new TestFixture {
      override val phase2TestProfile = Phase2TestGroup(expirationDate,
        List(phase2Test)
      )
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase2TestProfile)))
      when(phase2TestRepositoryMock.markTestAsInactive(any[Int])).thenReturn(Future.successful(()))
      when(phase2TestRepositoryMock.insertCubiksTests(any[String], any[Phase2TestGroup])).thenReturn(Future.successful(()))
      phase2TestService.registerAndInviteForTestGroup(candidates).futureValue

      verify(auditServiceMock, times(2)).logEventNoRequest(eqTo("Phase2TestRegistered"), any[Map[String, String]])
      verify(auditServiceMock, times(2)).logEventNoRequest(eqTo("Phase2TestInvited"), any[Map[String, String]])
      verify(auditEventHandlerMock, times(2)).handle(any[Phase2TestInvitationProcessComplete])(any[HeaderCarrier],
        any[RequestHeader])
      verify(dataStoreEventHandlerMock, times(2)).handle(any[OnlineExerciseResultSent])(any[HeaderCarrier],
        any[RequestHeader])
      verify(auditServiceMock, times(2)).logEventNoRequest(eqTo("OnlineTestInvitationEmailSent"), any[Map[String, String]])

      verify(phase2TestRepositoryMock, times(2)).insertCubiksTests(any[String], any[Phase2TestGroup])
      verify(phase2TestRepositoryMock, times(2)).markTestAsInactive(any[Int])
    }

    "process adjustment candidates first and individually" ignore new TestFixture {
      val adjustmentCandidates = candidates :+ adjustmentApplication :+ adjustmentApplication2
      when(onlineTestsGatewayClientMock.registerApplicants(any[Int]))
        .thenReturn(Future.successful(List(registrations.head)))

      when(onlineTestsGatewayClientMock.inviteApplicants(any[List[InviteApplicant]]))
        .thenReturn(Future.successful(List(invites.head)))

      phase2TestService.registerAndInviteForTestGroup(adjustmentCandidates).futureValue

      verify(auditServiceMock).logEventNoRequest(eqTo("Phase2TestRegistered"), any[Map[String, String]])
      verify(auditServiceMock).logEventNoRequest(eqTo("Phase2TestInvited"), any[Map[String, String]])
      verify(auditServiceMock).logEventNoRequest(eqTo("Phase2TestInvitationProcessComplete"), any[Map[String, String]])
      verify(phase2TestRepositoryMock).insertOrUpdateTestGroup(any[String], any[Phase2TestGroup])
      verifyDataStoreEvents(1, "OnlineExerciseResultSent")
    }

    "register and invite an invigilated e-tray candidate to DARO schedule" in new TestFixture {
      val application = onlineTestApplication.copy(
        needsOnlineAdjustments = true,
        eTrayAdjustments = Some(AdjustmentDetail(invigilatedInfo = Some("e-tray help needed")))
      )
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase2TestProfile)))

      when(onlineTestsGatewayClientMock.registerApplicants(any[Int])).thenReturn(Future.successful(List(registrations.head)))
      when(onlineTestsGatewayClientMock.inviteApplicants(any[List[InviteApplicant]])).thenReturn(Future.successful(List(invites.head)))
      when(phase2TestRepositoryMock.markTestAsInactive(any[Int])).thenReturn(Future.successful(()))
      when(phase2TestRepositoryMock.insertCubiksTests(any[String], any[Phase2TestGroup])).thenReturn(Future.successful(()))

      phase2TestService.registerAndInviteForTestGroup(List(application)).futureValue

      val invitation = InviteApplicant(DaroSchedule.scheduleId, cubiksUserId, inviteApplicant.scheduleCompletionURL, None)
      verify(onlineTestsGatewayClientMock).inviteApplicants(List(invitation))
      verify(phase2TestRepositoryMock).insertCubiksTests(
        application.applicationId,
        invigilatedTestProfile
      )
    }
  }

  "processNextExpiredTest" should {
    val phase2ExpirationEvent = Phase2ExpirationEvent(gracePeriodInSecs = 0)

    "do nothing if there is no expired application to process" in new TestFixture {
      when(phase2TestRepositoryMock.nextExpiringApplication(phase2ExpirationEvent)).thenReturn(Future.successful(None))
      phase2TestService.processNextExpiredTest(phase2ExpirationEvent).futureValue mustBe unit
    }

    "update progress status and send an email to the user when a Faststream application is expired" in new TestFixture {
      when(phase2TestRepositoryMock.nextExpiringApplication(phase2ExpirationEvent)).thenReturn(Future.successful(Some(expiredApplication)))
      when(cdRepositoryMock.find(any[String])).thenReturn(Future.successful(contactDetails))
      when(appRepositoryMock.getApplicationRoute(any[String])).thenReturn(Future.successful(ApplicationRoute.Faststream))
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
      verify(siftServiceMock, never()).sendSiftEnteredNotification(applicationId)
      verify(emailClientMock).sendEmailWithName(emailContactDetails, preferredName, TestExpirationEmailTemplates.phase2ExpirationTemplate)
    }

    "update progress status and send an email to the user when an sdip faststream application is expired" in new TestFixture {
      when(phase2TestRepositoryMock.nextExpiringApplication(phase2ExpirationEvent)).thenReturn(Future.successful(Some(expiredApplication)))
      when(cdRepositoryMock.find(any[String])).thenReturn(Future.successful(contactDetails))
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
      verify(siftServiceMock, never()).sendSiftEnteredNotification(applicationId)
      verify(emailClientMock).sendEmailWithName(emailContactDetails, preferredName, TestExpirationEmailTemplates.phase2ExpirationTemplate)
    }
  }

  "mark as started" should {
    "change progress to started" in new TestFixture {
      when(phase2TestRepositoryMock.updateTestStartTime(any[Int], any[DateTime])).thenReturn(Future.successful(()))
      when(phase2TestRepositoryMock.getTestProfileByCubiksId(cubiksUserId))
        .thenReturn(Future.successful(Phase2TestGroupWithAppId("appId123", phase2TestProfile)))
      when(phase2TestRepositoryMock.updateProgressStatus("appId123", ProgressStatuses.PHASE2_TESTS_STARTED)).thenReturn(Future.successful(()))
      phase2TestService.markAsStarted(cubiksUserId).futureValue

      verify(phase2TestRepositoryMock).updateProgressStatus("appId123", ProgressStatuses.PHASE2_TESTS_STARTED)
    }
  }

  "mark as completed" should {
    "change progress to completed if there are all tests completed" in new TestFixture {
      when(phase2TestRepositoryMock.updateTestCompletionTime(any[Int], any[DateTime])).thenReturn(Future.successful(()))
      val phase2Tests = phase2TestProfile.copy(tests = phase2TestProfile.tests.map(t => t.copy(completedDateTime = Some(DateTime.now()))),
        expirationDate = DateTime.now().plusDays(2)
      )

      when(phase2TestRepositoryMock.getTestProfileByCubiksId(cubiksUserId))
        .thenReturn(Future.successful(Phase2TestGroupWithAppId("appId123", phase2Tests)))
      when(phase2TestRepositoryMock.updateProgressStatus("appId123", ProgressStatuses.PHASE2_TESTS_COMPLETED)).thenReturn(Future.successful(()))

      phase2TestService.markAsCompleted(cubiksUserId).futureValue

      verify(phase2TestRepositoryMock).updateProgressStatus("appId123", ProgressStatuses.PHASE2_TESTS_COMPLETED)
    }
  }

  "mark report as ready to download" should {
    "not change progress if not all the active tests have reports ready" in new TestFixture {
      val reportReady = CubiksTestResultReady(reportId = Some(1), reportStatus = "Ready", reportLinkURL = Some("www.report.com"))

      when(phase2TestRepositoryMock.getTestProfileByCubiksId(cubiksUserId)).thenReturn(
        Future.successful(Phase2TestGroupWithAppId("appId", phase2TestProfile.copy(
          tests = List(phase2Test.copy(usedForResults = false, cubiksUserId = 123),
            phase2Test,
            phase2Test.copy(cubiksUserId = 789, resultsReadyToDownload = false)
          )
        )))
      )
      when(phase2TestRepositoryMock.updateTestReportReady(cubiksUserId, reportReady)).thenReturn(Future.successful(()))
      when(phase2TestRepositoryMock.updateProgressStatus(any[String], any[ProgressStatus])).thenReturn(Future.successful(()))

      val result = phase2TestService.markAsReportReadyToDownload(cubiksUserId, reportReady).futureValue

      verify(phase2TestRepositoryMock, times(0)).updateProgressStatus(any[String], any[ProgressStatus])
    }

    "change progress to reports ready if all the active tests have reports ready" in new TestFixture {
      val reportReady = CubiksTestResultReady(reportId = Some(1), reportStatus = "Ready", reportLinkURL = Some("www.report.com"))

      when(phase2TestRepositoryMock.getTestProfileByCubiksId(cubiksUserId)).thenReturn(
        Future.successful(Phase2TestGroupWithAppId("appId", phase2TestProfile.copy(
          tests = List(phase2Test.copy(usedForResults = false, cubiksUserId = 123),
            phase2Test.copy(resultsReadyToDownload = true),
            phase2Test.copy(cubiksUserId = 789, resultsReadyToDownload = true)
          )
        )))
      )
      when(phase2TestRepositoryMock.updateTestReportReady(cubiksUserId, reportReady)).thenReturn(Future.successful(()))
      when(phase2TestRepositoryMock.updateProgressStatus(any[String], any[ProgressStatus])).thenReturn(Future.successful(()))

      val result = phase2TestService.markAsReportReadyToDownload(cubiksUserId, reportReady).futureValue

      verify(phase2TestRepositoryMock).updateProgressStatus("appId", ProgressStatuses.PHASE2_TESTS_RESULTS_READY)
    }
  }

  "reset phase2 tests" should {
    "remove progress and register for new tests" in new TestFixture {
      val currentExpirationDate = now.plusDays(2)
      override val phase2TestProfile = Phase2TestGroup(currentExpirationDate,
        List(phase2Test.copy(scheduleId = DaroSchedule.scheduleId))
      )

      val onlineTestApplicationForReset = onlineTestApplication.copy(applicationStatus = ApplicationStatus.PHASE2_TESTS_PASSED)

      val expectedRegistration = registrations.head
      val expectedInvite = invites.head
      val phase2TestProfileWithStartedTests = phase2TestProfile.copy(tests = phase2TestProfile.tests
        .map(t => t.copy(scheduleId = 3, startedDateTime = Some(startedDate))))
      val phase2TestProfileWithNewTest = phase2TestProfileWithStartedTests.copy(tests =
        List(phase2Test.copy(usedForResults = false), phase2Test))

      // expectations for 3 invocations
      when(phase2TestRepositoryMock.getTestGroup(any[String]))
        .thenReturn(Future.successful(Some(phase2TestProfileWithStartedTests)))
        .thenReturn(Future.successful(Some(phase2TestProfileWithStartedTests)))
        .thenReturn(Future.successful(Some(phase2TestProfileWithNewTest)))

      when(phase2TestRepositoryMock.markTestAsInactive(any[Int])).thenReturn(Future.successful(()))
      when(phase2TestRepositoryMock.insertCubiksTests(any[String], any[Phase2TestGroup])).thenReturn(Future.successful(()))
      when(onlineTestsGatewayClientMock.registerApplicants(any[Int]))
        .thenReturn(Future.successful(List(expectedRegistration)))
      when(onlineTestsGatewayClientMock.inviteApplicants(any[List[InviteApplicant]]))
        .thenReturn(Future.successful(List(expectedInvite)))

      phase2TestService.resetTests(onlineTestApplicationForReset, "createdBy").futureValue

      verify(phase2TestRepositoryMock).resetTestProfileProgresses("appId",
        List(PHASE2_TESTS_STARTED, PHASE2_TESTS_COMPLETED, PHASE2_TESTS_RESULTS_RECEIVED, PHASE2_TESTS_RESULTS_READY,
          PHASE2_TESTS_FAILED, PHASE2_TESTS_EXPIRED, PHASE2_TESTS_PASSED, PHASE2_TESTS_FAILED_NOTIFIED, PHASE2_TESTS_FAILED_SDIP_AMBER))
      verify(phase2TestRepositoryMock).markTestAsInactive(cubiksUserId)

      val phase2TestGroupCaptor: ArgumentCaptor[Phase2TestGroup] = ArgumentCaptor.forClass(classOf[Phase2TestGroup])
      verify(phase2TestRepositoryMock).insertCubiksTests(any[String], phase2TestGroupCaptor.capture)

      val phase2TestGroup = phase2TestGroupCaptor.getValue
      phase2TestGroup.expirationDate mustBe currentExpirationDate

      verify(dataStoreEventHandlerMock).handle(DataStoreEvents.ETrayReset("appId", "createdBy"))(hc, rh)
    }

    "reset and set 5 days expiry date for non invigilated e-tray" in new TestFixture {
      val currentExpiryDate = now.minusDays(2)
      override val phase2TestProfile = Phase2TestGroup(currentExpiryDate,
        List(phase2Test.copy(scheduleId = DaroSchedule.scheduleId))
      )

      val onlineTestApplicationForReset = onlineTestApplication.copy(applicationStatus = ApplicationStatus.PHASE2_TESTS_PASSED)

      val expectedRegistration = registrations.head
      val expectedInvite = invites.head
      val phase2TestProfileWithStartedTests = phase2TestProfile.copy(tests = phase2TestProfile.tests
        .map(t => t.copy(scheduleId = 3, startedDateTime = Some(startedDate))))
      val phase2TestProfileWithNewTest = phase2TestProfileWithStartedTests.copy(tests =
        List(phase2Test.copy(usedForResults = false), phase2Test))

      // expectations for 3 invocations
      when(phase2TestRepositoryMock.getTestGroup(any[String]))
        .thenReturn(Future.successful(Some(phase2TestProfileWithStartedTests)))
        .thenReturn(Future.successful(Some(phase2TestProfileWithStartedTests)))
        .thenReturn(Future.successful(Some(phase2TestProfileWithNewTest)))

      when(phase2TestRepositoryMock.markTestAsInactive(any[Int])).thenReturn(Future.successful(()))
      when(phase2TestRepositoryMock.insertCubiksTests(any[String], any[Phase2TestGroup])).thenReturn(Future.successful(()))
      when(onlineTestsGatewayClientMock.registerApplicants(any[Int]))
        .thenReturn(Future.successful(List(expectedRegistration)))
      when(onlineTestsGatewayClientMock.inviteApplicants(any[List[InviteApplicant]]))
        .thenReturn(Future.successful(List(expectedInvite)))

      phase2TestService.resetTests(onlineTestApplicationForReset, "createdBy").futureValue

      verify(phase2TestRepositoryMock).resetTestProfileProgresses("appId",
        List(PHASE2_TESTS_STARTED, PHASE2_TESTS_COMPLETED, PHASE2_TESTS_RESULTS_RECEIVED, PHASE2_TESTS_RESULTS_READY,
          PHASE2_TESTS_FAILED, PHASE2_TESTS_EXPIRED, PHASE2_TESTS_PASSED, PHASE2_TESTS_FAILED_NOTIFIED, PHASE2_TESTS_FAILED_SDIP_AMBER))
      verify(phase2TestRepositoryMock).markTestAsInactive(cubiksUserId)

      val phase2TestGroupCaptor: ArgumentCaptor[Phase2TestGroup] = ArgumentCaptor.forClass(classOf[Phase2TestGroup])
      verify(phase2TestRepositoryMock).insertCubiksTests(any[String], phase2TestGroupCaptor.capture)

      val phase2TestGroup = phase2TestGroupCaptor.getValue
      phase2TestGroup.expirationDate.toLocalDate mustBe now.plusDays(5).toLocalDate

      verify(dataStoreEventHandlerMock).handle(DataStoreEvents.ETrayReset("appId", "createdBy"))(hc, rh)
    }

    "reset and reinstate expiry date from remaining days for invigilated e-tray" in new TestFixture {
      val currentExpiryDate = now.plusDays(30)
      override val phase2TestProfile = Phase2TestGroup(currentExpiryDate,
        List(phase2Test.copy(scheduleId = DaroSchedule.scheduleId, invigilatedAccessCode = Some("ABC")))
      )

      val invigilatedOnlineTestApplicationForReset = onlineTestApplication.copy(
        applicationStatus = ApplicationStatus.PHASE2_TESTS_PASSED,
        needsOnlineAdjustments = true,
        eTrayAdjustments = Some(AdjustmentDetail(invigilatedInfo = Some("")))
      )

      val expectedRegistration = registrations.head
      val expectedInvite = invites.head
      val phase2TestProfileWithStartedTests = phase2TestProfile.copy(tests = phase2TestProfile.tests
        .map(t => t.copy(scheduleId = 3, startedDateTime = Some(startedDate))))
      val phase2TestProfileWithNewTest = phase2TestProfileWithStartedTests.copy(tests =
        List(phase2Test.copy(usedForResults = false), phase2Test))

      // expectations for 3 invocations
      when(phase2TestRepositoryMock.getTestGroup(any[String]))
        .thenReturn(Future.successful(Some(phase2TestProfileWithStartedTests)))
        .thenReturn(Future.successful(Some(phase2TestProfileWithStartedTests)))
        .thenReturn(Future.successful(Some(phase2TestProfileWithNewTest)))

      when(phase2TestRepositoryMock.markTestAsInactive(any[Int])).thenReturn(Future.successful(()))
      when(phase2TestRepositoryMock.insertCubiksTests(any[String], any[Phase2TestGroup])).thenReturn(Future.successful(()))
      when(onlineTestsGatewayClientMock.registerApplicants(any[Int]))
        .thenReturn(Future.successful(List(expectedRegistration)))
      when(onlineTestsGatewayClientMock.inviteApplicants(any[List[InviteApplicant]]))
        .thenReturn(Future.successful(List(expectedInvite)))

      phase2TestService.resetTests(invigilatedOnlineTestApplicationForReset, "createdBy").futureValue

      verify(phase2TestRepositoryMock).resetTestProfileProgresses("appId",
        List(PHASE2_TESTS_STARTED, PHASE2_TESTS_COMPLETED, PHASE2_TESTS_RESULTS_RECEIVED, PHASE2_TESTS_RESULTS_READY,
          PHASE2_TESTS_FAILED, PHASE2_TESTS_EXPIRED, PHASE2_TESTS_PASSED, PHASE2_TESTS_FAILED_NOTIFIED, PHASE2_TESTS_FAILED_SDIP_AMBER))
      verify(phase2TestRepositoryMock).markTestAsInactive(cubiksUserId)

      val phase2TestGroupCaptor: ArgumentCaptor[Phase2TestGroup] = ArgumentCaptor.forClass(classOf[Phase2TestGroup])
      verify(phase2TestRepositoryMock).insertCubiksTests(any[String], phase2TestGroupCaptor.capture)

      val phase2TestGroup = phase2TestGroupCaptor.getValue
      phase2TestGroup.expirationDate mustBe currentExpiryDate

      verify(dataStoreEventHandlerMock).handle(DataStoreEvents.ETrayReset("appId", "createdBy"))(hc, rh)
    }

    "reset and set 90 days expiry date for invigilated e-tray" in new TestFixture {
      val currentExpiryDate = now.minusDays(2)
      override val phase2TestProfile = Phase2TestGroup(currentExpiryDate,
        List(phase2Test.copy(scheduleId = DaroSchedule.scheduleId))
      )

      val invigilatedOnlineTestApplicationForReset = onlineTestApplication.copy(
        applicationStatus = ApplicationStatus.PHASE2_TESTS_PASSED,
        needsOnlineAdjustments = true,
        eTrayAdjustments = Some(AdjustmentDetail(invigilatedInfo = Some("")))
      )

      val expectedRegistration = registrations.head
      val expectedInvite = invites.head
      val phase2TestProfileWithStartedTests = phase2TestProfile.copy(tests = phase2TestProfile.tests
        .map(t => t.copy(scheduleId = 3, startedDateTime = Some(startedDate))))
      val phase2TestProfileWithNewTest = phase2TestProfileWithStartedTests.copy(tests =
        List(phase2Test.copy(usedForResults = false), phase2Test))

      // expectations for 3 invocations
      when(phase2TestRepositoryMock.getTestGroup(any[String]))
        .thenReturn(Future.successful(Some(phase2TestProfileWithStartedTests)))
        .thenReturn(Future.successful(Some(phase2TestProfileWithStartedTests)))
        .thenReturn(Future.successful(Some(phase2TestProfileWithNewTest)))

      when(phase2TestRepositoryMock.markTestAsInactive(any[Int])).thenReturn(Future.successful(()))
      when(phase2TestRepositoryMock.insertCubiksTests(any[String], any[Phase2TestGroup])).thenReturn(Future.successful(()))
      when(onlineTestsGatewayClientMock.registerApplicants(any[Int]))
        .thenReturn(Future.successful(List(expectedRegistration)))
      when(onlineTestsGatewayClientMock.inviteApplicants(any[List[InviteApplicant]]))
        .thenReturn(Future.successful(List(expectedInvite)))

      phase2TestService.resetTests(invigilatedOnlineTestApplicationForReset, "createdBy").futureValue

      verify(phase2TestRepositoryMock).resetTestProfileProgresses("appId",
        List(PHASE2_TESTS_STARTED, PHASE2_TESTS_COMPLETED, PHASE2_TESTS_RESULTS_RECEIVED, PHASE2_TESTS_RESULTS_READY,
          PHASE2_TESTS_FAILED, PHASE2_TESTS_EXPIRED, PHASE2_TESTS_PASSED, PHASE2_TESTS_FAILED_NOTIFIED, PHASE2_TESTS_FAILED_SDIP_AMBER))
      verify(phase2TestRepositoryMock).markTestAsInactive(cubiksUserId)

      val phase2TestGroupCaptor: ArgumentCaptor[Phase2TestGroup] = ArgumentCaptor.forClass(classOf[Phase2TestGroup])
      verify(phase2TestRepositoryMock).insertCubiksTests(any[String], phase2TestGroupCaptor.capture)

      val phase2TestGroup = phase2TestGroupCaptor.getValue
      phase2TestGroup.expirationDate.toLocalDate mustBe now.plusDays(90).toLocalDate

      verify(dataStoreEventHandlerMock).handle(DataStoreEvents.ETrayReset("appId", "createdBy"))(hc, rh)
    }

    "reset invigilated e-tray by removing adjustments and set 5 days expiry date" in new TestFixture {
      val currentExpiryDate = now.plusDays(30)
      override val phase2TestProfile = Phase2TestGroup(currentExpiryDate,
        List(phase2Test.copy(scheduleId = DaroSchedule.scheduleId, invigilatedAccessCode = Some("ABC")))
      )

      val onlineTestApplicationForReset = onlineTestApplication.copy(
        applicationStatus = ApplicationStatus.PHASE2_TESTS_PASSED
      )

      val expectedRegistration = registrations.head
      val expectedInvite = invites.head
      val phase2TestProfileWithStartedTests = phase2TestProfile.copy(tests = phase2TestProfile.tests
        .map(t => t.copy(scheduleId = 3, startedDateTime = Some(startedDate))))
      val phase2TestProfileWithNewTest = phase2TestProfileWithStartedTests.copy(tests =
        List(phase2Test.copy(usedForResults = false), phase2Test))

      // expectations for 3 invocations
      when(phase2TestRepositoryMock.getTestGroup(any[String]))
        .thenReturn(Future.successful(Some(phase2TestProfileWithStartedTests)))
        .thenReturn(Future.successful(Some(phase2TestProfileWithStartedTests)))
        .thenReturn(Future.successful(Some(phase2TestProfileWithNewTest)))

      when(phase2TestRepositoryMock.markTestAsInactive(any[Int])).thenReturn(Future.successful(()))
      when(phase2TestRepositoryMock.insertCubiksTests(any[String], any[Phase2TestGroup])).thenReturn(Future.successful(()))
      when(onlineTestsGatewayClientMock.registerApplicants(any[Int]))
        .thenReturn(Future.successful(List(expectedRegistration)))
      when(onlineTestsGatewayClientMock.inviteApplicants(any[List[InviteApplicant]]))
        .thenReturn(Future.successful(List(expectedInvite)))

      phase2TestService.resetTests(onlineTestApplicationForReset, "createdBy").futureValue

      verify(phase2TestRepositoryMock).resetTestProfileProgresses("appId",
        List(PHASE2_TESTS_STARTED, PHASE2_TESTS_COMPLETED, PHASE2_TESTS_RESULTS_RECEIVED, PHASE2_TESTS_RESULTS_READY,
          PHASE2_TESTS_FAILED, PHASE2_TESTS_EXPIRED, PHASE2_TESTS_PASSED, PHASE2_TESTS_FAILED_NOTIFIED, PHASE2_TESTS_FAILED_SDIP_AMBER))
      verify(phase2TestRepositoryMock).markTestAsInactive(cubiksUserId)

      val phase2TestGroupCaptor: ArgumentCaptor[Phase2TestGroup] = ArgumentCaptor.forClass(classOf[Phase2TestGroup])
      verify(phase2TestRepositoryMock).insertCubiksTests(any[String], phase2TestGroupCaptor.capture)

      val phase2TestGroup = phase2TestGroupCaptor.getValue
      phase2TestGroup.expirationDate.toLocalDate mustBe now.plusDays(5).toLocalDate

      verify(dataStoreEventHandlerMock).handle(DataStoreEvents.ETrayReset("appId", "createdBy"))(hc, rh)
    }

    "remove phase 3 tests and reset phase 2 tests" in new TestFixture {
      override val phase2TestProfile = Phase2TestGroup(expirationDate,
        List(phase2Test.copy(scheduleId = DaroSchedule.scheduleId))
      )

      val onlineTestApplicationForReset = onlineTestApplication.copy(applicationStatus = ApplicationStatus.PHASE3_TESTS)
      val progressResponse = ProgressResponse("appId", phase3ProgressResponse =
        Phase3ProgressResponse(phase3TestsInvited = true))

      when(appRepositoryMock.findProgress(any[String])).
        thenReturn(Future.successful(progressResponse))

      when(phase3TestServiceMock.removeTestGroup("appId")).thenReturn(Future.successful(()))

      val expectedRegistration = registrations.head
      val expectedInvite = invites.head
      val phase2TestProfileWithStartedTests = phase2TestProfile.copy(tests = phase2TestProfile.tests
        .map(t => t.copy(scheduleId = 3, startedDateTime = Some(startedDate))))
      val phase2TestProfileWithNewTest = phase2TestProfileWithStartedTests.copy(tests =
        List(phase2Test.copy(usedForResults = false), phase2Test))

      // expectations for 3 invocations
      when(phase2TestRepositoryMock.getTestGroup(any[String]))
        .thenReturn(Future.successful(Some(phase2TestProfileWithStartedTests)))
        .thenReturn(Future.successful(Some(phase2TestProfileWithStartedTests)))
        .thenReturn(Future.successful(Some(phase2TestProfileWithNewTest)))

      when(phase2TestRepositoryMock.markTestAsInactive(any[Int])).thenReturn(Future.successful(()))
      when(phase2TestRepositoryMock.insertCubiksTests(any[String], any[Phase2TestGroup])).thenReturn(Future.successful(()))
      when(onlineTestsGatewayClientMock.registerApplicants(any[Int]))
        .thenReturn(Future.successful(List(expectedRegistration)))
      when(onlineTestsGatewayClientMock.inviteApplicants(any[List[InviteApplicant]]))
        .thenReturn(Future.successful(List(expectedInvite)))

      phase2TestService.resetTests(onlineTestApplicationForReset, "createdBy").futureValue

      verify(phase2TestRepositoryMock).resetTestProfileProgresses("appId",
        List(PHASE2_TESTS_STARTED, PHASE2_TESTS_COMPLETED, PHASE2_TESTS_RESULTS_RECEIVED, PHASE2_TESTS_RESULTS_READY,
          PHASE2_TESTS_FAILED, PHASE2_TESTS_EXPIRED, PHASE2_TESTS_PASSED, PHASE2_TESTS_FAILED_NOTIFIED, PHASE2_TESTS_FAILED_SDIP_AMBER))
      verify(phase2TestRepositoryMock).markTestAsInactive(cubiksUserId)
      verify(phase2TestRepositoryMock).insertCubiksTests(any[String], any[Phase2TestGroup])
      verify(dataStoreEventHandlerMock).handle(DataStoreEvents.ETrayReset("appId", "createdBy"))(hc, rh)
    }

    "return cannot reset phase2 tests exception" in new TestFixture {
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(None))

      an[CannotResetPhase2Tests] must be thrownBy
        Await.result(phase2TestService.resetTests(onlineTestApplication, "createdBy"), 1 seconds)
    }
  }

  "Extend time for expired test" should {
    val progress = phase2Progress(
      Phase2ProgressResponse(
        phase2TestsExpired = true,
        phase2TestsFirstReminder = true,
        phase2TestsSecondReminder = true
      ))

    "extend the test to 5 days from now and remove: expired and two reminder progresses" in new TestFixture {
      when(appRepositoryMock.findProgress(any[String])).thenReturn(Future.successful(progress))
      val phase2TestProfileWithExpirationInPast = Phase2TestGroup(now.minusDays(1), List(phase2Test))
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase2TestProfileWithExpirationInPast)))

      phase2TestService.extendTestGroupExpiryTime("appId", 5, "admin").futureValue

      verify(phase2TestRepositoryMock).updateGroupExpiryTime("appId", now.plusDays(5), "phase2")
      verify(appRepositoryMock).removeProgressStatuses("appId", List(
        PHASE2_TESTS_EXPIRED, PHASE2_TESTS_SECOND_REMINDER, PHASE2_TESTS_FIRST_REMINDER)
      )
    }

    "extend the test to 3 days from now and remove: expired and only one reminder progresses" in new TestFixture {
      when(appRepositoryMock.findProgress(any[String])).thenReturn(Future.successful(progress))
      val phase2TestProfileWithExpirationInPast = Phase2TestGroup(now.minusDays(1), List(phase2Test))
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase2TestProfileWithExpirationInPast)))

      phase2TestService.extendTestGroupExpiryTime("appId", 3, "admin").futureValue

      verify(phase2TestRepositoryMock).updateGroupExpiryTime("appId", now.plusDays(3), "phase2")
      verify(appRepositoryMock).removeProgressStatuses("appId", List(
        PHASE2_TESTS_EXPIRED, PHASE2_TESTS_SECOND_REMINDER)
      )
    }

    "extend the test to 1 day from now and remove: expired progress" in new TestFixture {
      when(appRepositoryMock.findProgress(any[String])).thenReturn(Future.successful(progress))
      val phase2TestProfileWithExpirationInPast = Phase2TestGroup(now.minusDays(1), List(phase2Test))
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase2TestProfileWithExpirationInPast)))

      phase2TestService.extendTestGroupExpiryTime("appId", 1, "admin").futureValue

      verify(phase2TestRepositoryMock).updateGroupExpiryTime("appId", now.plusDays(1), "phase2")
      verify(appRepositoryMock).removeProgressStatuses("appId", List(PHASE2_TESTS_EXPIRED))
    }
  }

  "retrieve phase 2 test report" should {
    "return an exception if there is an error retrieving one of the reports" in new TestFixture {
      val failedTest = phase2Test.copy(scheduleId = 555, reportId = Some(2))

      when(onlineTestsGatewayClientMock.downloadXmlReport(eqTo(failedTest.reportId.get)))
        .thenReturn(Future.failed(new Exception))

      val result = phase2TestService.retrieveTestResult(Phase2TestGroupWithAppId(
        "appId", phase2TestProfile.copy(tests = List(failedTest))
      ))

      result.failed.futureValue mustBe an[Exception]
      verify(auditServiceMock, times(0)).logEventNoRequest(eqTo("ResultsRetrievedForSchedule"), any[Map[String, String]])
      verify(auditServiceMock, times(0)).logEventNoRequest(eqTo(s"ProgressStatusSet${ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED}"),
        any[Map[String, String]])
      verify(phase2TestRepositoryMock, times(0)).updateProgressStatus(any[String], any[ProgressStatus])
      verify(phase2TestRepositoryMock, times(0)).insertTestResult(any[String], eqTo(failedTest), any[TestResult])
    }

    "Not update anything if the active test has no report Id" in new TestFixture {
      val usedTest = phase2Test.copy(usedForResults = true, reportId = None, resultsReadyToDownload = false)
      val unusedTest = phase2Test.copy(usedForResults = false, reportId = Some(123), resultsReadyToDownload = true)
      val testProfile = phase2TestProfile.copy(tests = List(usedTest, unusedTest))

      when(phase2TestRepositoryMock.updateProgressStatus(any[String], any[ProgressStatus])).thenReturn(Future.successful(()))

      phase2TestService.retrieveTestResult(Phase2TestGroupWithAppId(
        "appId", testProfile
      )).futureValue

      verify(auditServiceMock, times(0)).logEventNoRequest(eqTo("ResultsRetrievedForSchedule"), any[Map[String, String]])
      verify(auditServiceMock, times(0)).logEventNoRequest(eqTo(s"ProgressStatusSet${ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED}"),
        any[Map[String, String]])
      verify(phase2TestRepositoryMock, times(0)).updateProgressStatus(any[String], any[ProgressStatus])
      verify(phase2TestRepositoryMock, times(0)).insertTestResult(any[String], eqTo(usedTest), any[TestResult])
      verify(phase2TestRepositoryMock, times(0)).insertTestResult(any[String], eqTo(unusedTest), any[TestResult])
    }

    "save a phase2 report for a candidate and update progress status" in new TestFixture {
      val test = phase2Test.copy(reportId = Some(123), resultsReadyToDownload = true)
      val testProfile = phase2TestProfile.copy(tests = List(test))

      when(onlineTestsGatewayClientMock.downloadXmlReport(any[Int]))
        .thenReturn(Future.successful(testResult))

      when(phase2TestRepositoryMock.insertTestResult(any[String], any[CubiksTest], any[model.persisted.TestResult]))
        .thenReturn(Future.successful(()))
      when(phase2TestRepositoryMock.updateProgressStatus(any[String], any[ProgressStatus])).thenReturn(Future.successful(()))
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturn(
        Future.successful(Some(testProfile.copy(tests = List(test.copy(testResult = Some(savedResult))))))
      )

      phase2TestService.retrieveTestResult(Phase2TestGroupWithAppId(
        "appId", testProfile
      )).futureValue

      verify(auditServiceMock, times(2)).logEventNoRequest(any[String], any[Map[String, String]])
      verify(phase2TestRepositoryMock).updateProgressStatus(any[String], any[ProgressStatus])
    }

    "save a phase2 report for a candidate and not update progress status" in new TestFixture {
      val testReady = phase2Test.copy(reportId = Some(123), resultsReadyToDownload = true)
      val testNotReady = phase2Test.copy(reportId = None, resultsReadyToDownload = false)
      val testProfile = phase2TestProfile.copy(tests = List(testReady, testNotReady))

      when(onlineTestsGatewayClientMock.downloadXmlReport(any[Int]))
        .thenReturn(Future.successful(testResult))

      when(phase2TestRepositoryMock.insertTestResult(any[String], any[CubiksTest], any[model.persisted.TestResult]))
        .thenReturn(Future.successful(()))
      when(phase2TestRepositoryMock.updateProgressStatus(any[String], any[ProgressStatus])).thenReturn(Future.successful(()))
      when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturn(
        Future.successful(Some(testProfile.copy(tests = List(testReady.copy(testResult = Some(savedResult)), testNotReady))))
      )

      phase2TestService.retrieveTestResult(Phase2TestGroupWithAppId(
        "appId", testProfile
      )).futureValue

      verify(auditServiceMock, times(1)).logEventNoRequest(any[String], any[Map[String, String]])
      verify(phase2TestRepositoryMock, times(0)).updateProgressStatus(any[String], any[ProgressStatus])
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
      verifyZeroInteractions(emailClientMock)
    }
  }

  private def phase2Progress(phase2ProgressResponse: Phase2ProgressResponse) =
    ProgressResponse("appId", phase2ProgressResponse = phase2ProgressResponse)

  trait TestFixture extends StcEventServiceFixture {

    implicit val hc = mock[HeaderCarrier]
    implicit val rh = mock[RequestHeader]

    val dateTimeFactoryMock = mock[DateTimeFactory]
    val now = new DateTimeFactoryImpl().nowLocalTimeZone.withZone(DateTimeZone.UTC)
    when(dateTimeFactoryMock.nowLocalTimeZone).thenReturn(now)

    val scheduleCompletionBaseUrl = "http://localhost:9284/fset-fast-stream/online-tests/phase2"
    def availableSchedules = Map("oria" -> OriaSchedule, "daro" -> DaroSchedule)
    val testGatewayConfig =  OnlineTestsGatewayConfig(
      "",
      Phase1TestsConfig(expiryTimeInDays = 5,
        scheduleIds = Map("sjq" -> 16196, "bq" -> 16194),
        List("sjq", "bq"),
        List("sjq")
      ),
      phase2Tests = Phase2TestsConfig(expiryTimeInDays = 5, expiryTimeInDaysForInvigilatedETray = 90, availableSchedules, None),
      numericalTests = NumericalTestsConfig(Map(NumericalTestsConfig.numericalTestScheduleName -> NumericalTestSchedule(12345, 123))),
      reportConfig = ReportConfig(1, 2, "en-GB"),
      candidateAppUrl = "http://localhost:9284",
      emailDomain = "test.com"
    )

    val cubiksUserId = 123
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

    val postcode : Option[PostCode]= Some("WC2B 4")
    val emailContactDetails = "emailfjjfjdf@mailinator.com"
    val contactDetails = ContactDetails(outsideUk = false, Address("Aldwych road"), postcode, Some("UK"), emailContactDetails, "111111")

    val success = Future.successful(unit)
    val appRepositoryMock = mock[GeneralApplicationRepository]
    val cdRepositoryMock = mock[ContactDetailsRepository]
    val phase2TestRepositoryMock = mock[Phase2TestRepository]
    val onlineTestsGatewayClientMock = mock[OnlineTestsGatewayClient]
    val emailClientMock = mock[Phase2OnlineTestEmailClient]
    val auditServiceMock = mock[AuditService]
    val uuidFactoryMock = mock[UUIDFactory]
    val eventServiceMock = mock[StcEventService]
    val phase3TestServiceMock = mock[Phase3TestService]
    val siftServiceMock = mock[ApplicationSiftService]

    val tokens = UUIDFactory.generateUUID :: UUIDFactory.generateUUID :: Nil
    val registrations = Registration(123) :: Registration(456) :: Nil
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
    val emailCubiks = token + "@" + testGatewayConfig.emailDomain
    val registerApplicant = RegisterApplicant(preferredNameSanitized, lastName, emailCubiks)
    val registration = Registration(cubiksUserId)
    val scheduleId = 1

    val inviteApplicant = InviteApplicant(scheduleId,
      cubiksUserId, s"$scheduleCompletionBaseUrl/complete/$token",
      resultsURL = None, timeAdjustments = Nil
    )

    val onlineTestApplication2 = onlineTestApplication.copy(applicationId = "appId2", userId = "userId2")
    val adjustmentApplication = onlineTestApplication.copy(applicationId = "appId3", userId = "userId3", needsOnlineAdjustments = true)
    val adjustmentApplication2 = onlineTestApplication.copy(applicationId = "appId4", userId = "userId4", needsOnlineAdjustments = true)
    val candidates = List(onlineTestApplication, onlineTestApplication2)

    val registeredMap = Map(
      (registrations.head.userId, (onlineTestApplication, tokens.head, registrations.head)),
      (registrations.last.userId, (onlineTestApplication2, tokens.last, registrations.last))
    )

    val invites = List(Invitation(userId = registrations.head.userId, email = "email@test.com", accessCode = "accessCode",
      logonUrl = "logon.com", authenticateUrl = authenticateUrl, participantScheduleId = 999
    ),
      Invitation(userId = registrations.last.userId, email = "email@test.com", accessCode = "accessCode", logonUrl = "logon.com",
        authenticateUrl = authenticateUrl, participantScheduleId = 888
      ))

    val phase2Test = CubiksTest(scheduleId = OriaSchedule.scheduleId,
      usedForResults = true,
      cubiksUserId = cubiksUserId,
      token = token,
      testUrl = authenticateUrl,
      invitationDate = invitationDate,
      participantScheduleId = 999
    )

    val phase2TestProfile = Phase2TestGroup(expirationDate,
      List(phase2Test, phase2Test.copy(scheduleId = DaroSchedule.scheduleId))
    )
    val invigilatedTestProfile = Phase2TestGroup(
      invigilatedExpirationDate, List(phase2Test.copy(scheduleId = DaroSchedule.scheduleId, invigilatedAccessCode = Some("accessCode")))
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

    when(onlineTestsGatewayClientMock.registerApplicants(any[Int]))
      .thenReturn(Future.successful(registrations))

    when(onlineTestsGatewayClientMock.inviteApplicants(any[List[InviteApplicant]]))
      .thenReturn(Future.successful(invites))

    when(phase2TestRepositoryMock.insertOrUpdateTestGroup(any[String], any[Phase2TestGroup]))
      .thenReturn(Future.successful(()))

    when(phase2TestRepositoryMock.getTestGroup(any[String]))
      .thenReturn(Future.successful(Some(phase2TestProfile)))

    when(phase2TestRepositoryMock.resetTestProfileProgresses(any[String], any[List[ProgressStatus]]))
      .thenReturn(Future.successful(()))

    when(cdRepositoryMock.find(any[String])).thenReturn(Future.successful(
      ContactDetails(outsideUk = false, Address("Aldwych road"), Some("QQ1 1QQ"), Some("UK"), "email@test.com", "111111")))

    when(emailClientMock.sendOnlineTestInvitation(any[String], any[String], any[DateTime])(any[HeaderCarrier]))
      .thenReturn(Future.successful(()))

    when(phase2TestRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase2TestProfile)))
    when(phase2TestRepositoryMock.updateGroupExpiryTime(any[String], any[DateTime], any[String])).thenReturn(Future.successful(()))
    when(appRepositoryMock.removeProgressStatuses(any[String], any[List[ProgressStatus]])).thenReturn(Future.successful(()))
    when(phase2TestRepositoryMock.phaseName).thenReturn("phase2")

    when(uuidFactoryMock.generateUUID()).thenReturn(token)

    val appConfigMock = mock[MicroserviceAppConfig]
    when(appConfigMock.onlineTestsGatewayConfig).thenReturn(testGatewayConfig)

    val phase2TestService = new Phase2TestService(
      appConfigMock,
      appRepositoryMock,
      cdRepositoryMock,
      phase2TestRepositoryMock,
      onlineTestsGatewayClientMock,
      uuidFactoryMock,
      dateTimeFactoryMock,
      emailClientMock,
      auditServiceMock,
      ActorSystem(),
      stcEventServiceMock,
      authProviderClientMock,
      phase3TestServiceMock,
      siftServiceMock
    )
  }
}
