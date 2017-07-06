/*
 * Copyright 2017 HM Revenue & Customs
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

package services.onlinetesting.phase3

import config._
import connectors.CSREmailClient
import connectors.launchpadgateway.LaunchpadGatewayClient
import connectors.launchpadgateway.exchangeobjects.out._
import factories.{ DateTimeFactory, UUIDFactory }
import model.OnlineTestCommands.OnlineTestApplication
import model._
import model.command.{ Phase3ProgressResponse, ProgressResponse }
import model.persisted.phase3tests.{ LaunchpadTest, LaunchpadTestCallbacks, Phase3TestGroup }
import model.persisted.{ ContactDetails, Phase3TestGroupWithAppId }
import org.joda.time.DateTime
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.onlinetesting.Phase3TestRepository
import services.AuditService
import services.adjustmentsmanagement.AdjustmentsManagementService
import services.stc.StcEventServiceFixture
import testkit.{ ExtendedTimeout, UnitSpec }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class Phase3TestServiceSpec extends UnitSpec with ExtendedTimeout {

  "Register and Invite an applicant" should {

    "send audit events" in new Phase3TestServiceFixture {
      phase3TestServiceNoTestGroup.registerAndInviteForTestGroup(onlineTestApplication, testInterviewId, None).futureValue

      verifyDataStoreEvents(
        4,
        List(
          "VideoInterviewCandidateRegistered",
          "VideoInterviewInvited",
          "VideoInterviewRegistrationAndInviteComplete",
          "VideoInterviewInvitationEmailSent"
        )
      )

      verifyAuditEvents(
        4,
        List(
          "VideoInterviewCandidateRegistered",
          "VideoInterviewInvited",
          "VideoInterviewRegistrationAndInviteComplete",
          "VideoInterviewInvitationEmailSent"
        )
      )
    }

    "suppress the invitation email for invigilated applicants" in new Phase3TestServiceFixture {
      val videoAdjustments = AdjustmentDetail(timeNeeded = Some(10), invigilatedInfo = Some("blah blah"), otherInfo = Some("more blah"))
      val invigilatedApplicant = onlineTestApplication.copy(needsOnlineAdjustments = true, videoInterviewAdjustments = Some(videoAdjustments))
      phase3TestServiceNoTestGroupForInvigilated.registerAndInviteForTestGroup(invigilatedApplicant, testInterviewId, None).futureValue
      verify(emailClientMock, times(0)).sendOnlineTestInvitation(any[String], any[String], any[DateTime])(any[HeaderCarrier])
    }

    "invite and immediately extend invigilated applicants" in new Phase3TestServiceFixture {
      val videoAdjustments = AdjustmentDetail(timeNeeded = Some(10), invigilatedInfo = Some("blah blah"), otherInfo = Some("more blah"))
      val invigilatedApplicant = onlineTestApplication.copy(needsOnlineAdjustments = true, videoInterviewAdjustments = Some(videoAdjustments))
      phase3TestServiceNoTestGroupForInvigilated.registerAndInviteForTestGroup(invigilatedApplicant, testInterviewId, None).futureValue
      verify(phase3TestServiceNoTestGroupForInvigilated, times(1)).extendTestGroupExpiryTime(any(), any(),
        any())(any[HeaderCarrier](), any[RequestHeader]())
    }

    "invite and not immediately extend when the applicant is not invigilated" in new Phase3TestServiceFixture {
      phase3TestServiceNoTestGroup.registerAndInviteForTestGroup(onlineTestApplication, testInterviewId, None).futureValue

      verify(phase3TestServiceNoTestGroupForInvigilated, times(0)).extendTestGroupExpiryTime(any(), any(),
        any())(any[HeaderCarrier](), any[RequestHeader]())
    }

    "insert a valid test group" in new Phase3TestServiceFixture {
      phase3TestServiceNoTestGroup.registerAndInviteForTestGroup(onlineTestApplication, testInterviewId, None).futureValue

      verify(p3TestRepositoryMock).insertOrUpdateTestGroup(eqTo(onlineTestApplication.applicationId), eqTo(Phase3TestGroup(
        expectedFromNowExpiryTime,
        List(
          testPhase3Test.copy(startedDateTime = None)
        ),
        None
      )))
    }

    "call the register and invite methods of the launchpad gateway only once and with the correct arguments" in new Phase3TestServiceFixture {
      phase3TestServiceNoTestGroup.registerAndInviteForTestGroup(onlineTestApplication, testInterviewId, None).futureValue

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
          s"http://www.foo.com/test/interview/fset-fast-stream/online-tests/phase3/complete/$expectedCustomInviteId"
        )
      ))
    }

    "call the register and invite methods of the 0% adjusted interview id when a candidate has no adjustments" in new Phase3TestServiceFixture {
      phase3TestServiceNoTestGroup.registerAndInviteForTestGroup(onlineTestApplication).futureValue

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
          zeroPCAdjustedInterviewId,
          testLaunchpadCandidateId,
          expectedCustomInviteId,
          s"http://www.foo.com/test/interview/fset-fast-stream/online-tests/phase3/complete/$expectedCustomInviteId"
        )
      ))
    }

    "call the register and invite methods of the 33% interview id when a candidate has 33% time adjustments" in new Phase3TestServiceFixture {
      phase3TestServiceNoTestGroup.registerAndInviteForTestGroup(onlineTestApplicationWithThirtyThreeTimeAdjustment).futureValue

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
          thirtyThreePCAdjustedInterviewId,
          testLaunchpadCandidateId,
          expectedCustomInviteId,
          s"http://www.foo.com/test/interview/fset-fast-stream/online-tests/phase3/complete/$expectedCustomInviteId"
        )
      ))
    }
  }

  "Register and Invite an applicant to reschedule an interview with new adjustments" should {
    "send audit events" in new Phase3TestServiceFixture {

      phase3TestServiceForReschedule.registerAndInviteForTestGroup(
        onlineTestApplicationWithThirtyThreeTimeAdjustment, thirtyThreePCAdjustedInterviewId, Some(phase3TestGroupNotCompleted)
      ).futureValue

      verifyDataStoreEvents(
        4,
        List(
          "VideoInterviewRegistrationAndInviteComplete",
          "VideoInterviewInvited",
          "VideoInterviewInvitationEmailSent",
          "VideoInterviewExtended"
        )
      )
      verifyAuditEvents(
        4,
        List(
          "VideoInterviewRegistrationAndInviteComplete",
          "VideoInterviewInvited",
          "VideoInterviewInvitationEmailSent",
          "VideoInterviewExtended"
        )
      )
    }

    "invite to a new interview and reset statuses" in new Phase3TestServiceFixture {
      phase3TestServiceForReschedule.registerAndInviteForTestGroup(
        onlineTestApplicationWithThirtyThreeTimeAdjustment,
        thirtyThreePCAdjustedInterviewId,
        Some(phase3TestGroupNotCompleted)
      ).futureValue

      verify(launchpadGatewayClientMock, times(0)).registerApplicant(any())

      val expectedCustomInviteId = "FSINV-" + tokens.head
      verify(launchpadGatewayClientMock).inviteApplicant(eqTo(
        InviteApplicantRequest(
          thirtyThreePCAdjustedInterviewId,
          testLaunchpadCandidateId,
          expectedCustomInviteId,
          s"http://www.foo.com/test/interview/fset-fast-stream/online-tests/phase3/complete/$expectedCustomInviteId"
        )
      ))
      verify(p3TestRepositoryMock).resetTestProfileProgresses(any(), any())
    }
  }

  "Register and Invite an applicant to reschedule a started but not completed with no adjustments" should {
    "send audit events" in new Phase3TestServiceFixture {

      phase3TestServiceForReschedule.registerAndInviteForTestGroup(
        onlineTestApplication, testInterviewId, Some(phase3TestGroupNotCompleted)
      ).futureValue

      verifyDataStoreEvents(
        4,
        List(
          "VideoInterviewRegistrationAndInviteComplete",
          "VideoInterviewInvited",
          "VideoInterviewInvitationEmailSent",
          "VideoInterviewExtended"
        )
      )

      verifyAuditEvents(
        4,
        List(
          "VideoInterviewRegistrationAndInviteComplete",
          "VideoInterviewInvited",
          "VideoInterviewInvitationEmailSent",
          "VideoInterviewExtended"
        )
      )
    }

    "reset same new interview (reset interview in launchpad) and reset statuses" in new Phase3TestServiceFixture {
      phase3TestServiceForReschedule.registerAndInviteForTestGroup(
        onlineTestApplication,
        testInterviewId,
        Some(phase3TestGroupNotCompleted)
      ).futureValue

      verify(launchpadGatewayClientMock, times(0)).registerApplicant(any())
      verify(launchpadGatewayClientMock, times(0)).inviteApplicant(any())
      verify(launchpadGatewayClientMock, times(0)).retakeApplicant(any())
      val expectedCustomInviteId = "FSINV-" + tokens.head
      verify(launchpadGatewayClientMock).resetApplicant(eqTo(
        ResetApplicantRequest(
          testInterviewId,
          testLaunchpadCandidateId,
          phase3TestGroupNotCompleted.expirationDate.toLocalDate //,
        )
      ))
      verify(p3TestRepositoryMock).resetTestProfileProgresses(any(), any())
    }
  }

  "Register and Invite an applicant to reschedule a completed test with no adjustments" should {
    "send audit events" in new Phase3TestServiceFixture {

      phase3TestServiceForReschedule.registerAndInviteForTestGroup(
        onlineTestApplication, testInterviewId, Some(phase3TestGroupNotCompleted)
      ).futureValue

      verifyDataStoreEvents(
        4,
        List(
          "VideoInterviewRegistrationAndInviteComplete",
          "VideoInterviewInvited",
          "VideoInterviewInvitationEmailSent",
          "VideoInterviewExtended"
        )
      )

      verifyAuditEvents(
        4,
        List(
          "VideoInterviewRegistrationAndInviteComplete",
          "VideoInterviewInvited",
          "VideoInterviewInvitationEmailSent",
          "VideoInterviewExtended"
        )
      )
    }

    "invite to retake same new interview (reset interview in launchpad) and reset statuses" in new Phase3TestServiceFixture {
      phase3TestServiceForReschedule.registerAndInviteForTestGroup(
        onlineTestApplication,
        testInterviewId,
        Some(phase3TestGroupCompleted)
      ).futureValue

      verify(launchpadGatewayClientMock, times(0)).registerApplicant(any())
      verify(launchpadGatewayClientMock, times(0)).inviteApplicant(any())
      verify(launchpadGatewayClientMock, times(0)).resetApplicant(any())
      val expectedCustomInviteId = "FSINV-" + tokens.head
      verify(launchpadGatewayClientMock).retakeApplicant(eqTo(
        RetakeApplicantRequest(
          testInterviewId,
          testLaunchpadCandidateId,
          phase3TestGroupNotCompleted.expirationDate.toLocalDate //,
        )
      ))
      verify(p3TestRepositoryMock).resetTestProfileProgresses(any(), any())
    }
  }

  "mark as started" should {
    "change progress to started" in new Phase3TestServiceFixture {
      phase3TestServiceWithUnexpiredTestGroup.markAsStarted(testInviteId).futureValue

      verify(p3TestRepositoryMock).updateProgressStatus("appId123", ProgressStatuses.PHASE3_TESTS_STARTED)

      verifyDataStoreEvents(1, "VideoInterviewStarted")
      verifyAuditEvents(1, "VideoInterviewStarted")
    }
  }

  "mark as completed" should {
    "change progress to completed if there are all tests completed" in new Phase3TestServiceFixture {
      phase3TestServiceWithUnexpiredTestGroup.markAsCompleted(testInviteId).futureValue

      verify(p3TestRepositoryMock).updateProgressStatus("appId123", ProgressStatuses.PHASE3_TESTS_COMPLETED)

      verifyDataStoreEvents(1, "VideoInterviewCompleted")
      verifyAuditEvents(1, "VideoInterviewCompleted")
    }

    "change progress to completed if there are all tests completed and remove expired, if it was set" in new Phase3TestServiceFixture {
      phase3TestServiceWithExpiredTestGroup.markAsCompleted(testInviteId).futureValue

      verify(p3TestRepositoryMock).updateProgressStatus("appId123", ProgressStatuses.PHASE3_TESTS_COMPLETED)
      verify(appRepositoryMock).removeProgressStatuses("appId123", ProgressStatuses.PHASE3_TESTS_EXPIRED :: Nil)

      verifyDataStoreEvents(1, "VideoInterviewCompleted")
      verifyAuditEvents(1, "VideoInterviewCompleted")
    }
  }

  "mark as results received" should {
    "change progress to results received when any result set arrives" in new Phase3TestServiceFixture {
      phase3TestServiceWithUnexpiredTestGroup.markAsResultsReceived(testInviteId).futureValue

      verify(p3TestRepositoryMock).updateProgressStatus("appId123", ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED)

      verifyDataStoreEvents(1, "VideoInterviewResultsReceived")
      verifyAuditEvents(1, "VideoInterviewResultsReceived")
    }

    "change progress to results received when any result set arrives and unexpire the testgroup if expired" in new Phase3TestServiceFixture {
      phase3TestServiceWithExpiredTestGroup.markAsResultsReceived(testInviteId).futureValue

      verify(p3TestRepositoryMock).updateProgressStatus("appId123", ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED)
      verify(appRepositoryMock).removeProgressStatuses("appId123", ProgressStatuses.PHASE3_TESTS_EXPIRED :: Nil)

      verifyDataStoreEvents(1, "VideoInterviewResultsReceived")
      verifyAuditEvents(1, "VideoInterviewResultsReceived")
    }
  }

  "extend a test group's expiry" should {
    "throw IllegalStateException when there is no test group" in new Phase3TestServiceFixture {
      phase3TestServiceNoTestGroup.extendTestGroupExpiryTime("a", 1, "1").failed.futureValue mustBe an[IllegalStateException]

      verify(launchpadGatewayClientMock, times(0)).extendDeadline(any())
      verify(p3TestRepositoryMock, times(0)).updateGroupExpiryTime(any(), any(), any())
      verifyAuditEvents(0)
      verifyDataStoreEvents(0)
    }

    "extend a test group from the current time if it's expired" in new Phase3TestServiceFixture {
      val daysToExtend = 7
      phase3TestServiceWithExpiredTestGroup.extendTestGroupExpiryTime("a", daysToExtend, "A N User").futureValue

      val launchpadRequestCaptor = ArgumentCaptor.forClass(classOf[ExtendDeadlineRequest])
      val repositoryDateCaptor = ArgumentCaptor.forClass(classOf[DateTime])

      verify(launchpadGatewayClientMock, times(1)).extendDeadline(launchpadRequestCaptor.capture)
      verify(p3TestRepositoryMock, times(1)).updateGroupExpiryTime(any(), repositoryDateCaptor.capture, any())
      verifyAuditEvents(1, "VideoInterviewExtended")
      verifyDataStoreEvents(1, "VideoInterviewExtended")

      launchpadRequestCaptor.getValue.newDeadline mustBe expectedFromNowExpiryTime.toLocalDate
      repositoryDateCaptor.getValue mustBe expectedFromNowExpiryTime
    }

    "extend a test group from its expiry time if it's not expired" in new Phase3TestServiceFixture {
      val daysToExtend = 7
      phase3TestServiceWithUnexpiredTestGroup.extendTestGroupExpiryTime("a", daysToExtend, "A N User").futureValue

      val launchpadRequestCaptor = ArgumentCaptor.forClass(classOf[ExtendDeadlineRequest])
      val repositoryDateCaptor = ArgumentCaptor.forClass(classOf[DateTime])

      verify(launchpadGatewayClientMock, times(1)).extendDeadline(launchpadRequestCaptor.capture)
      verify(p3TestRepositoryMock, times(1)).updateGroupExpiryTime(any(), repositoryDateCaptor.capture, any())
      verifyAuditEvents(1, "VideoInterviewExtended")
      verifyDataStoreEvents(1, "VideoInterviewExtended")

      launchpadRequestCaptor.getValue.newDeadline mustBe expectedFromExistingExpiryExpiryTime.toLocalDate
      repositoryDateCaptor.getValue mustBe expectedFromExistingExpiryExpiryTime
    }
  }

  "add reset event" should {
    "capture video interview reset event when phase3 tests are completed" in new Phase3TestServiceFixture {
      phase3TestServiceWithCompletedTestGroup.addResetEventMayBe(testInviteId).futureValue
      verifyAuditEvents(1, "VideoInterviewReset")
      verifyDataStoreEvents(1, "VideoInterviewReset")
    }
    "capture video interview reset event when phase3 test results are completed" in new Phase3TestServiceFixture {
      phase3TestServiceWithResultsReceivedTestGroup.addResetEventMayBe(testInviteId).futureValue
      verifyAuditEvents(1, "VideoInterviewReset")
      verifyDataStoreEvents(1, "VideoInterviewReset")
    }
    "not capture reset event when phase3 tests are not completed or test results not received" in new Phase3TestServiceFixture {
      phase3TestServiceWithUnexpiredTestGroup.addResetEventMayBe(testInviteId).futureValue
      verifyAuditEvents(0)
      verifyDataStoreEvents(0)
    }
  }

  trait Phase3TestServiceFixture extends StcEventServiceFixture {

    implicit val hc = mock[HeaderCarrier]
    implicit val rh = mock[RequestHeader]

    val appRepositoryMock = mock[GeneralApplicationRepository]
    val cdRepositoryMock = mock[ContactDetailsRepository]
    val p3TestRepositoryMock = mock[Phase3TestRepository]
    val launchpadGatewayClientMock = mock[LaunchpadGatewayClient]
    val emailClientMock = mock[CSREmailClient]
    val auditServiceMock = mock[AuditService]
    val tokenFactoryMock = mock[UUIDFactory]
    val dateTimeFactoryMock = mock[DateTimeFactory]
    val adjustmentsManagementServiceMock = mock[AdjustmentsManagementService]
    val tokens = UUIDFactory.generateUUID() :: Nil

    val testFirstName = "Optimus"
    val testLastName = "Prime"

    val onlineTestApplication = OnlineTestApplication(
      applicationId = "appId",
      applicationStatus = ApplicationStatus.SUBMITTED,
      userId = "userId",
      guaranteedInterview = false,
      needsOnlineAdjustments = false,
      needsAtVenueAdjustments = false,
      preferredName = testFirstName,
      lastName = testLastName,
      None,
      None
    )
    val onlineTestApplicationWithThirtyThreeTimeAdjustment = onlineTestApplication.copy(
      videoInterviewAdjustments = Some(AdjustmentDetail(Some(33), None, None))
    )

    val onlineTestApplication2 = onlineTestApplication.copy(applicationId = "appId2", userId = "userId2")

    val testInterviewId = 123
    val testTimeNow = DateTime.parse("2016-10-01T00:00:01Z")
    val unexpiredTestExpiryTime = DateTime.parse("2016-11-01T00:00:01Z")
    val expectedFromNowExpiryTime = testTimeNow.plusDays(7)
    val expectedFromExistingExpiryExpiryTime = unexpiredTestExpiryTime.plusDays(7)
    val testExpiredTime = testTimeNow.minusDays(3)
    val testLaunchpadCandidateId = "CND_123"
    val testFaststreamCustomCandidateId = "FSCND_456"
    val testInviteId = "FSINV_123"
    val testCandidateRedirectUrl = "http://www.foo.com/test/interview"
    val testEmail = "foo@bar.com"

    val zeroPCAdjustedInterviewId = 12345
    val thirtyThreePCAdjustedInterviewId = 67890

    val gatewayConfigMock = LaunchpadGatewayConfig(
      "localhost",
      Phase3TestsConfig(
        timeToExpireInDays = 7,
        invigilatedTimeToExpireInDays = 90,
        candidateCompletionRedirectUrl = testCandidateRedirectUrl,
        Map(
          "0pc" -> zeroPCAdjustedInterviewId,
          "33pc" -> thirtyThreePCAdjustedInterviewId
        ),
        72,
        false
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
      Some(testTimeNow),
      None,
      LaunchpadTestCallbacks()
    )

    val testPhase3TestNotCompleted = LaunchpadTest(
      testInterviewId,
      usedForResults = true,
      "launchpad",
      testCandidateRedirectUrl,
      testInviteId,
      testLaunchpadCandidateId,
      testFaststreamCustomCandidateId,
      testTimeNow,
      Some(testTimeNow),
      None,
      LaunchpadTestCallbacks()
    )

    val testPhase3TestCompleted = testPhase3TestNotCompleted.copy(completedDateTime = Some(testTimeNow))

    val testTestGroup = Phase3TestGroup(
      expectedFromNowExpiryTime,
      List(testPhase3Test)
    )

    val phase3TestGroupUnexpiring = Phase3TestGroup(
      unexpiredTestExpiryTime,
      List(
        testPhase3Test
      ),
      None
    )

    val phase3TestGroupNotCompleted = Phase3TestGroup(
      unexpiredTestExpiryTime,
      List(
        testPhase3TestNotCompleted
      ),
      None
    )

    val phase3TestGroupCompleted = Phase3TestGroup(
      unexpiredTestExpiryTime,
      List(
        testPhase3TestCompleted
      ),
      None
    )

    // Common Mocks
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

    when(launchpadGatewayClientMock.retakeApplicant(any())).thenReturn {
      Future.successful(RetakeApplicantResponse(
        testFaststreamCustomCandidateId,
        testCandidateRedirectUrl,
        "Tomorrow"
      ))
    }

    when(launchpadGatewayClientMock.resetApplicant(any())).thenReturn {
      Future.successful(ResetApplicantResponse(
        testFaststreamCustomCandidateId,
        testCandidateRedirectUrl,
        "Tomorrow"
      ))
    }

    when(emailClientMock.sendOnlineTestInvitation(any(), any(), any())(any[HeaderCarrier]())).thenReturn(
      Future.successful(())
    )

    def noTestGroupMocks = {
      when(p3TestRepositoryMock.getTestGroup(any())).thenReturn(Future.successful(None))
      when(p3TestRepositoryMock.insertOrUpdateTestGroup(any(), any())).thenReturn(Future.successful(()))
      when(p3TestRepositoryMock.resetTestProfileProgresses(any(), any())).thenReturn(Future.successful(()))
      when(appRepositoryMock.removeProgressStatuses(any(), any())).thenReturn(Future.successful(()))
      when(appRepositoryMock.findProgress(any[String])).thenReturn(Future.successful(
        ProgressResponse("appId")
      ))
    }

    lazy val phase3TestServiceNoTestGroup = mockService {
      noTestGroupMocks
    }

    lazy val phase3TestServiceNoTestGroupForInvigilated = {
      noTestGroupMocks
      val service =
        new Phase3TestService {
          val appRepository = appRepositoryMock
          val testRepository = p3TestRepositoryMock
          val cdRepository = cdRepositoryMock
          val launchpadGatewayClient = launchpadGatewayClientMock
          val tokenFactory = tokenFactoryMock
          val dateTimeFactory = dateTimeFactoryMock
          val emailClient = emailClientMock
          val auditService = auditServiceMock
          val gatewayConfig = gatewayConfigMock
          val eventService = eventServiceMock
        }

      val phase3TestServiceSpy = spy(service)

      doReturn(Future.successful(()), Nil: _*).when(phase3TestServiceSpy).extendTestGroupExpiryTime(any(), any(),
        any())(any[HeaderCarrier](), any[RequestHeader]())

      phase3TestServiceSpy
    }

    lazy val phase3TestServiceWithUnexpiredTestGroup = mockService {
      when(p3TestRepositoryMock.getTestGroup(any())).thenReturn(Future.successful(Some(
        phase3TestGroupUnexpiring
      )))

      // Extensions
      when(p3TestRepositoryMock.updateGroupExpiryTime(any(), any(), any())).thenReturn(Future.successful(()))
      when(p3TestRepositoryMock.resetTestProfileProgresses(any(), any())).thenReturn(Future.successful(()))
      when(appRepositoryMock.removeProgressStatuses(any(), any())).thenReturn(Future.successful(()))
      when(launchpadGatewayClientMock.extendDeadline(any())).thenReturn(Future.successful(()))
      when(appRepositoryMock.findProgress(any[String])).thenReturn(Future.successful(ProgressResponse("appId")))

      // Mark As Started
      when(p3TestRepositoryMock.updateTestStartTime(any[String], any[DateTime])).thenReturn(Future.successful(()))
      when(p3TestRepositoryMock.getTestGroupByToken(testInviteId))
        .thenReturn(Future.successful(Phase3TestGroupWithAppId("appId123", testTestGroup)))
      when(p3TestRepositoryMock.updateProgressStatus("appId123", ProgressStatuses.PHASE3_TESTS_STARTED)).thenReturn(Future.successful(()))

      markAsCompleteMocks

      markAsResultsReceivedMocks
    }

    lazy val phase3TestServiceForReschedule = mockService {
      when(p3TestRepositoryMock.getTestGroup(any())).thenReturn(Future.successful(Some(
        phase3TestGroupUnexpiring
      )))

      // Extensions
      when(p3TestRepositoryMock.updateGroupExpiryTime(any(), any(), any())).thenReturn(Future.successful(()))
      when(p3TestRepositoryMock.resetTestProfileProgresses(any(), any())).thenReturn(Future.successful(()))
      when(p3TestRepositoryMock.insertOrUpdateTestGroup(any(), any())).thenReturn(Future.successful(()))
      when(p3TestRepositoryMock.resetTestProfileProgresses(any(), any())).thenReturn(Future.successful(()))
      when(p3TestRepositoryMock.getTestGroup(any())).thenReturn(Future.successful(Some(phase3TestGroupNotCompleted)))
      when(appRepositoryMock.removeProgressStatuses(any(), any())).thenReturn(Future.successful(()))
      when(launchpadGatewayClientMock.extendDeadline(any())).thenReturn(Future.successful(()))
      when(appRepositoryMock.findProgress(any[String])).thenReturn(Future.successful(
        ProgressResponse("appId")
      ))

      // Mark As Started
      when(p3TestRepositoryMock.updateTestStartTime(any[String], any[DateTime])).thenReturn(Future.successful(()))
      when(p3TestRepositoryMock.getTestGroupByToken(testInviteId))
        .thenReturn(Future.successful(Phase3TestGroupWithAppId("appId123", testTestGroup)))
      when(p3TestRepositoryMock.updateProgressStatus("appId123", ProgressStatuses.PHASE3_TESTS_STARTED)).thenReturn(Future.successful(()))

      markAsCompleteMocks

      markAsResultsReceivedMocks
    }

    private def markAsCompleteMocks = {
      when(p3TestRepositoryMock.getTestGroupByToken(testInviteId))
        .thenReturn(Future.successful(Phase3TestGroupWithAppId("appId123", testTestGroup)))
      when(p3TestRepositoryMock.updateProgressStatus("appId123", ProgressStatuses.PHASE3_TESTS_COMPLETED)).thenReturn(Future.successful(()))
      when(p3TestRepositoryMock.updateTestCompletionTime(any[String], any[DateTime])).thenReturn(Future.successful(()))
    }

    private def markAsResultsReceivedMocks = {
      when(p3TestRepositoryMock.updateProgressStatus("appId123", ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED))
        .thenReturn(Future.successful(()))
    }

    lazy val phase3TestServiceWithExpiredTestGroup = mockService {
      when(p3TestRepositoryMock.getTestGroup(any())).thenReturn(Future.successful(Some(
        Phase3TestGroup(
          testExpiredTime,
          List(
            testPhase3Test
          ),
          None
        )
      )))
      when(p3TestRepositoryMock.updateGroupExpiryTime(any(), any(), any())).thenReturn(Future.successful(()))
      when(appRepositoryMock.removeProgressStatuses(any(), any())).thenReturn(Future.successful(()))
      when(launchpadGatewayClientMock.extendDeadline(any())).thenReturn(Future.successful(()))
      when(appRepositoryMock.findProgress(any[String])).thenReturn(Future.successful(
        ProgressResponse(
          "appId",
          phase3ProgressResponse = Phase3ProgressResponse(
            phase3TestsExpired = true
          )
        )
      ))

      markAsCompleteMocks

      markAsResultsReceivedMocks
    }

    lazy val phase3TestServiceWithCompletedTestGroup = mockService {
      when(p3TestRepositoryMock.getTestGroupByToken(testInviteId))
        .thenReturn(Future.successful(Phase3TestGroupWithAppId("appId", Phase3TestGroup(
          testExpiredTime,
          List(
            testPhase3Test
          ),
          None
        ))))
      when(appRepositoryMock.findProgress(any[String])).thenReturn(Future.successful(
        ProgressResponse(
          "appId",
          phase3ProgressResponse = Phase3ProgressResponse(
            phase3TestsCompleted = true
          )
        )
      ))
    }

    lazy val phase3TestServiceWithResultsReceivedTestGroup = mockService {
      when(p3TestRepositoryMock.getTestGroupByToken(testInviteId))
        .thenReturn(Future.successful(Phase3TestGroupWithAppId("appId", Phase3TestGroup(
          testExpiredTime,
          List(
            testPhase3Test
          ),
          None
        ))))
      when(appRepositoryMock.findProgress(any[String])).thenReturn(Future.successful(
        ProgressResponse(
          "appId",
          phase3ProgressResponse = Phase3ProgressResponse(
            phase3TestsResultsReceived = true
          )
        )
      ))
    }

    def mockService(mockSetup: => Unit): Phase3TestService = {
      mockSetup
      new Phase3TestService {
        val appRepository = appRepositoryMock
        val testRepository = p3TestRepositoryMock
        val cdRepository = cdRepositoryMock
        val launchpadGatewayClient = launchpadGatewayClientMock
        val tokenFactory = tokenFactoryMock
        val dateTimeFactory = dateTimeFactoryMock
        val emailClient = emailClientMock
        val auditService = auditServiceMock
        val gatewayConfig = gatewayConfigMock
        val eventService = eventServiceMock
        val adjustmentsService = adjustmentsManagementServiceMock
      }
    }
  }

}
