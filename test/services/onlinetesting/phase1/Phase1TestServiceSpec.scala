/*
 * Copyright 2018 HM Revenue & Customs
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

package services.onlinetesting.phase1

import akka.actor.ActorSystem
import config._
import connectors.ExchangeObjects._
import connectors.{ CSREmailClient, CubiksGatewayClient }
import factories.{ DateTimeFactory, UUIDFactory }
import model.Commands.PostCode
import model.Exceptions.ConnectorException
import model.OnlineTestCommands._
import model.ProgressStatuses.{ toString => _, _ }
import model.stc.StcEventTypes.{ toString => _ }
import model.exchange.CubiksTestResultReady
import model.persisted._
import model.{ ProgressStatuses, _ }
import org.joda.time.DateTime
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.PrivateMethodTester
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.onlinetesting.Phase1TestRepository
import services.AuditService
import services.sift.ApplicationSiftService
import services.stc.{ StcEventService, StcEventServiceFixture }
import testkit.{ ExtendedTimeout, UnitSpec, UnitWithAppSpec }

import scala.concurrent.duration.TimeUnit
import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.http.HeaderCarrier

class Phase1TestServiceSpec extends UnitSpec with ExtendedTimeout
  with PrivateMethodTester {
  implicit val ec: ExecutionContext = ExecutionContext.global
  val scheduleCompletionBaseUrl = "http://localhost:9284/fset-fast-stream/online-tests/phase1"
  val testGatewayConfig = CubiksGatewayConfig(
    "",
    Phase1TestsConfig(expiryTimeInDays = 5,
      scheduleIds = Map("sjq" -> 16196, "bq" -> 16194),
      List("sjq", "bq"),
      List("sjq")
    ),
    phase2Tests = Phase2TestsConfig(expiryTimeInDays = 5, expiryTimeInDaysForInvigilatedETray = 90,
      Map("daro" -> Phase2ScheduleExamples.DaroSchedule), None),
    numericalTests = NumericalTestsConfig(Map(NumericalTestsConfig.numericalTestScheduleName -> NumericalTestSchedule(12345, 123))),
    reportConfig = ReportConfig(1, 2, "en-GB"),
    candidateAppUrl = "http://localhost:9284",
    emailDomain = "test.com"
  )

  val sjqScheduleId = testGatewayConfig.phase1Tests.scheduleIds("sjq")
  val bqScheduleId = testGatewayConfig.phase1Tests.scheduleIds("bq")

  val preferredName = "Preferred\tName"
  val preferredNameSanitized = "Preferred Name"
  val lastName = ""
  val userId = "testUserId"

  val onlineTestApplication = OnlineTestApplication(applicationId = "appId",
    applicationStatus = ApplicationStatus.SUBMITTED,
    userId = userId,
    guaranteedInterview = false,
    needsOnlineAdjustments = false,
    needsAtVenueAdjustments = false,
    preferredName,
    lastName,
    None,
    None
  )

  val cubiksUserId = 98765
  val token = "token"
  val emailCubiks = token + "@" + testGatewayConfig.emailDomain
  val registerApplicant = RegisterApplicant(preferredNameSanitized, lastName, emailCubiks)
  val registration = Registration(cubiksUserId)

  val inviteApplicant = InviteApplicant(sjqScheduleId,
    cubiksUserId, s"$scheduleCompletionBaseUrl/complete/$token",
    resultsURL = None, timeAdjustments = Nil
  )

  val accessCode = "fdkfdfj"
  val logonUrl = "http://localhost/logonUrl"
  val authenticateUrl = "http://localhost/authenticate"
  val invitation = Invitation(cubiksUserId, emailCubiks, accessCode, logonUrl, authenticateUrl, sjqScheduleId)

  val invitationDate = DateTime.parse("2016-05-11")
  val startedDate = invitationDate.plusDays(1)
  val expirationDate = invitationDate.plusDays(5)

  val phase1TestBq = CubiksTest(scheduleId = testGatewayConfig.phase1Tests.scheduleIds("bq"),
    usedForResults = true,
    cubiksUserId = cubiksUserId,
    token = token,
    testUrl = authenticateUrl,
    invitationDate = invitationDate,
    participantScheduleId = 235
  )

  val phase1Test = CubiksTest(scheduleId = testGatewayConfig.phase1Tests.scheduleIds("sjq"),
    usedForResults = true,
    cubiksUserId = cubiksUserId,
    token = token,
    testUrl = authenticateUrl,
    invitationDate = invitationDate,
    participantScheduleId = 234
  )
  val phase1TestProfile = Phase1TestProfile(expirationDate,
    List(phase1Test)
  )

  val candidate = model.Candidate(userId = "user123", firstName = Some("Cid"),
    lastName = Some("Highwind"), preferredName = None, applicationId = Some("appId123"),
    email = Some("test@test.com"), dateOfBirth = None, address = None, postCode = None, country = None,
    applicationRoute = None, applicationStatus = None
  )

  val postcode : Option[PostCode]= Some("WC2B 4")
  val emailContactDetails = "emailfjjfjdf@mailinator.com"
  val contactDetails = ContactDetails(outsideUk = false, Address("Aldwych road"), postcode, Some("UK"), emailContactDetails, "111111")

  val auditDetails = Map("userId" -> userId)
  val auditDetailsWithEmail = auditDetails + ("email" -> emailContactDetails)

  val connectorErrorMessage = "Error in connector"

  val result = OnlineTestCommands.TestResult(status = "Completed",
                                             norm = "some norm",
                                             tScore = Some(23.9999d),
                                             percentile = Some(22.4d),
                                             raw = Some(66.9999d),
                                             sten = Some(1.333d)
  )

  val savedResult = persisted.TestResult(status = "Completed",
                                         norm = "some norm",
                                         tScore = Some(23.9999d),
                                         percentile = Some(22.4d),
                                         raw = Some(66.9999d),
                                         sten = Some(1.333d)
  )

  val applicationId = "31009ccc-1ac3-4d55-9c53-1908a13dc5e1"
  val expiredApplication = ExpiringOnlineTest(applicationId, userId, preferredName)
  val expiryReminder = NotificationExpiringOnlineTest(applicationId, userId, preferredName, expirationDate)
  val success = Future.successful(())

  "get online test" should {
    "return None if the application id does not exist" in new OnlineTest {
      when(otRepositoryMock.getTestGroup(any())).thenReturn(Future.successful(None))
      val result = phase1TestService.getTestGroup("nonexistent-userid").futureValue
      result mustBe None
    }

    val validExpireDate = new DateTime(2016, 6, 9, 0, 0)

    "return a valid set of aggregated online test data if the user id is valid" in new OnlineTest {
      when(appRepositoryMock.findCandidateByUserId(any[String])).thenReturn(Future.successful(
        Some(candidate)
      ))

      when(otRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(
        Some(Phase1TestProfile(expirationDate = validExpireDate,
          tests = List(phase1Test)
        ))
      ))

      val result = phase1TestService.getTestGroup("valid-userid").futureValue

      result.get.expirationDate must equal(validExpireDate)
    }
  }

  "register and invite application" should {
    "issue one email for invites to SJQ for GIS candidates" in new SuccessfulTestInviteFixture {
      when(otRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase1TestProfile)))
      when(otRepositoryMock.markTestAsInactive(any[Int])).thenReturn(Future.successful(()))
      when(otRepositoryMock.insertCubiksTests(any[String], any[Phase1TestProfile])).thenReturn(Future.successful(()))

      val result = phase1TestService
        .registerAndInviteForTestGroup(onlineTestApplication.copy(guaranteedInterview = true))

      result.futureValue mustBe unit

      verify(emailClientMock, times(1)).sendOnlineTestInvitation(
        eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate)
      )(any[HeaderCarrier])

      verify(auditServiceMock, times(1)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("UserInvitedToOnlineTest", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationEmailSent", auditDetailsWithEmail)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationProcessComplete", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvited", auditDetails)
      verify(auditServiceMock, times(5)).logEventNoRequest(any[String], any[Map[String, String]])
    }

    "issue one email for invites to SJQ and BQ tests for non GIS candidates" in new SuccessfulTestInviteFixture {
      when(otRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase1TestProfile)))
      when(otRepositoryMock.markTestAsInactive(any[Int])).thenReturn(Future.successful(()))
      when(otRepositoryMock.insertCubiksTests(any[String], any[Phase1TestProfile])).thenReturn(Future.successful(()))
      val result = phase1TestService.registerAndInviteForTestGroup(onlineTestApplication)

      result.futureValue mustBe unit

      verify(emailClientMock, times(1)).sendOnlineTestInvitation(
        eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate)
      )(any[HeaderCarrier])

      verify(auditServiceMock, times(2)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(2)).logEventNoRequest("UserInvitedToOnlineTest", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationEmailSent", auditDetailsWithEmail)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationProcessComplete", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvited", auditDetails)
      verify(auditServiceMock, times(7)).logEventNoRequest(any[String], any[Map[String, String]])
    }

    "fail if registration fails" in new OnlineTest {
      when(cubiksGatewayClientMock.registerApplicant(any[RegisterApplicant])).
        thenReturn(Future.failed(new ConnectorException(connectorErrorMessage)))

      val result = phase1TestService.registerAndInviteForTestGroup(onlineTestApplication)
      result.failed.futureValue mustBe a[ConnectorException]

      verify(auditServiceMock, times(0)).logEventNoRequest(any[String], any[Map[String, String]])
    }

    "fail and audit 'UserRegisteredForOnlineTest' if invitation fails" in new OnlineTest {
      when(cubiksGatewayClientMock.registerApplicant(any[RegisterApplicant]))
        .thenReturn(Future.successful(registration))
      when(cubiksGatewayClientMock.inviteApplicant(any[InviteApplicant])).thenReturn(
        Future.failed(new ConnectorException(connectorErrorMessage))
      )

      val result = phase1TestService.registerAndInviteForTestGroup(onlineTestApplication)
      result.failed.futureValue mustBe a[ConnectorException]

      verify(auditServiceMock, times(2)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(2)).logEventNoRequest(any[String], any[Map[String, String]])
    }

    "fail, audit 'UserRegisteredForOnlineTest' and audit 'UserInvitedToOnlineTest' " +
      "if there is an exception retrieving the contact details" in new OnlineTest {
      when(cubiksGatewayClientMock.registerApplicant(eqTo(registerApplicant)))
        .thenReturn(Future.successful(registration))
      when(cubiksGatewayClientMock.inviteApplicant(any[InviteApplicant]))
        .thenReturn(Future.successful(invitation))
      when(cdRepositoryMock.find(userId))
        .thenReturn(Future.failed(new Exception))

      val result = phase1TestService.registerAndInviteForTestGroup(onlineTestApplication)
      result.failed.futureValue mustBe an[Exception]

      verify(auditServiceMock, times(2)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(2)).logEventNoRequest("UserInvitedToOnlineTest", auditDetails)
      verify(auditServiceMock, times(4)).logEventNoRequest(any[String], any[Map[String, String]])
    }

    "fail, audit 'UserRegisteredForOnlineTest' and audit 'UserInvitedToOnlineTest'" +
      " if there is an exception sending the invitation email" in new OnlineTest {
      when(cubiksGatewayClientMock.registerApplicant(any[RegisterApplicant]))
        .thenReturn(Future.successful(registration))
      when(cubiksGatewayClientMock.inviteApplicant(any[InviteApplicant]))
        .thenReturn(Future.successful(invitation))
      when(cdRepositoryMock.find(userId))
        .thenReturn(Future.successful(contactDetails))

      when(emailClientMock.sendOnlineTestInvitation(
        eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate)
      )(any[HeaderCarrier]))
        .thenReturn(Future.failed(new Exception))

      val result = phase1TestService.registerAndInviteForTestGroup(onlineTestApplication)
      result.failed.futureValue mustBe an[Exception]

      verify(auditServiceMock, times(2)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(2)).logEventNoRequest("UserInvitedToOnlineTest", auditDetails)
      verify(auditServiceMock, times(4)).logEventNoRequest(any[String], any[Map[String, String]])
    }

    "fail, audit 'UserRegisteredForOnlineTest', audit 'UserInvitedToOnlineTest'" +
      ", not send invitation email to user" +
      "if there is an exception storing the status and the online profile data to database" in new OnlineTest {
      when(cubiksGatewayClientMock.registerApplicant(eqTo(registerApplicant)))
        .thenReturn(Future.successful(registration))
      when(cubiksGatewayClientMock.inviteApplicant(any[InviteApplicant]))
        .thenReturn(Future.successful(invitation))
      when(cdRepositoryMock.find(userId)).thenReturn(Future.successful(contactDetails))
      when(emailClientMock.sendOnlineTestInvitation(
        eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate))(any[HeaderCarrier])
      ).thenReturn(Future.successful(()))

      when(otRepositoryMock.insertOrUpdateTestGroup("appId", phase1TestProfile))
        .thenReturn(Future.failed(new Exception))

      val result = phase1TestService.registerAndInviteForTestGroup(onlineTestApplication)
      result.failed.futureValue mustBe an[Exception]

      verify(emailClientMock, times(0)).sendOnlineTestInvitation(any[String], any[String], any[DateTime])(
        any[HeaderCarrier]
      )
      verify(auditServiceMock, times(2)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(2)).logEventNoRequest("UserInvitedToOnlineTest", auditDetails)
      verify(auditServiceMock, times(4)).logEventNoRequest(any[String], any[Map[String, String]])
    }

    "audit 'OnlineTestInvitationProcessComplete' on success" in new OnlineTest {
      when(otRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase1TestProfile)))
      when(cubiksGatewayClientMock.registerApplicant(eqTo(registerApplicant)))
        .thenReturn(Future.successful(registration))
      when(cubiksGatewayClientMock.inviteApplicant(any[InviteApplicant]))
        .thenReturn(Future.successful(invitation))
      when(otRepositoryMock.markTestAsInactive(any[Int])).thenReturn(Future.successful(()))
      when(otRepositoryMock.insertCubiksTests(any[String], any[Phase1TestProfile])).thenReturn(Future.successful(()))
      when(cdRepositoryMock.find(any[String])).thenReturn(Future.successful(contactDetails))
      when(emailClientMock.sendOnlineTestInvitation(
        eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate))(
        any[HeaderCarrier]
      )).thenReturn(Future.successful(()))
      when(otRepositoryMock.insertOrUpdateTestGroup(any[String], any[Phase1TestProfile]))
        .thenReturn(Future.successful(()))

      val result = phase1TestService.registerAndInviteForTestGroup(onlineTestApplication)
      result.futureValue mustBe unit

      verify(emailClientMock, times(1)).sendOnlineTestInvitation(
        eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate)
      )(any[HeaderCarrier])

      verify(auditServiceMock, times(2)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(2)).logEventNoRequest("UserInvitedToOnlineTest", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationEmailSent", auditDetailsWithEmail)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationProcessComplete", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvited", auditDetails)
      verify(auditServiceMock, times(7)).logEventNoRequest(any[String], any[Map[String, String]])
    }
  }

  "get adjusted time" should {
    "return minimum if percentage is zero" in new OnlineTest {
      val result = phase1TestService.getAdjustedTime(minimum = 6, maximum = 12, percentageToIncrease = 0)
      result must be(6)
    }

    "return maximum if percentage is 100%" in new OnlineTest {
      val result = phase1TestService.getAdjustedTime(minimum = 6, maximum = 12, percentageToIncrease = 100)
      result must be(12)
    }

    "return maximum if percentage is over 100%" in new OnlineTest {
      val result = phase1TestService.getAdjustedTime(minimum = 6, maximum = 12, percentageToIncrease = 101)
      result must be(12)
    }

    "return adjusted time if percentage is above zero and below 100%" in new OnlineTest {
      val result = phase1TestService.getAdjustedTime(minimum = 6, maximum = 12, percentageToIncrease = 50)
      result must be(9)
    }
    "return adjusted time round up if percentage is above zero and below 100%" in new OnlineTest {
      val result = phase1TestService.getAdjustedTime(minimum = 6, maximum = 12, percentageToIncrease = 51)
      result must be(10)
    }
  }

  "build invite application" should {
     "return an InviteApplication for a GIS candidate" in new OnlineTest {
      val result = phase1TestService.buildInviteApplication(
        onlineTestApplication.copy(guaranteedInterview = true),
        token, cubiksUserId, sjqScheduleId
      )

      result mustBe inviteApplicant.copy(
        scheduleCompletionURL = s"$scheduleCompletionBaseUrl/complete/$token"
      )
    }

    "return an InviteApplication for a non GIS candidate" in new OnlineTest {
      val sjqInvite = phase1TestService.buildInviteApplication(onlineTestApplication,
        token, cubiksUserId, testGatewayConfig.phase1Tests.scheduleIds("sjq"))

      sjqInvite mustBe inviteApplicant.copy(
        scheduleID = sjqScheduleId,
        scheduleCompletionURL = s"$scheduleCompletionBaseUrl/continue/$token"
      )

      val bqInvite = phase1TestService.buildInviteApplication(onlineTestApplication,
        token, cubiksUserId, bqScheduleId)

      bqInvite mustBe inviteApplicant.copy(
        scheduleID = bqScheduleId,
        scheduleCompletionURL = s"$scheduleCompletionBaseUrl/complete/$token"
      )
    }
  }

  "mark as started" should {
    "change progress to started" in new OnlineTest {
      when(otRepositoryMock.updateTestStartTime(any[Int], any[DateTime])).thenReturn(Future.successful(()))
      when(otRepositoryMock.getTestProfileByCubiksId(cubiksUserId))
        .thenReturn(Future.successful(Phase1TestGroupWithUserIds("appId123", "userId", phase1TestProfile)))
      when(otRepositoryMock.updateProgressStatus("appId123", ProgressStatuses.PHASE1_TESTS_STARTED)).thenReturn(Future.successful(()))
      phase1TestService.markAsStarted(cubiksUserId).futureValue

      verify(otRepositoryMock).updateProgressStatus("appId123", ProgressStatuses.PHASE1_TESTS_STARTED)
    }
  }

  "mark as completed" should {
    "change progress to completed if there are all tests completed and the test profile hasn't expired" in new OnlineTest {
      when(otRepositoryMock.updateTestCompletionTime(any[Int], any[DateTime])).thenReturn(Future.successful(()))
      val phase1Tests = phase1TestProfile.copy(tests = phase1TestProfile.tests.map(t => t.copy(completedDateTime = Some(DateTime.now()))),
        expirationDate = DateTime.now().plusDays(2)
      )

      when(otRepositoryMock.getTestProfileByCubiksId(cubiksUserId))
        .thenReturn(Future.successful(Phase1TestGroupWithUserIds("appId123", "userId", phase1Tests)))
      when(otRepositoryMock.updateProgressStatus("appId123", ProgressStatuses.PHASE1_TESTS_COMPLETED)).thenReturn(Future.successful(()))

      phase1TestService.markAsCompleted(cubiksUserId).futureValue

      verify(otRepositoryMock).updateProgressStatus("appId123", ProgressStatuses.PHASE1_TESTS_COMPLETED)
    }
  }

  "mark report as ready to download" should {
    "not change progress if not all the active tests have reports ready" in new OnlineTest {
      val reportReady = CubiksTestResultReady(reportId = Some(1), reportStatus = "Ready", reportLinkURL = Some("www.report.com"))

      when(otRepositoryMock.getTestProfileByCubiksId(cubiksUserId)).thenReturn(
        Future.successful(Phase1TestGroupWithUserIds("appId", "userId", phase1TestProfile.copy(
          tests = List(phase1Test.copy(usedForResults = false, cubiksUserId = 123),
            phase1Test,
            phase1TestBq.copy(cubiksUserId = 789, resultsReadyToDownload = false)
          )
        )))
      )
      when(otRepositoryMock.updateTestReportReady(cubiksUserId, reportReady)).thenReturn(Future.successful(()))
      when(otRepositoryMock.updateProgressStatus(any[String], any[ProgressStatus])).thenReturn(Future.successful(()))

      val result = phase1TestService.markAsReportReadyToDownload(cubiksUserId, reportReady).futureValue

      verify(otRepositoryMock, times(0)).updateProgressStatus(any[String], any[ProgressStatus])
    }

    "change progress to reports ready if all the active tests have reports ready" in new OnlineTest {
      val reportReady = CubiksTestResultReady(reportId = Some(1), reportStatus = "Ready", reportLinkURL = Some("www.report.com"))

      when(otRepositoryMock.getTestProfileByCubiksId(cubiksUserId)).thenReturn(
        Future.successful(Phase1TestGroupWithUserIds("appId", "userId", phase1TestProfile.copy(
          tests = List(phase1Test.copy(usedForResults = false, cubiksUserId = 123),
            phase1Test.copy(resultsReadyToDownload = true),
            phase1TestBq.copy(cubiksUserId = 789, resultsReadyToDownload = true)
          )
        )))
      )
      when(otRepositoryMock.updateTestReportReady(cubiksUserId, reportReady)).thenReturn(Future.successful(()))
      when(otRepositoryMock.updateProgressStatus(any[String], any[ProgressStatus])).thenReturn(Future.successful(()))

      val result = phase1TestService.markAsReportReadyToDownload(cubiksUserId, reportReady).futureValue

      verify(otRepositoryMock).updateProgressStatus("appId", ProgressStatuses.PHASE1_TESTS_RESULTS_READY)
    }
  }

  "reset phase1 tests" should {
    "remove progress and register for new tests" in new SuccessfulTestInviteFixture {
      import ProgressStatuses._

      when(appRepositoryMock.findCandidateByUserId(any[String])).thenReturn(Future.successful(Some(candidate)))

      val phase1TestProfileWithStartedTests = phase1TestProfile.copy(tests = phase1TestProfile.tests
        .map(t => t.copy(startedDateTime = Some(startedDate))))

      val phase1TestProfileWithNewTest = phase1TestProfileWithStartedTests.copy(tests =
        List(phase1Test.copy(usedForResults = false), phase1Test))

      // expectations for 2 invocations
      when(otRepositoryMock.getTestGroup(any[String]))
        .thenReturn(Future.successful(Some(phase1TestProfileWithStartedTests)))
        .thenReturn(Future.successful(Some(phase1TestProfileWithNewTest)))

      when(otRepositoryMock.markTestAsInactive(any[Int])).thenReturn(Future.successful(()))
      when(otRepositoryMock.insertCubiksTests(any[String], any[Phase1TestProfile])).thenReturn(Future.successful(()))
      when(otRepositoryMock.resetTestProfileProgresses(any[String], any[List[ProgressStatus]])).thenReturn(Future.successful(()))
      phase1TestService.resetTests(onlineTestApplication, List("sjq"), "createdBy").futureValue

      verify(otRepositoryMock).resetTestProfileProgresses("appId",
        List(PHASE1_TESTS_STARTED, PHASE1_TESTS_COMPLETED, PHASE1_TESTS_RESULTS_RECEIVED, PHASE1_TESTS_RESULTS_READY,
          PHASE1_TESTS_FAILED, PHASE1_TESTS_FAILED_NOTIFIED, PHASE1_TESTS_FAILED_SDIP_AMBER, PHASE1_TESTS_FAILED_SDIP_GREEN))
      val expectedTestsAfterReset = List(phase1TestProfileWithStartedTests.tests.head.copy(usedForResults = false),
        phase1Test.copy(participantScheduleId = invitation.participantScheduleId))

      verify(otRepositoryMock).markTestAsInactive(cubiksUserId)
      verify(otRepositoryMock).insertCubiksTests(any[String], any[Phase1TestProfile])
    }
  }

  "retrieve phase 1 test report" should {
    "return an exception if there is an error retrieving one of the reports" in new OnlineTest {
      val failedTest = phase1Test.copy(scheduleId = 555, reportId = Some(2))
      val successfulTest = phase1Test.copy(scheduleId = 444, reportId = Some(1))

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

      val result = phase1TestService.retrieveTestResult(Phase1TestGroupWithUserIds(
        "appId", "userId", phase1TestProfile.copy(tests = List(successfulTest, failedTest))
      ))

      result.failed.futureValue mustBe an[Exception]
    }

    "save a phase1 report for a candidate and update progress status" in new OnlineTest {
      val test = phase1Test.copy(reportId = Some(123), resultsReadyToDownload = true)
      val testProfile = phase1TestProfile.copy(tests = List(test))

      when(cubiksGatewayClientMock.downloadXmlReport(any[Int]))
        .thenReturn(Future.successful(result))

      when(otRepositoryMock.insertTestResult(any[String], any[CubiksTest], any[persisted.TestResult])).thenReturn(Future.successful(()))
      when(otRepositoryMock.updateProgressStatus(any[String], any[ProgressStatus])).thenReturn(Future.successful(()))
      when(otRepositoryMock.getTestGroup(any[String])).thenReturn(
        Future.successful(Some(testProfile.copy(tests = List(test.copy(testResult = Some(savedResult))))))
      )

      phase1TestService.retrieveTestResult(Phase1TestGroupWithUserIds(
        "appId", "userId", testProfile
      )).futureValue

      verify(auditServiceMock, times(2)).logEventNoRequest(any[String], any[Map[String, String]])
      verify(otRepositoryMock).updateProgressStatus(any[String], any[ProgressStatus])
    }

    "save a phase1 report for a candidate and not update progress status" in new OnlineTest {
      val testReady = phase1Test.copy(reportId = Some(123), resultsReadyToDownload = true)
      val testNotReady = phase1Test.copy(reportId = None, resultsReadyToDownload = false)
      val testProfile = phase1TestProfile.copy(tests = List(testReady, testNotReady))

      when(cubiksGatewayClientMock.downloadXmlReport(any[Int]))
        .thenReturn(Future.successful(result))

      when(otRepositoryMock.insertTestResult(any[String], any[CubiksTest], any[persisted.TestResult])).thenReturn(Future.successful(()))
      when(otRepositoryMock.updateProgressStatus(any[String], any[ProgressStatus])).thenReturn(Future.successful(()))
      when(otRepositoryMock.getTestGroup(any[String])).thenReturn(
        Future.successful(Some(testProfile.copy(tests = List(testReady.copy(testResult = Some(savedResult)), testNotReady))))
      )

      phase1TestService.retrieveTestResult(Phase1TestGroupWithUserIds(
        "appId", "userId", testProfile
      )).futureValue

      verify(auditServiceMock, times(1)).logEventNoRequest(any[String], any[Map[String, String]])
      verify(otRepositoryMock, times(0)).updateProgressStatus(any[String], any[ProgressStatus])
    }

    "retrieve only reports which are not already saved" in new OnlineTest {
      when(cubiksGatewayClientMock.downloadXmlReport(123)).thenReturn(Future.successful(result))
      when(otRepositoryMock.insertTestResult(any[String], any[CubiksTest], any[model.persisted.TestResult])).thenReturn(Future.successful(()))
      when(otRepositoryMock.updateProgressStatus("appId", PHASE1_TESTS_RESULTS_RECEIVED)).thenReturn(Future.successful(()))

      val testWithoutResult = phase1Test.copy(reportId = Some(123), resultsReadyToDownload = true)
      val testWithResult = phase1Test.copy(reportId = Some(456), resultsReadyToDownload = true, testResult = Some(savedResult))
      val testProfileAfterTestResultInsertion = phase1TestProfile.copy(tests = List(
        testWithoutResult.copy(testResult = Some(savedResult)),
        testWithResult
      ))
      when(otRepositoryMock.getTestGroup(any[String])).thenReturn(Future.successful(Some(testProfileAfterTestResultInsertion)))

      val testProfile = phase1TestProfile.copy(tests = List(testWithoutResult, testWithResult))
      phase1TestService.retrieveTestResult(Phase1TestGroupWithUserIds("appId", "userId", testProfile)).futureValue

      verify(otRepositoryMock).insertTestResult("appId", testWithoutResult, savedResult)
      verify(otRepositoryMock).updateProgressStatus("appId", PHASE1_TESTS_RESULTS_RECEIVED)
      verify(cubiksGatewayClientMock, never).downloadXmlReport(456)
    }
  }

  "processNextExpiredTest" should {
    "do nothing if there are no expired application to process" in new OnlineTest {
      when(otRepositoryMock.nextExpiringApplication(Phase1ExpirationEvent)).thenReturn(Future.successful(None))
      phase1TestService.processNextExpiredTest(Phase1ExpirationEvent).futureValue mustBe unit
    }

    "update progress status and send an email to the user when a Faststream application is expired" in new OnlineTest {
      when(otRepositoryMock.nextExpiringApplication(Phase1ExpirationEvent)).thenReturn(Future.successful(Some(expiredApplication)))
      when(cdRepositoryMock.find(any[String])).thenReturn(Future.successful(contactDetails))
      when(appRepositoryMock.getApplicationRoute(any[String])).thenReturn(Future.successful(ApplicationRoute.Faststream))
      val results = List(SchemeEvaluationResult("Sdip", "Green"))
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatuses.ProgressStatus])).thenReturn(success)

      when(emailClientMock.sendEmailWithName(any[String], any[String], any[String])(any[HeaderCarrier])).thenReturn(success)

      val result = phase1TestService.processNextExpiredTest(Phase1ExpirationEvent)

      result.futureValue mustBe unit

      verify(cdRepositoryMock).find(userId)
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(applicationId, PHASE1_TESTS_EXPIRED)
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(applicationId, SIFT_ENTERED)
      verify(appRepositoryMock, never()).getCurrentSchemeStatus(applicationId)
      verify(appRepositoryMock, never()).updateCurrentSchemeStatus(applicationId, results)
      verify(siftServiceMock, never()).sendSiftEnteredNotification(applicationId)
      verify(emailClientMock).sendEmailWithName(emailContactDetails, preferredName, Phase1ExpirationEvent.template)
    }

    "not attempt progressing SdipFS application to SIFT_ENTERED in PHASE1" in new OnlineTest {
      when(otRepositoryMock.nextExpiringApplication(Phase1ExpirationEvent)).thenReturn(Future.successful(Some(expiredApplication)))
      when(cdRepositoryMock.find(any[String])).thenReturn(Future.successful(contactDetails))
      when(appRepositoryMock.getApplicationRoute(any[String])).thenReturn(Future.successful(ApplicationRoute.SdipFaststream))
      val results = Nil
      when(appRepositoryMock.getCurrentSchemeStatus(any[String])).thenReturn(Future.successful(results))
      when(appRepositoryMock.updateCurrentSchemeStatus(any[String], any[List[SchemeEvaluationResult]])).thenReturn(Future.successful(unit))
      when(otRepositoryMock.upsertTestGroupEvaluationResult(any[String], any[PassmarkEvaluation])).thenReturn(Future.successful(unit))
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatuses.ProgressStatus])).thenReturn(success)
      when(siftServiceMock.sendSiftEnteredNotification(applicationId)).thenReturn(Future.successful(()))

      when(emailClientMock.sendEmailWithName(any[String], any[String], any[String])(any[HeaderCarrier])).thenReturn(success)

      val result = phase1TestService.processNextExpiredTest(Phase1ExpirationEvent)

      result.futureValue mustBe unit

      verify(cdRepositoryMock).find(userId)
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(applicationId, PHASE1_TESTS_EXPIRED)
      verify(appRepositoryMock, never()).addProgressStatusAndUpdateAppStatus(applicationId, SIFT_ENTERED)
      verify(appRepositoryMock, never()).getCurrentSchemeStatus(applicationId)
      verify(appRepositoryMock, never()).updateCurrentSchemeStatus(applicationId, results)
      verify(siftServiceMock, never()).sendSiftEnteredNotification(applicationId)
      verify(emailClientMock).sendEmailWithName(emailContactDetails, preferredName, Phase1ExpirationEvent.template)
    }
  }

  "processNextTestForReminder" should {
    "do nothing if there are no application to process for reminders" in new OnlineTest {
      when(otRepositoryMock.nextTestForReminder(Phase1FirstReminder)).thenReturn(Future.successful(None))
      phase1TestService.processNextTestForReminder(Phase1FirstReminder).futureValue mustBe unit
    }

    "update progress status and send an email to the user when an application is about to expire" in new OnlineTest {
      when(otRepositoryMock.nextTestForReminder(Phase1FirstReminder)).thenReturn(Future.successful(Some(expiryReminder)))
      when(cdRepositoryMock.find(userId)).thenReturn(Future.successful(contactDetails))
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatuses.ProgressStatus])).thenReturn(success)
      when(emailClientMock.sendTestExpiringReminder(any[String], any[String], any[Int], any[TimeUnit], any[DateTime])
        (any[HeaderCarrier])).thenReturn(success)

      val result = phase1TestService.processNextTestForReminder(Phase1FirstReminder)

      result.futureValue mustBe unit

      verify(cdRepositoryMock).find(userId)
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(applicationId, PHASE1_TESTS_FIRST_REMINDER)
      verify(emailClientMock).sendTestExpiringReminder(
        emailContactDetails,
        preferredName,
        Phase1FirstReminder.hoursBeforeReminder,
        Phase1FirstReminder.timeUnit,
        expiryReminder.expiryDate)
    }
  }

  "Progress Sdip for SdipFaststream candidate" should {
    "Set the progress status the candidate has passed Sdip" in new OnlineTest {
      import scala.collection.JavaConversions._
      when(otRepositoryMock.updateProgressStatusOnly(any[String], any[ProgressStatus])).thenReturn(Future.successful(unit))

      val testProfileWithEvaluation = phase1TestProfile.copy(
        evaluation = Some(PassmarkEvaluation("version", None, result = List(SchemeEvaluationResult(SchemeId("Finance"), "Green"),
          SchemeEvaluationResult(SchemeId("Sdip"), "Green")), "version-res", None
        ))
      )

      val phase1TestGroup = Phase1TestGroupWithUserIds("appId1", "userId1", testProfileWithEvaluation)

      val eventCaptor = ArgumentCaptor.forClass(classOf[ProgressStatuses.ProgressStatus])

      val result = phase1TestService.progressSdipFaststreamCandidateForSdip(phase1TestGroup).futureValue

      verify(otRepositoryMock).updateProgressStatusOnly(any[String], eventCaptor.capture)
      eventCaptor.getAllValues.head.toString mustBe ProgressStatuses.getProgressStatusForSdipFsSuccess(ApplicationStatus.PHASE1_TESTS).toString
    }

    "Set the progress status the candidate has failed Sdip" in new OnlineTest {
      import scala.collection.JavaConversions._
      when(otRepositoryMock.updateProgressStatusOnly(any[String], any[ProgressStatus])).thenReturn(Future.successful(unit))

      val testProfileWithEvaluation = phase1TestProfile.copy(
        evaluation = Some(PassmarkEvaluation("version", None,
          result = List(SchemeEvaluationResult(SchemeId("Finance"), "Green"), SchemeEvaluationResult(SchemeId("Sdip"), "Red")),
          "version-res", None
        ))
      )

      val phase1TestGroup = Phase1TestGroupWithUserIds("appId1", "userId1", testProfileWithEvaluation)

      val eventCaptor = ArgumentCaptor.forClass(classOf[ProgressStatuses.ProgressStatus])

      val result = phase1TestService.progressSdipFaststreamCandidateForSdip(phase1TestGroup).futureValue

      verify(otRepositoryMock).updateProgressStatusOnly(any[String], eventCaptor.capture)
      eventCaptor.getAllValues.head.toString mustBe ProgressStatuses.getProgressStatusForSdipFsFailed(ApplicationStatus.PHASE1_TESTS).toString
    }
  }

  trait OnlineTest {
    implicit val hc = HeaderCarrier()
    implicit val rh = mock[RequestHeader]

    val appRepositoryMock = mock[GeneralApplicationRepository]
    val cdRepositoryMock = mock[ContactDetailsRepository]
    val otRepositoryMock = mock[Phase1TestRepository]
    val cubiksGatewayClientMock = mock[CubiksGatewayClient]
    val emailClientMock = mock[CSREmailClient]
    val auditServiceMock = mock[AuditService]
    val tokenFactoryMock = mock[UUIDFactory]
    val onlineTestInvitationDateFactoryMock = mock[DateTimeFactory]
    val eventServiceMock = mock[StcEventService]
    val siftServiceMock = mock[ApplicationSiftService]

    when(tokenFactoryMock.generateUUID()).thenReturn(token)
    when(onlineTestInvitationDateFactoryMock.nowLocalTimeZone).thenReturn(invitationDate)
    when(otRepositoryMock.resetTestProfileProgresses(any[String], any[List[ProgressStatus]])).thenReturn(Future.successful(()))

    val phase1TestService = new Phase1TestService with StcEventServiceFixture {
      override val delaySecsBetweenRegistrations = 0
      val appRepository = appRepositoryMock
      val cdRepository = cdRepositoryMock
      val testRepository = otRepositoryMock
      val cubiksGatewayClient = cubiksGatewayClientMock
      val emailClient = emailClientMock
      val auditService = auditServiceMock
      val tokenFactory = tokenFactoryMock
      val dateTimeFactory = onlineTestInvitationDateFactoryMock
      val gatewayConfig = testGatewayConfig
      val eventService = eventServiceMock
      val actor = ActorSystem()
      val siftService = siftServiceMock
    }
  }

  trait SuccessfulTestInviteFixture extends OnlineTest {
    when(cubiksGatewayClientMock.registerApplicant(eqTo(registerApplicant)))
      .thenReturn(Future.successful(registration))
    when(cubiksGatewayClientMock.inviteApplicant(any[InviteApplicant]))
      .thenReturn(Future.successful(invitation))
    when(cdRepositoryMock.find(any[String])).thenReturn(Future.successful(contactDetails))
    when(emailClientMock.sendOnlineTestInvitation(
      eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate))(
      any[HeaderCarrier]
    )).thenReturn(Future.successful(()))
    when(otRepositoryMock.insertOrUpdateTestGroup(any[String], any[Phase1TestProfile])).thenReturn(Future.successful(()))
    when(otRepositoryMock.resetTestProfileProgresses(any[String], any[List[ProgressStatus]])).thenReturn(Future.successful(()))
  }
}
