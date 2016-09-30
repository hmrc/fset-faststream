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
import config._
import connectors.ExchangeObjects._
import connectors.{ CSREmailClient, CubiksGatewayClient }
import factories.{ DateTimeFactory, UUIDFactory }
import model.{ Address, ApplicationStatus, Commands, ProgressStatuses }
import model.Exceptions.ConnectorException
import model.OnlineTestCommands._
import model.PersistedObjects.ContactDetails
import model.ProgressStatuses.ProgressStatus
import model.persisted.Phase1TestProfileWithAppId
import org.joda.time.DateTime
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.{ BeforeAndAfterEach, PrivateMethodTester }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import repositories.application.{ GeneralApplicationRepository, OnlineTestRepository }
import repositories.{ ContactDetailsRepository, TestReportRepository }
import services.AuditService
import testkit.ExtendedTimeout
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

class OnlineTestServiceSpec extends PlaySpec with BeforeAndAfterEach with MockitoSugar with ScalaFutures with ExtendedTimeout
  with PrivateMethodTester {
  implicit val ec: ExecutionContext = ExecutionContext.global
  val scheduleCompletionBaseUrl = "http://localhost:9284/fset-fast-stream/online-tests/phase1"

  val testGatewayConfig = CubiksGatewayConfig(
    "",
    Phase1TestsConfig(expiryTimeInDays = 7,
      scheduleIds = Map("sjq" -> 16196, "bq" -> 16194),
      List("sjq", "bq"),
      List("sjq")
    ),
    competenceAssessment = CubiksGatewayStandardAssessment(31, 32),
    situationalAssessment = CubiksGatewayStandardAssessment(41, 42),
    ReportConfig(1, 2, "en-GB"),
    candidateAppUrl = "http://localhost:9284",
    emailDomain = "test.com"
  )

  val sjqScheduleId =testGatewayConfig.phase1Tests.scheduleIds("sjq")
  val bqScheduleId =testGatewayConfig.phase1Tests.scheduleIds("bq")

  val preferredName = "Preferred\tName"
  val preferredNameSanitized = "Preferred Name"
  val userId = "userId"

  val onlineTestApplication = OnlineTestApplication(applicationId = "appId",
    applicationStatus = ApplicationStatus.SUBMITTED,
    userId = userId,
    guaranteedInterview = false,
    needsAdjustments = false,
    preferredName,
    timeAdjustments = None
  )

  val cubiksUserId = 98765
  val lastName = ""
  val token = "token"
  val emailCubiks = token + "@" + testGatewayConfig.emailDomain
  val registerApplicant = RegisterApplicant(preferredNameSanitized, lastName, emailCubiks)
  val registration = Registration(cubiksUserId)

  val inviteApplicant = InviteApplicant(sjqScheduleId,
    cubiksUserId, s"$scheduleCompletionBaseUrl/complete/$token",
    resultsURL = None, timeAdjustments = None
  )

  val accessCode = "fdkfdfj"
  val logonUrl = "http://localhost/logonUrl"
  val authenticateUrl = "http://localhost/authenticate"
  val invitation = Invitation(cubiksUserId, emailCubiks, accessCode, logonUrl, authenticateUrl, sjqScheduleId)

  val invitationDate = DateTime.parse("2016-05-11")
  val startedDate = invitationDate.plusDays(1)
  val expirationDate = invitationDate.plusDays(7)
  val phase1Test = Phase1Test(scheduleId = testGatewayConfig.phase1Tests.scheduleIds("sjq"),
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

  val candidate = Commands.Candidate(userId = "user123", firstName = Some("Cid"),
    lastName = Some("Highwind"), preferredName = None, applicationId = Some("appId123"),
    email = Some("test@test.com"), dateOfBirth = None, address = None, postCode = None, country = None
  )

  val postcode = "WC2B 4"
  val emailContactDetails = "emailfjjfjdf@mailinator.com"
  val contactDetails = ContactDetails(Address("Aldwych road"), postcode, emailContactDetails, Some("111111"))

  val auditDetails = Map("userId" -> userId)
  val auditDetailsWithEmail = auditDetails + ("email" -> emailContactDetails)

  val connectorErrorMessage = "Error in connector"

  "get online test" should {
    "return None if the application id does not exist" in new OnlineTest {
      when(otRepositoryMock.getPhase1TestGroup(any())).thenReturn(Future.successful(None))
      val result = onlineTestService.getPhase1TestProfile("nonexistent-userid").futureValue
      result mustBe None
    }

    val validExpireDate = new DateTime(2016, 6, 9, 0, 0)

    "return a valid set of aggregated online test data if the user id is valid" in new OnlineTest {
      when(appRepositoryMock.findCandidateByUserId(any[String])).thenReturn(Future.successful(
        Some(candidate)
      ))

      when(otRepositoryMock.getPhase1TestGroup(any[String])).thenReturn(Future.successful(
        Some(Phase1TestProfile(expirationDate = validExpireDate,
          tests = List(phase1Test)
        ))
      ))

      val result = onlineTestService.getPhase1TestProfile("valid-userid").futureValue

      result.get.expirationDate must equal(validExpireDate)
//      result.get.activeTests.head.invitationDate must equal(InvitationDate)
    }
  }

  "register and invite application" should {
    "issue one email for invites to SJQ for GIS candidates" in new SuccessfulTestInviteFixture {
      when(otRepositoryMock.getPhase1TestGroup(any[String])).thenReturn(Future.successful(Some(phase1TestProfile)))

      val result = onlineTestService
        .registerAndInviteForTestGroup(onlineTestApplication.copy(guaranteedInterview = true))

      result.futureValue mustBe (())

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
      when(otRepositoryMock.getPhase1TestGroup(any[String])).thenReturn(Future.successful(Some(phase1TestProfile)))
      val result = onlineTestService.registerAndInviteForTestGroup(onlineTestApplication)

      result.futureValue mustBe (())

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
      when(cubiksGatewayClientMock.registerApplicant(any[RegisterApplicant])(any[HeaderCarrier])).
        thenReturn(Future.failed(new ConnectorException(connectorErrorMessage)))

      val result = onlineTestService.registerAndInviteForTestGroup(onlineTestApplication)
      result.failed.futureValue mustBe a[ConnectorException]

      verify(auditServiceMock, times(0)).logEventNoRequest(any[String], any[Map[String, String]])
    }

    "fail and audit 'UserRegisteredForOnlineTest' if invitation fails" in new OnlineTest {
      when(cubiksGatewayClientMock.registerApplicant(any[RegisterApplicant])(any[HeaderCarrier]))
        .thenReturn(Future.successful(registration))
      when(cubiksGatewayClientMock.inviteApplicant(any[InviteApplicant])(any[HeaderCarrier])).thenReturn(
        Future.failed(new ConnectorException(connectorErrorMessage))
      )

      val result = onlineTestService.registerAndInviteForTestGroup(onlineTestApplication)
      result.failed.futureValue mustBe a[ConnectorException]

      verify(auditServiceMock, times(2)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(2)).logEventNoRequest(any[String], any[Map[String, String]])
    }
    "fail, audit 'UserRegisteredForOnlineTest' and audit 'UserInvitedToOnlineTest' " +
      "if there is an exception retrieving the contact details" in new OnlineTest {
        when(cubiksGatewayClientMock.registerApplicant(eqTo(registerApplicant))(any[HeaderCarrier]))
          .thenReturn(Future.successful(registration))
        when(cubiksGatewayClientMock.inviteApplicant(any[InviteApplicant])(any[HeaderCarrier]))
          .thenReturn(Future.successful(invitation))
        when(cdRepositoryMock.find(userId))
          .thenReturn(Future.failed(new Exception))

        val result = onlineTestService.registerAndInviteForTestGroup(onlineTestApplication)
        result.failed.futureValue mustBe an[Exception]

        verify(auditServiceMock, times(2)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
        verify(auditServiceMock, times(2)).logEventNoRequest("UserInvitedToOnlineTest", auditDetails)
        verify(auditServiceMock, times(4)).logEventNoRequest(any[String], any[Map[String, String]])
      }
    "fail, audit 'UserRegisteredForOnlineTest' and audit 'UserInvitedToOnlineTest'" +
      " if there is an exception sending the invitation email" in new OnlineTest {
        when(cubiksGatewayClientMock.registerApplicant(any[RegisterApplicant])(any[HeaderCarrier]))
          .thenReturn(Future.successful(registration))
        when(cubiksGatewayClientMock.inviteApplicant(any[InviteApplicant])(any[HeaderCarrier]))
          .thenReturn(Future.successful(invitation))
        when(cdRepositoryMock.find(userId))
          .thenReturn(Future.successful(contactDetails))

        when(emailClientMock.sendOnlineTestInvitation(
          eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate)
        )(any[HeaderCarrier]))
          .thenReturn(Future.failed(new Exception))

        val result = onlineTestService.registerAndInviteForTestGroup(onlineTestApplication)
        result.failed.futureValue mustBe an[Exception]

        verify(auditServiceMock, times(2)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
        verify(auditServiceMock, times(2)).logEventNoRequest("UserInvitedToOnlineTest", auditDetails)
        verify(auditServiceMock, times(4)).logEventNoRequest(any[String], any[Map[String, String]])
      }
    "fail, audit 'UserRegisteredForOnlineTest', audit 'UserInvitedToOnlineTest'" +
      ", not send invitation email to user" +
      "if there is an exception storing the status and the online profile data to database" in new OnlineTest {
        when(cubiksGatewayClientMock.registerApplicant(eqTo(registerApplicant))(any[HeaderCarrier]))
          .thenReturn(Future.successful(registration))
        when(cubiksGatewayClientMock.inviteApplicant(any[InviteApplicant])(any[HeaderCarrier]))
          .thenReturn(Future.successful(invitation))
        when(cdRepositoryMock.find(userId)).thenReturn(Future.successful(contactDetails))
        when(emailClientMock.sendOnlineTestInvitation(
          eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate))(any[HeaderCarrier])
        ).thenReturn(Future.successful(()))


      when(otRepositoryMock.insertOrUpdatePhase1TestGroup("appId", phase1TestProfile))
        .thenReturn(Future.failed(new Exception))
      when(trRepositoryMock.remove("appId")).thenReturn(Future.successful(()))

      val result = onlineTestService.registerAndInviteForTestGroup(onlineTestApplication)
      result.failed.futureValue mustBe an[Exception]

      verify(emailClientMock, times(0)).sendOnlineTestInvitation(any[String], any[String], any[DateTime])(
        any[HeaderCarrier]
      )
      verify(auditServiceMock, times(2)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(2)).logEventNoRequest("UserInvitedToOnlineTest", auditDetails)
      verify(auditServiceMock, times(4)).logEventNoRequest(any[String], any[Map[String, String]])
    }

    "audit 'OnlineTestInvitationProcessComplete' on success" in new OnlineTest {
      when(otRepositoryMock.getPhase1TestGroup(any[String])).thenReturn(Future.successful(Some(phase1TestProfile)))
      when(cubiksGatewayClientMock.registerApplicant(eqTo(registerApplicant))(any[HeaderCarrier]))
        .thenReturn(Future.successful(registration))
      when(cubiksGatewayClientMock.inviteApplicant(any[InviteApplicant])(any[HeaderCarrier]))
        .thenReturn(Future.successful(invitation))
      when(cdRepositoryMock.find(any[String])).thenReturn(Future.successful(contactDetails))
      when(emailClientMock.sendOnlineTestInvitation(
        eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate))(
        any[HeaderCarrier]
      )).thenReturn(Future.successful(()))
      when(otRepositoryMock.insertOrUpdatePhase1TestGroup(any[String], any[Phase1TestProfile]))
        .thenReturn(Future.successful(()))
      when(trRepositoryMock.remove(any[String])).thenReturn(Future.successful(()))

      val result = onlineTestService.registerAndInviteForTestGroup(onlineTestApplication)
      result.futureValue mustBe (())

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
      val result = onlineTestService.getAdjustedTime(minimum = 6, maximum = 12, percentageToIncrease = 0)
      result must be(6)
    }
    "return maximum if percentage is 100%" in new OnlineTest {
      val result = onlineTestService.getAdjustedTime(minimum = 6, maximum = 12, percentageToIncrease = 100)
      result must be(12)
    }
    "return maximum if percentage is over 100%" in new OnlineTest {
      val result = onlineTestService.getAdjustedTime(minimum = 6, maximum = 12, percentageToIncrease = 101)
      result must be(12)
    }
    "return adjusted time if percentage is above zero and below 100%" in new OnlineTest {
      val result = onlineTestService.getAdjustedTime(minimum = 6, maximum = 12, percentageToIncrease = 50)
      result must be(9)
    }
    "return adjusted time round up if percentage is above zero and below 100%" in new OnlineTest {
      val result = onlineTestService.getAdjustedTime(minimum = 6, maximum = 12, percentageToIncrease = 51)
      result must be(10)
    }
  }

  "build invite application" should {
    "return an InviteApplication for a GIS candidate" in new OnlineTest {
      val result = onlineTestService.buildInviteApplication(
        onlineTestApplication.copy(guaranteedInterview = true),
        token, cubiksUserId, sjqScheduleId
      )

      result mustBe inviteApplicant.copy(
        scheduleCompletionURL = s"$scheduleCompletionBaseUrl/complete/$token"
      )
    }

    "return an InviteApplication for a non GIS candidate" in new OnlineTest {
      val sjqInvite = onlineTestService.buildInviteApplication(onlineTestApplication,
        token, cubiksUserId, testGatewayConfig.phase1Tests.scheduleIds("sjq"))

      sjqInvite mustBe inviteApplicant.copy(
        scheduleID = sjqScheduleId,
        scheduleCompletionURL = s"$scheduleCompletionBaseUrl/continue/$token"
      )

      val bqInvite = onlineTestService.buildInviteApplication(onlineTestApplication,
        token, cubiksUserId, bqScheduleId)

      bqInvite mustBe inviteApplicant.copy(
        scheduleID = bqScheduleId,
        scheduleCompletionURL = s"$scheduleCompletionBaseUrl/complete/$token"
      )
    }

  }

  "mark as started" should {
    "change progress to started" in new OnlineTest {
      when(otRepositoryMock.insertOrUpdatePhase1TestGroup(any[String], any[Phase1TestProfile])).thenReturn(Future.successful(()))
      when(otRepositoryMock.getPhase1TestProfileByCubiksId(cubiksUserId))
        .thenReturn(Future.successful(Phase1TestProfileWithAppId("appId123", phase1TestProfile)))
      when(otRepositoryMock.updateProgressStatus("appId123", ProgressStatuses.PHASE1_TESTS_STARTED)).thenReturn(Future.successful(()))
      onlineTestService.markAsStarted(cubiksUserId).futureValue

      verify(otRepositoryMock).updateProgressStatus("appId123", ProgressStatuses.PHASE1_TESTS_STARTED)
    }
  }

  "mark as completed" should {
    "change progress to completed if there are all tests completed" in new OnlineTest {
      when(otRepositoryMock.insertOrUpdatePhase1TestGroup(any[String], any[Phase1TestProfile])).thenReturn(Future.successful(()))
      val phase1Tests = phase1TestProfile.copy(tests = phase1TestProfile.tests.map(t => t.copy(completedDateTime = Some(DateTime.now()))))
      when(otRepositoryMock.getPhase1TestProfileByCubiksId(cubiksUserId))
        .thenReturn(Future.successful(Phase1TestProfileWithAppId("appId123", phase1Tests)))
      when(otRepositoryMock.updateProgressStatus("appId123", ProgressStatuses.PHASE1_TESTS_COMPLETED)).thenReturn(Future.successful(()))
      onlineTestService.markAsCompleted(cubiksUserId).futureValue

      verify(otRepositoryMock).updateProgressStatus("appId123", ProgressStatuses.PHASE1_TESTS_COMPLETED)
    }
  }


  "reset phase1 tests" should {
    "remove progress and register for new tests" in new SuccessfulTestInviteFixture {
      import ProgressStatuses._

      when(appRepositoryMock.findCandidateByUserId(any[String])).thenReturn(Future.successful(Some(candidate)))
      val phase1TestProfileWithStartedTests = phase1TestProfile.copy(tests = phase1TestProfile.tests
        .map(t => t.copy(startedDateTime = Some(startedDate))))
      when(otRepositoryMock.getPhase1TestGroup(any[String])).thenReturn(Future.successful(Some(phase1TestProfileWithStartedTests)))
      when(otRepositoryMock.removePhase1TestProfileProgresses(any[String], any[List[ProgressStatus]])).thenReturn(Future.successful(()))
      val result = onlineTestService.resetPhase1Tests(onlineTestApplication, List("sjq")).futureValue

      verify(otRepositoryMock).removePhase1TestProfileProgresses(
        "appId",
        List(PHASE1_TESTS_STARTED, PHASE1_TESTS_COMPLETED, PHASE1_TESTS_RESULTS_RECEIVED))
      val expectedTestsAfterReset = List(phase1TestProfileWithStartedTests.tests.head.copy(usedForResults = false),
        phase1Test.copy(participantScheduleId = invitation.participantScheduleId))
      verify(otRepositoryMock).insertOrUpdatePhase1TestGroup(
        "appId",
        phase1TestProfile.copy(tests = expectedTestsAfterReset)
      )
    }
  }

  trait OnlineTest {
    implicit val hc = HeaderCarrier()

    val appRepositoryMock = mock[GeneralApplicationRepository]
    val cdRepositoryMock = mock[ContactDetailsRepository]
    val otRepositoryMock = mock[OnlineTestRepository]
    val trRepositoryMock = mock[TestReportRepository]
    val cubiksGatewayClientMock = mock[CubiksGatewayClient]
    val emailClientMock = mock[CSREmailClient]
    var auditServiceMock = mock[AuditService]
    val tokenFactoryMock = mock[UUIDFactory]
    val onlineTestInvitationDateFactoryMock = mock[DateTimeFactory]

    when(tokenFactoryMock.generateUUID()).thenReturn(token)
    when(onlineTestInvitationDateFactoryMock.nowLocalTimeZone).thenReturn(invitationDate)
    when(otRepositoryMock.removePhase1TestProfileProgresses(any[String], any[List[ProgressStatus]])).thenReturn(Future.successful(()))

    val onlineTestService = new OnlineTestService {
      val appRepository = appRepositoryMock
      val cdRepository = cdRepositoryMock
      val otRepository = otRepositoryMock
      val trRepository = trRepositoryMock
      val cubiksGatewayClient = cubiksGatewayClientMock
      val emailClient = emailClientMock
      val auditService = auditServiceMock
      val tokenFactory = tokenFactoryMock
      val dateTimeFactory = onlineTestInvitationDateFactoryMock
      val gatewayConfig = testGatewayConfig
      val actor = ActorSystem()

    }
  }

  trait SuccessfulTestInviteFixture extends OnlineTest {
    when(cubiksGatewayClientMock.registerApplicant(eqTo(registerApplicant))(any[HeaderCarrier]))
      .thenReturn(Future.successful(registration))
    when(cubiksGatewayClientMock.inviteApplicant(any[InviteApplicant])(any[HeaderCarrier]))
      .thenReturn(Future.successful(invitation))
    when(cdRepositoryMock.find(any[String])).thenReturn(Future.successful(contactDetails))
    when(emailClientMock.sendOnlineTestInvitation(
      eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate))(
      any[HeaderCarrier]
    )).thenReturn(Future.successful(()))
    when(otRepositoryMock.insertOrUpdatePhase1TestGroup(any[String], any[Phase1TestProfile])).thenReturn(Future.successful(()))
    when(trRepositoryMock.remove(any[String])).thenReturn(Future.successful(()))
    when(otRepositoryMock.removePhase1TestProfileProgresses(any[String], any[List[ProgressStatus]])).thenReturn(Future.successful(()))
  }
}
