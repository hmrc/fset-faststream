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
import connectors.ExchangeObjects.{ Invitation, InviteApplicant, RegisterApplicant, Registration, TimeAdjustments }
import connectors.{ CSREmailClient, CubiksGatewayClient }
import factories.{ DateTimeFactory, UUIDFactory }
import model.Commands.AdjustmentDetail
import model.OnlineTestCommands
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses.{ toString => _, _ }
import model.command.{ Phase2ProgressResponse, ProgressResponse }
import model.events.AuditEvents.Phase2TestInvitationProcessComplete
import model.events.DataStoreEvents
import model.events.DataStoreEvents.OnlineExerciseResultSent
import model.events.EventTypes.Events
import model.exchange.CubiksTestResultReady
import model.persisted.{ ContactDetails, Phase2TestGroup, Phase2TestGroupWithAppId, _ }
import model.{ Address, ApplicationStatus, ProgressStatuses }
import org.joda.time.{ DateTime, DateTimeZone }
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.onlinetesting.Phase2TestRepository
import services.AuditService
import services.events.{ EventService, EventServiceFixture }
import services.onlinetesting.ResetPhase2Test.{ CannotResetPhase2Tests, ResetLimitExceededException }
import services.onlinetesting.phase2.ScheduleSelector
import testkit.ExtendedTimeout
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps

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
      when(otRepositoryMock.markTestAsInactive(any[Int])).thenReturn(Future.successful(()))
      when(otRepositoryMock.insertCubiksTests(any[String], any[Phase1TestProfile])).thenReturn(Future.successful(()))
      phase2TestService.registerAndInviteForTestGroup(candidates).futureValue

      verify(auditServiceMock, times(2)).logEventNoRequest(eqTo("Phase2TestRegistered"), any[Map[String, String]])
      verify(auditServiceMock, times(2)).logEventNoRequest(eqTo("Phase2TestInvited"), any[Map[String, String]])
      verify(phase2TestService.auditEventHandlerMock, times(2)).handle(any[Phase2TestInvitationProcessComplete])(any[HeaderCarrier],
        any[RequestHeader])
      verify(phase2TestService.dataStoreEventHandlerMock, times(2)).handle(any[OnlineExerciseResultSent])(any[HeaderCarrier],
        any[RequestHeader])
      verify(auditServiceMock, times(2)).logEventNoRequest(eqTo("OnlineTestInvitationEmailSent"), any[Map[String, String]])
      
      verify(otRepositoryMock, times(2)).insertCubiksTests(any[String], any[Phase2TestGroup])
      verify(otRepositoryMock, times(2)).markTestAsInactive(any[Int])
    }

    "process adjustment candidates first and individually" ignore new Phase2TestServiceFixture {
     val adjustmentCandidates = candidates :+ adjustmentApplication :+ adjustmentApplication2
      when(cubiksGatewayClientMock.registerApplicants(any[Int]))
        .thenReturn(Future.successful(List(registrations.head)))

      when(cubiksGatewayClientMock.inviteApplicants(any[List[InviteApplicant]]))
        .thenReturn(Future.successful(List(invites.head)))

      phase2TestService.registerAndInviteForTestGroup(adjustmentCandidates).futureValue

      verify(auditServiceMock).logEventNoRequest(eqTo("Phase2TestRegistered"), any[Map[String, String]])
      verify(auditServiceMock).logEventNoRequest(eqTo("Phase2TestInvited"), any[Map[String, String]])
      verify(auditServiceMock).logEventNoRequest(eqTo("Phase2TestInvitationProcessComplete"), any[Map[String, String]])
      verify(otRepositoryMock).insertOrUpdateTestGroup(any[String], any[Phase2TestGroup])
      phase2TestService.verifyDataStoreEvents(1, "OnlineExerciseResultSent")
    }
  }

  "mark as started" should {
    "change progress to started" in new Phase2TestServiceFixture {
      when(otRepositoryMock.updateTestStartTime(any[Int], any[DateTime])).thenReturn(Future.successful(()))
      when(otRepositoryMock.getTestProfileByCubiksId(cubiksUserId))
        .thenReturn(Future.successful(Phase2TestGroupWithAppId("appId123", phase2TestProfile)))
      when(otRepositoryMock.updateProgressStatus("appId123", ProgressStatuses.PHASE2_TESTS_STARTED)).thenReturn(Future.successful(()))
      phase2TestService.markAsStarted(cubiksUserId).futureValue

      verify(otRepositoryMock).updateProgressStatus("appId123", ProgressStatuses.PHASE2_TESTS_STARTED)
    }
  }

  "mark as completed" should {
    "change progress to completed if there are all tests completed" in new Phase2TestServiceFixture {
      when(otRepositoryMock.updateTestCompletionTime(any[Int], any[DateTime])).thenReturn(Future.successful(()))
      val phase2Tests = phase2TestProfile.copy(tests = phase2TestProfile.tests.map(t => t.copy(completedDateTime = Some(DateTime.now()))),
        expirationDate = DateTime.now().plusDays(2)
      )

      when(otRepositoryMock.getTestProfileByCubiksId(cubiksUserId))
        .thenReturn(Future.successful(Phase2TestGroupWithAppId("appId123", phase2Tests)))
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
      when(otRepositoryMock.updateTestReportReady(cubiksUserId, reportReady))
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
            phase2Test.copy(resultsReadyToDownload = true),
            phase2Test.copy(cubiksUserId = 789, resultsReadyToDownload = true)
          )
        )))
      )
      when(otRepositoryMock.updateTestReportReady(cubiksUserId, reportReady))
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
      val phase2TestProfileWithNewTest = phase2TestProfileWithStartedTests.copy(tests =
        List(phase2Test.copy(usedForResults = false), phase2Test))

      // expectations for 3 invocations
      when(otRepositoryMock.getTestGroup(any[String]))
        .thenReturn(Future.successful(Some(phase2TestProfileWithStartedTests)))
        .thenReturn(Future.successful(Some(phase2TestProfileWithStartedTests)))
        .thenReturn(Future.successful(Some(phase2TestProfileWithNewTest)))

      when(otRepositoryMock.markTestAsInactive(any[Int])).thenReturn(Future.successful(()))
      when(otRepositoryMock.insertCubiksTests(any[String], any[Phase1TestProfile])).thenReturn(Future.successful(()))
      when(cubiksGatewayClientMock.registerApplicants(any[Int]))
        .thenReturn(Future.successful(List(expectedRegistration)))
      when(cubiksGatewayClientMock.inviteApplicants(any[List[InviteApplicant]]))
        .thenReturn(Future.successful(List(expectedInvite)))

      phase2TestService.resetTests(onlineTestApplication, "createdBy").futureValue

      verify(otRepositoryMock).removeTestProfileProgresses("appId",
        List(PHASE2_TESTS_STARTED, PHASE2_TESTS_COMPLETED, PHASE2_TESTS_RESULTS_RECEIVED, PHASE2_TESTS_RESULTS_READY))
      verify(otRepositoryMock).markTestAsInactive(cubiksUserId)
      verify(otRepositoryMock).insertCubiksTests(any[String], any[Phase2TestGroup])
      verify(phase2TestService.dataStoreEventHandlerMock).handle(DataStoreEvents.ETrayReset("appId", "createdBy"))(hc, rh)
    }

    "return reset limit exceeded exception" in new Phase2TestServiceFixture {
      val expectedRegistration = registrations.head
      val expectedInvite = invites.head
      val phase2TestProfileWithStartedTests = phase2TestProfile.copy(tests = phase2TestProfile.tests
        .map(t => t.copy(startedDateTime = Some(startedDate))))

      when(otRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase2TestProfileWithStartedTests)))
      when(cubiksGatewayClientMock.registerApplicants(any[Int]))
        .thenReturn(Future.successful(List(expectedRegistration)))
      when(cubiksGatewayClientMock.inviteApplicants(any[List[InviteApplicant]]))
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

  "Extend time for expired test" should {
    val progress = phase2Progress(
      Phase2ProgressResponse(
        phase2TestsExpired = true,
        phase2TestsFirstReminder = true,
        phase2TestsSecondReminder = true
      ))

    "extend the test to 7 days from now and remove: expired and two reminder progresses" in new Phase2TestServiceFixture {
      when(appRepositoryMock.findProgress(any[String])).thenReturn(Future.successful(progress))
      val phase2TestProfileWithExpirationInPast = Phase2TestGroup(now.minusDays(1), List(phase2Test))
      when(otRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase2TestProfileWithExpirationInPast)))

      phase2TestService.extendTestGroupExpiryTime("appId", 7, "admin").futureValue

      verify(otRepositoryMock).updateGroupExpiryTime("appId", now.plusDays(7), "phase2")
      verify(appRepositoryMock).removeProgressStatuses("appId", List(
        PHASE2_TESTS_EXPIRED, PHASE2_TESTS_SECOND_REMINDER, PHASE2_TESTS_FIRST_REMINDER)
      )
    }

    "extend the test to 3 days from now and remove: expired and only one reminder progresses" in new Phase2TestServiceFixture {
      when(appRepositoryMock.findProgress(any[String])).thenReturn(Future.successful(progress))
      val phase2TestProfileWithExpirationInPast = Phase2TestGroup(now.minusDays(1), List(phase2Test))
      when(otRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase2TestProfileWithExpirationInPast)))

      phase2TestService.extendTestGroupExpiryTime("appId", 3, "admin").futureValue

      verify(otRepositoryMock).updateGroupExpiryTime("appId", now.plusDays(3), "phase2")
      verify(appRepositoryMock).removeProgressStatuses("appId", List(
        PHASE2_TESTS_EXPIRED, PHASE2_TESTS_SECOND_REMINDER)
      )
    }

    "extend the test to 1 day from now and remove: expired progress" in new Phase2TestServiceFixture {
      when(appRepositoryMock.findProgress(any[String])).thenReturn(Future.successful(progress))
      val phase2TestProfileWithExpirationInPast = Phase2TestGroup(now.minusDays(1), List(phase2Test))
      when(otRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase2TestProfileWithExpirationInPast)))

      phase2TestService.extendTestGroupExpiryTime("appId", 1, "admin").futureValue

      verify(otRepositoryMock).updateGroupExpiryTime("appId", now.plusDays(1), "phase2")
      verify(appRepositoryMock).removeProgressStatuses("appId", List(PHASE2_TESTS_EXPIRED))
    }
  }

  "retrieve phase 2 test report" should {
    "return an exception if there is an error retrieving one of the reports" in new Phase2TestServiceFixture {
      val failedTest = phase2Test.copy(scheduleId = 555, reportId = Some(2))
      val successfulTest = phase2Test.copy(scheduleId = 444, reportId = Some(1))

      when(cubiksGatewayClientMock.downloadXmlReport(eqTo(successfulTest.reportId.get)))
        .thenReturn(Future.successful(OnlineTestCommands.TestResult(status = "Completed",
          norm = "some norm",
          tScore = Some(23.9999d),
          percentile = Some(22.4d),
          raw = Some(66.9999d),
          sten = Some(1.333d)
        )))

      when(cubiksGatewayClientMock.downloadXmlReport(eqTo(failedTest.reportId.get)))
        .thenReturn(Future.failed(new Exception))

      val result = phase2TestService.retrieveTestResult(Phase2TestGroupWithAppId(
        "appId", phase2TestProfile.copy(tests = List(successfulTest, failedTest))
      ))
    }

    "save a phase2 report for a candidate and update progress status" in new Phase2TestServiceFixture {
      val test = phase2Test.copy(reportId = Some(123), resultsReadyToDownload = true)
      val testProfile = phase2TestProfile.copy(tests = List(test))

      when(cubiksGatewayClientMock.downloadXmlReport(any[Int]))
        .thenReturn(Future.successful(testResult))

      when(otRepositoryMock.insertTestResult(any[String], any[CubiksTest], any[model.persisted.TestResult]))
        .thenReturn(Future.successful(()))
      when(otRepositoryMock.updateProgressStatus(any[String], any[ProgressStatus]))
        .thenReturn(Future.successful(()))
      when(otRepositoryMock.getTestGroup(any[String])).thenReturn(
        Future.successful(Some(testProfile.copy(tests = List(test.copy(testResult = Some(savedResult))))))
      )

      phase2TestService.retrieveTestResult(Phase2TestGroupWithAppId(
        "appId", testProfile
      )).futureValue

      verify(auditServiceMock, times(2)).logEventNoRequest(any[String], any[Map[String, String]])
      verify(otRepositoryMock).updateProgressStatus(any[String], any[ProgressStatus])
    }

    "save a phase2 report for a candidate and not update progress status" in new Phase2TestServiceFixture {
      val testReady = phase2Test.copy(reportId = Some(123), resultsReadyToDownload = true)
      val testNotReady = phase2Test.copy(reportId = None, resultsReadyToDownload = false)
      val testProfile = phase2TestProfile.copy(tests = List(testReady, testNotReady))

      when(cubiksGatewayClientMock.downloadXmlReport(any[Int]))
        .thenReturn(Future.successful(testResult))

      when(otRepositoryMock.insertTestResult(any[String], any[CubiksTest], any[model.persisted.TestResult]))
        .thenReturn(Future.successful(()))
      when(otRepositoryMock.updateProgressStatus(any[String], any[ProgressStatus]))
        .thenReturn(Future.successful(()))
      when(otRepositoryMock.getTestGroup(any[String])).thenReturn(
        Future.successful(Some(testProfile.copy(tests = List(testReady.copy(testResult = Some(savedResult)), testNotReady))))
      )

      phase2TestService.retrieveTestResult(Phase2TestGroupWithAppId(
        "appId", testProfile
      )).futureValue

      verify(auditServiceMock, times(1)).logEventNoRequest(any[String], any[Map[String, String]])
      verify(otRepositoryMock, times(0)).updateProgressStatus(any[String], any[ProgressStatus])
    }
  }

  "Extend time for test which has not expired yet" should {
    "extend the test to 7 days from expiration date which is in 1 day, remove two reminder progresses" in new Phase2TestServiceFixture {
      val progress = phase2Progress(
        Phase2ProgressResponse(
          phase2TestsFirstReminder = true,
          phase2TestsSecondReminder = true
        ))

      when(appRepositoryMock.findProgress(any[String])).thenReturn(Future.successful(progress))
      val phase2TestProfileWithExpirationInPast = Phase2TestGroup(now.plusDays(1), List(phase2Test))
      when(otRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase2TestProfileWithExpirationInPast)))

      phase2TestService.extendTestGroupExpiryTime("appId", 7, "admin").futureValue

      verify(otRepositoryMock).updateGroupExpiryTime("appId", now.plusDays(8), "phase2")
      verify(appRepositoryMock).removeProgressStatuses("appId", List(
        PHASE2_TESTS_SECOND_REMINDER, PHASE2_TESTS_FIRST_REMINDER)
      )
    }

    "extend the test to 2 days from expiration date which is in 1 day, remove one reminder progress" in new Phase2TestServiceFixture {
      val progress = phase2Progress(
        Phase2ProgressResponse(
          phase2TestsFirstReminder = true,
          phase2TestsSecondReminder = true
        ))

      when(appRepositoryMock.findProgress(any[String])).thenReturn(Future.successful(progress))
      val phase2TestProfileWithExpirationInPast = Phase2TestGroup(now.plusDays(1), List(phase2Test))
      when(otRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase2TestProfileWithExpirationInPast)))

      phase2TestService.extendTestGroupExpiryTime("appId", 2, "admin").futureValue

      val newExpirationDate = now.plusDays(3)
      verify(otRepositoryMock).updateGroupExpiryTime("appId", newExpirationDate, "phase2")
      verify(appRepositoryMock).removeProgressStatuses("appId", List(PHASE2_TESTS_SECOND_REMINDER))
    }

    "extend the test to 1 day from expiration date which is set to today, does not remove any progresses" in new Phase2TestServiceFixture {
      val progress = phase2Progress(
        Phase2ProgressResponse(
          phase2TestsFirstReminder = true,
          phase2TestsSecondReminder = true
        ))

      when(appRepositoryMock.findProgress(any[String])).thenReturn(Future.successful(progress))
      val phase2TestProfileWithExpirationInPast = Phase2TestGroup(now, List(phase2Test))
      when(otRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase2TestProfileWithExpirationInPast)))

      phase2TestService.extendTestGroupExpiryTime("appId", 1, "admin").futureValue

      val newExpirationDate = now.plusDays(1)
      verify(otRepositoryMock).updateGroupExpiryTime("appId", newExpirationDate, "phase2")
      verify(appRepositoryMock, never).removeProgressStatuses(any[String], any[List[ProgressStatus]])
    }
  }

  "build time adjustments" should {
    "return Nil when there is no need for adjustments and no gis" in new Phase2TestServiceFixture {
      val onlineTestApplicationWithNoAdjustments = OnlineTestApplication("appId1", "PHASE1_TESTS", "userId1", guaranteedInterview = false,
        needsOnlineAdjustments = false, needsAtVenueAdjustments = false, preferredName = "PrefName1", lastName = "LastName1",
        eTrayAdjustments = None, videoInterviewAdjustments = None)
      val result = phase2TestService.buildTimeAdjustments(5, onlineTestApplicationWithNoAdjustments)
      result mustBe List()
    }

    "return time adjustments when gis" in new Phase2TestServiceFixture {
      val onlineTestApplicationGisWithAdjustments = OnlineTestApplication("appId1", "PHASE1_TESTS", "userId1", guaranteedInterview = true,
        needsOnlineAdjustments = false, needsAtVenueAdjustments = false, preferredName = "PrefName1", lastName = "LastName1",
        eTrayAdjustments = Some(AdjustmentDetail(Some(25), None, None)), videoInterviewAdjustments = None)
      val result = phase2TestService.buildTimeAdjustments(5, onlineTestApplicationGisWithAdjustments)
      result mustBe List(TimeAdjustments(5, 1, 100))
    }

    "return time adjustments when adjustments needed" in new Phase2TestServiceFixture {
      val onlineTestApplicationGisWithAdjustments = OnlineTestApplication("appId1", "PHASE1_TESTS", "userId1", guaranteedInterview = false,
        needsOnlineAdjustments = true, needsAtVenueAdjustments = false, preferredName = "PrefName1", lastName = "LastName1",
        eTrayAdjustments = Some(AdjustmentDetail(Some(50), None, None)), videoInterviewAdjustments = None)
      val result = phase2TestService.buildTimeAdjustments(5, onlineTestApplicationGisWithAdjustments)
      result mustBe List(TimeAdjustments(5, 1, 120))
    }
  }

  "calculate absolute time with adjustments" should {
    "return 140 when adjustment is 75%" in new Phase2TestServiceFixture {
      val onlineTestApplicationGisWithAdjustments = OnlineTestApplication("appId1", "PHASE1_TESTS", "userId1", guaranteedInterview = true,
        needsOnlineAdjustments = true, needsAtVenueAdjustments = false, preferredName = "PrefName1", lastName = "LastName1",
        eTrayAdjustments = Some(AdjustmentDetail(Some(75), None, None)), videoInterviewAdjustments = None)
      val result = phase2TestService.calculateAbsoluteTimeWithAdjustments(onlineTestApplicationGisWithAdjustments)
      result mustBe 140
    }

    "return 80 when no adjustments needed" in new Phase2TestServiceFixture {
      val onlineTestApplicationGisWithNoAdjustments = OnlineTestApplication("appId1", "PHASE1_TESTS", "userId1", guaranteedInterview = true,
        needsOnlineAdjustments = false, needsAtVenueAdjustments = false, preferredName = "PrefName1", lastName = "LastName1",
        eTrayAdjustments = None, videoInterviewAdjustments = None)
      val result = phase2TestService.calculateAbsoluteTimeWithAdjustments(onlineTestApplicationGisWithNoAdjustments)
      result mustBe 80
    }
  }

  private def phase2Progress(phase2ProgressResponse: Phase2ProgressResponse) =
    ProgressResponse("appId", phase2ProgressResponse = phase2ProgressResponse)

  trait Phase2TestServiceFixture {

    implicit val hc = mock[HeaderCarrier]
    implicit val rh = mock[RequestHeader]

    val clock = mock[DateTimeFactory]
    val now = DateTimeFactory.nowLocalTimeZone.withZone(DateTimeZone.UTC)
    when(clock.nowLocalTimeZone).thenReturn(now)

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
    val invitationDate = now
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
      needsOnlineAdjustments = false,
      needsAtVenueAdjustments = false,
      preferredName = "Optimus",
      lastName = "Prime",
      None,
      None
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

    when(cubiksGatewayClientMock.registerApplicants(any[Int]))
      .thenReturn(Future.successful(registrations))

    when(cubiksGatewayClientMock.inviteApplicants(any[List[InviteApplicant]]))
      .thenReturn(Future.successful(invites))

    when(otRepositoryMock.insertOrUpdateTestGroup(any[String], any[Phase2TestGroup]))
        .thenReturn(Future.successful(()))

    when(otRepositoryMock.getTestGroup(any[String]))
      .thenReturn(Future.successful(Some(phase2TestProfile)))

    when(otRepositoryMock.removeTestProfileProgresses(any[String], any[List[ProgressStatus]]))
      .thenReturn(Future.successful(()))

    when(cdRepositoryMock.find(any[String])).thenReturn(Future.successful(
      ContactDetails(outsideUk = false, Address("Aldwych road"), Some("QQ1 1QQ"), Some("UK"), "email@test.com", "111111")))

    when(emailClientMock.sendOnlineTestInvitation(any[String], any[String], any[DateTime])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))

    when(otRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase2TestProfile)))
    when(otRepositoryMock.updateGroupExpiryTime(any[String], any[DateTime], any[String])).thenReturn(Future.successful(()))
    when(appRepositoryMock.removeProgressStatuses(any[String], any[List[ProgressStatus]])).thenReturn(Future.successful(()))
    when(otRepositoryMock.phaseName).thenReturn("phase2")

    when(tokenFactoryMock.generateUUID()).thenReturn(token)

    val phase2TestService = new Phase2TestService with EventServiceFixture with ScheduleSelector {
      val appRepository = appRepositoryMock
      val cdRepository = cdRepositoryMock
      val phase2TestRepo = otRepositoryMock
      val cubiksGatewayClient = cubiksGatewayClientMock
      val emailClient = emailClientMock
      val auditService = auditServiceMock
      val tokenFactory = tokenFactoryMock
      val dateTimeFactory = clock
      val gatewayConfig = gatewayConfigMock
      val actor = ActorSystem()
    }
  }
}
