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

import akka.actor.ActorSystem
import config.Phase2ScheduleExamples._
import config._
import connectors.ExchangeObjects.{ Invitation, InviteApplicant, RegisterApplicant, Registration }
import connectors.{ CSREmailClient, CubiksGatewayClient }
import factories.{ DateTimeFactory, UUIDFactory }
import model.OnlineTestCommands.OnlineTestApplication
import model.PersistedObjects.ContactDetails
import model.ProgressStatuses.{ ProgressStatus, _ }
import model.events.DataStoreEvents
import model.exchange.CubiksTestResultReady
import model.persisted._
import model.{ Address, ApplicationStatus, ProgressStatuses }
import org.joda.time.DateTime
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.mvc.RequestHeader
import repositories.ContactDetailsRepository
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.Phase2TestRepository
import services.AuditService
import services.events.{ EventService, EventServiceFixture }
import services.onlinetesting.ResetPhase2Test.{ CannotResetPhase2Tests, ResetLimitExceededException }
import services.onlinetesting.phase2.ScheduleSelector
import testkit.ExtendedTimeout
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

class Phase2TestServiceSpec extends PlaySpec with MockitoSugar with ScalaFutures with ExtendedTimeout {

  "Register applicants" should {
    "correctly register a batch of candidates" in new Phase2TestServiceFixture {

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
    "correctly invite a batch of candidates" in new Phase2TestServiceFixture {
      val result = phase2TestService.inviteApplicants(registeredMap).futureValue

      result mustBe List(phase2TestService.Phase2TestInviteData(onlineTestApplication, 1, tokens.head,
        registrations.head, invites.head),
        phase2TestService.Phase2TestInviteData(onlineTestApplication2, scheduleId = DaroShedule.scheduleId, tokens.last,
          registrations.last, invites.last)
      )

      verify(auditServiceMock, times(2)).logEventNoRequest(eqTo("Phase2TestInvited"), any[Map[String, String]])
    }
  }

  "Register and Invite applicants" must {
    "email the candidate and send audit events" in new Phase2TestServiceFixture {
      phase2TestService.registerAndInviteForTestGroup(candidates).futureValue

      verify(auditServiceMock, times(2)).logEventNoRequest(eqTo("Phase2TestRegistered"), any[Map[String, String]])
      verify(auditServiceMock, times(2)).logEventNoRequest(eqTo("Phase2TestInvited"), any[Map[String, String]])
      verify(auditServiceMock, times(2)).logEventNoRequest(eqTo("Phase2TestInvitationProcessComplete"), any[Map[String, String]])
      verify(otRepositoryMock, times(2)).insertOrUpdateTestGroup(any[String], any[Phase2TestGroup])
    }

    "process adjustment candidates first and individually" ignore new Phase2TestServiceFixture {
     val adjustmentCandidates = candidates :+ adjustmentApplication :+ adjustmentApplication2
      when(cubiksGatewayClientMock.registerApplicants(any[Int])(any[HeaderCarrier]))
        .thenReturn(Future.successful(List(registrations.head)))

      when(cubiksGatewayClientMock.inviteApplicants(any[List[InviteApplicant]])(any[HeaderCarrier]))
        .thenReturn(Future.successful(List(invites.head)))

      phase2TestService.registerAndInviteForTestGroup(adjustmentCandidates).futureValue

      verify(auditServiceMock).logEventNoRequest(eqTo("Phase2TestRegistered"), any[Map[String, String]])
      verify(auditServiceMock).logEventNoRequest(eqTo("Phase2TestInvited"), any[Map[String, String]])
      verify(auditServiceMock).logEventNoRequest(eqTo("Phase2TestInvitationProcessComplete"), any[Map[String, String]])
      verify(otRepositoryMock).insertOrUpdateTestGroup(any[String], any[Phase2TestGroup])
      phase2TestService.verifyDataStoreEvents(1, "OnlineExerciseResultSent")
    }

    "exclude adjsutments candidates from the invite" in new Phase2TestServiceFixture {
      val adjustmentCandidates = candidates :+ adjustmentApplication :+ adjustmentApplication2
      phase2TestService.registerAndInviteForTestGroup(adjustmentCandidates).futureValue
      verify(auditServiceMock, times(0)).logEventNoRequest(eqTo("Phase2TestRegistered"), any[Map[String, String]])
      verify(auditServiceMock, times(0)).logEventNoRequest(eqTo("Phase2TestInvited"), any[Map[String, String]])
      verify(auditServiceMock, times(0)).logEventNoRequest(eqTo("Phase2TestInvitationProcessComplete"), any[Map[String, String]])
      verify(otRepositoryMock, times(0)).insertOrUpdateTestGroup(any[String], any[Phase2TestGroup])
      //phase2TestService.verifyDataStoreEvents(0, "OnlineExerciseResultSent")

    }
  }

  "mark as started" should {
    "change progress to started" in new Phase2TestServiceFixture {
      when(otRepositoryMock.insertOrUpdateTestGroup(any[String], any[Phase2TestGroup])).thenReturn(Future.successful(()))
      when(otRepositoryMock.getTestProfileByCubiksId(cubiksUserId))
        .thenReturn(Future.successful(Phase2TestGroupWithAppId("appId123", phase2TestProfile)))
      when(otRepositoryMock.updateProgressStatus("appId123", ProgressStatuses.PHASE2_TESTS_STARTED)).thenReturn(Future.successful(()))
      phase2TestService.markAsStarted(cubiksUserId).futureValue

      verify(otRepositoryMock).updateProgressStatus("appId123", ProgressStatuses.PHASE2_TESTS_STARTED)
    }
  }

  "mark as completed" should {
    "change progress to completed if there are all tests completed" in new Phase2TestServiceFixture {
      when(otRepositoryMock.insertOrUpdateTestGroup(any[String], any[Phase2TestGroup])).thenReturn(Future.successful(()))
      val phase1Tests = phase2TestProfile.copy(tests = phase2TestProfile.tests.map(t => t.copy(completedDateTime = Some(DateTime.now()))))
      when(otRepositoryMock.getTestProfileByCubiksId(cubiksUserId))
        .thenReturn(Future.successful(Phase2TestGroupWithAppId("appId123", phase1Tests)))
      when(otRepositoryMock.updateProgressStatus("appId123", ProgressStatuses.PHASE2_TESTS_COMPLETED)).thenReturn(Future.successful(()))
      phase2TestService.markAsCompleted(cubiksUserId).futureValue

      verify(otRepositoryMock).updateProgressStatus("appId123", ProgressStatuses.PHASE2_TESTS_COMPLETED)
    }
  }

  "mark report as ready to download" should {
    "not change progress if not all the active tests have reports ready" in new Phase2TestServiceFixture {
      val reportReady = CubiksTestResultReady(reportId = Some(1), reportStatus = "Ready", reportLinkURL = Some("www.report.com"))

      when(otRepositoryMock.getTestProfileByCubiksId(cubiksUserId)).thenReturn(
        Future.successful(Phase2TestGroupWithAppId("appId", phase2TestProfile.copy(
          tests = List(phase2Test.copy(usedForResults = false, cubiksUserId = 123),
            phase2Test,
            phase2Test.copy(cubiksUserId = 789, resultsReadyToDownload = false)
          )
        )))
      )
      when(otRepositoryMock.insertOrUpdateTestGroup(any[String], any[Phase2TestGroup]))
        .thenReturn(Future.successful(()))
      when(otRepositoryMock.updateProgressStatus(any[String], any[ProgressStatus]))
        .thenReturn(Future.successful(()))

      val result = phase2TestService.markAsReportReadyToDownload(cubiksUserId, reportReady).futureValue

      verify(otRepositoryMock, times(0)).updateProgressStatus(any[String], any[ProgressStatus])
    }

    "change progress to reports ready if all the active tests have reports ready" in new Phase2TestServiceFixture {
      val reportReady = CubiksTestResultReady(reportId = Some(1), reportStatus = "Ready", reportLinkURL = Some("www.report.com"))

      when(otRepositoryMock.getTestProfileByCubiksId(cubiksUserId)).thenReturn(
        Future.successful(Phase2TestGroupWithAppId("appId", phase2TestProfile.copy(
          tests = List(phase2Test.copy(usedForResults = false, cubiksUserId = 123),
            phase2Test,
            phase2Test.copy(cubiksUserId = 789, resultsReadyToDownload = true)
          )
        )))
      )
      when(otRepositoryMock.insertOrUpdateTestGroup(any[String], any[Phase2TestGroup]))
        .thenReturn(Future.successful(()))
      when(otRepositoryMock.updateProgressStatus(any[String], any[ProgressStatus]))
        .thenReturn(Future.successful(()))

      val result = phase2TestService.markAsReportReadyToDownload(cubiksUserId, reportReady).futureValue

      verify(otRepositoryMock).updateProgressStatus("appId", ProgressStatuses.PHASE2_TESTS_RESULTS_READY)
    }
  }

  "reset phase2 tests" should {
    "remove progress and register for new tests" in new Phase2TestServiceFixture {
      val expectedRegistration = registrations.head
      val expectedInvite = invites.head
      val phase2TestProfileWithStartedTests = phase2TestProfile.copy(tests = phase2TestProfile.tests
        .map(t => t.copy(scheduleId = 3, startedDateTime = Some(startedDate))))

      when(otRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase2TestProfileWithStartedTests)))
      when(cubiksGatewayClientMock.registerApplicants(any[Int])(any[HeaderCarrier]))
        .thenReturn(Future.successful(List(expectedRegistration)))
      when(cubiksGatewayClientMock.inviteApplicants(any[List[InviteApplicant]])(any[HeaderCarrier]))
        .thenReturn(Future.successful(List(expectedInvite)))

      phase2TestService.resetTests(onlineTestApplication, "createdBy").futureValue

      verify(otRepositoryMock).removeTestProfileProgresses("appId",
        List(PHASE2_TESTS_STARTED, PHASE2_TESTS_COMPLETED, PHASE2_TESTS_RESULTS_RECEIVED, PHASE2_TESTS_RESULTS_READY))
      val expectedTestsAfterReset = List(phase2TestProfileWithStartedTests.tests.head.copy(usedForResults = false),
        phase2Test.copy(scheduleId = DaroShedule.scheduleId, token = token, cubiksUserId = expectedRegistration.userId,
          participantScheduleId = expectedInvite.participantScheduleId))
      verify(otRepositoryMock).insertOrUpdateTestGroup(onlineTestApplication.applicationId,
        phase2TestProfile.copy(tests = expectedTestsAfterReset)
      )
      verify(phase2TestService.dataStoreEventHandlerMock).handle(DataStoreEvents.ETrayReset("appId", "createdBy"))(hc, rh)
    }
    "return reset limit exceeded exception" in new Phase2TestServiceFixture {
      val expectedRegistration = registrations.head
      val expectedInvite = invites.head
      val phase2TestProfileWithStartedTests = phase2TestProfile.copy(tests = phase2TestProfile.tests
        .map(t => t.copy(startedDateTime = Some(startedDate))))

      when(otRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase2TestProfileWithStartedTests)))
      when(cubiksGatewayClientMock.registerApplicants(any[Int])(any[HeaderCarrier]))
        .thenReturn(Future.successful(List(expectedRegistration)))
      when(cubiksGatewayClientMock.inviteApplicants(any[List[InviteApplicant]])(any[HeaderCarrier]))
        .thenReturn(Future.successful(List(expectedInvite)))

      an[ResetLimitExceededException] must be thrownBy
        Await.result(phase2TestService.resetTests(onlineTestApplication, "createdBy"), 1 seconds)
    }
    "return cannot reset phase2 tests exception" in new Phase2TestServiceFixture {
      when(otRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(None))

      an[CannotResetPhase2Tests] must be thrownBy
        Await.result(phase2TestService.resetTests(onlineTestApplication, "createdBy"), 1 seconds)
    }
  }

  trait Phase2TestServiceFixture {

    implicit val hc = mock[HeaderCarrier]
    implicit val rh = mock[RequestHeader]

    val scheduleCompletionBaseUrl = "http://localhost:9284/fset-fast-stream/online-tests/phase2"
    val gatewayConfigMock =  CubiksGatewayConfig(
      "",
      Phase1TestsConfig(expiryTimeInDays = 7,
        scheduleIds = Map("sjq" -> 16196, "bq" -> 16194),
        List("sjq", "bq"),
        List("sjq")
      ),
      competenceAssessment = CubiksGatewayStandardAssessment(31, 32),
      situationalAssessment = CubiksGatewayStandardAssessment(41, 42),
      phase2Tests = Phase2TestsConfig(expiryTimeInDays = 7, Map("daro" -> DaroShedule)),
      reportConfig = ReportConfig(1, 2, "en-GB"),
      candidateAppUrl = "http://localhost:9284",
      emailDomain = "test.com"
    )

    val cubiksUserId = 98765
    val token = "token"
    val authenticateUrl = "http://localhost/authenticate"
    val invitationDate = DateTime.parse("2016-05-11")
    val startedDate = invitationDate.plusDays(1)
    val expirationDate = invitationDate.plusDays(7)

    val appRepositoryMock = mock[GeneralApplicationRepository]
    val cdRepositoryMock = mock[ContactDetailsRepository]
    val otRepositoryMock = mock[Phase2TestRepository]
    val cubiksGatewayClientMock = mock[CubiksGatewayClient]
    val onlineTestInvitationDateFactoryMock = mock[DateTimeFactory]
    val emailClientMock = mock[CSREmailClient]
    var auditServiceMock = mock[AuditService]
    val tokenFactoryMock = mock[UUIDFactory]
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

    val preferredNameSanitized = "Preferred Name"
    val lastName = ""
    val emailCubiks = token + "@" + gatewayConfigMock.emailDomain
    val registerApplicant = RegisterApplicant(preferredNameSanitized, lastName, emailCubiks)
    val registration = Registration(cubiksUserId)
    val scheduleId = 1

    val inviteApplicant = InviteApplicant(scheduleId,
      cubiksUserId, s"$scheduleCompletionBaseUrl/complete/$token",
      resultsURL = None, timeAdjustments = Nil
    )

    val onlineTestApplication2 = onlineTestApplication.copy(applicationId = "appId2", userId = "userId2")
    val adjustmentApplication = onlineTestApplication.copy(applicationId = "appId3", userId = "userId3", needsAdjustments = true)
    val adjustmentApplication2 = onlineTestApplication.copy(applicationId = "appId4", userId = "userId4", needsAdjustments = true)
    val candidates = List(onlineTestApplication, onlineTestApplication2)

    val registeredMap = Map(
      registrations.head.userId -> (onlineTestApplication, tokens.head, registrations.head),
      registrations.last.userId -> (onlineTestApplication2, tokens.last, registrations.last)
    )

    val invites = List(Invitation(userId = registrations.head.userId, email = "email@test.com", accessCode = "accessCode",
       logonUrl = "logon.com", authenticateUrl = authenticateUrl, participantScheduleId = 999
      ),
      Invitation(userId = registrations.last.userId, email = "email@test.com", accessCode = "accessCode", logonUrl = "logon.com",
        authenticateUrl = authenticateUrl, participantScheduleId = 888
    ))

    val phase2Test = CubiksTest(scheduleId = 1,
      usedForResults = true,
      cubiksUserId = cubiksUserId,
      token = token,
      testUrl = authenticateUrl,
      invitationDate = invitationDate,
      participantScheduleId = 234
    )
    val phase2TestProfile = Phase2TestGroup(expirationDate,
      List(phase2Test)
    )

    when(cubiksGatewayClientMock.registerApplicants(any[Int])(any[HeaderCarrier]))
      .thenReturn(Future.successful(registrations))

    when(cubiksGatewayClientMock.inviteApplicants(any[List[InviteApplicant]])(any[HeaderCarrier]))
      .thenReturn(Future.successful(invites))

    when(otRepositoryMock.insertOrUpdateTestGroup(any[String], any[Phase2TestGroup]))
        .thenReturn(Future.successful(()))

    when(otRepositoryMock.getTestGroup(any[String]))
      .thenReturn(Future.successful(Some(phase2TestProfile)))

    when(otRepositoryMock.removeTestProfileProgresses(any[String], any[List[ProgressStatus]]))
      .thenReturn(Future.successful(()))

    when(cdRepositoryMock.find(any[String])).thenReturn(Future.successful(ContactDetails(
      Address("Aldwych road"), "QQ1 1QQ", "email@test.com", Some("111111")
    )))

    when(cdRepositoryMock.find(any[String])).thenReturn(Future.successful(ContactDetails(
      Address("Aldwych road"), "QQ1 1QQ", "email@test.com", Some("111111")
    )))

    when(emailClientMock.sendOnlineTestInvitation(any[String], any[String], any[DateTime])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))

    when(tokenFactoryMock.generateUUID()).thenReturn(token)

    when(onlineTestInvitationDateFactoryMock.nowLocalTimeZone).thenReturn(invitationDate)

    val phase2TestService = new Phase2TestService with EventServiceFixture with ScheduleSelector {
      val appRepository = appRepositoryMock
      val cdRepository = cdRepositoryMock
      val phase2TestRepo = otRepositoryMock
      val cubiksGatewayClient = cubiksGatewayClientMock
      val emailClient = emailClientMock
      val auditService = auditServiceMock
      val tokenFactory = tokenFactoryMock
      val dateTimeFactory = onlineTestInvitationDateFactoryMock
      val gatewayConfig = gatewayConfigMock
      val actor = ActorSystem()
    }
  }
}
