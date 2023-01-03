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

package services.onlinetesting.phase1

import akka.actor.ActorSystem
import config.{MicroserviceAppConfig, _}
import connectors.ExchangeObjects._
import connectors.{OnlineTestEmailClient, OnlineTestsGatewayClient}
import factories.{DateTimeFactory, UUIDFactory}
import model.Commands.PostCode
import model.Exceptions._
import model.OnlineTestCommands._
import model.Phase1TestExamples._
import model.ProgressStatuses.{toString => _, _}
import model.exchange.PsiRealTimeResults
import model.persisted._
import model.stc.StcEventTypes.{toString => _}
import model.{ProgressStatuses, _}
import org.joda.time.{DateTime, LocalDate}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.PrivateMethodTester
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.onlinetesting.Phase1TestRepository
import services.AuditService
import services.onlinetesting.Exceptions.{TestCancellationException, TestRegistrationException}
import services.sift.ApplicationSiftService
import testkit.MockitoImplicits._
import testkit.{ExtendedTimeout, UnitSpec}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class Phase1TestServiceSpec extends UnitSpec with ExtendedTimeout
  with PrivateMethodTester {
  implicit val ec: ExecutionContext = ExecutionContext.global
  val scheduleCompletionBaseUrl = "http://localhost:9284/fset-fast-stream/online-tests/phase1"

  val inventoryIds: Map[String, String] = Map[String, String](
  "test1" -> "test1-uuid",
  "test2" -> "test2-uuid",
  "test3" -> "test3-uuid",
  "test4"->"test4-uuid")

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
    expiryTimeInDays = 5, expiryTimeInDaysForInvigilatedETray = 90, gracePeriodInSecs = 0, testRegistrationDelayInSecs = 1,
    tests, standard = List("test1", "test2")
  )

  val mockNumericalTestsConfig2 = NumericalTestsConfig(gracePeriodInSecs = 0, tests = tests, standard = List("test1"))

  val gatewayConfig = OnlineTestsGatewayConfig(
    url = "",
    phase1Tests = mockPhase1TestConfig,
    phase2Tests = mockPhase2TestConfig,
    numericalTests = mockNumericalTestsConfig2,
    reportConfig = ReportConfig(1, 2, "en-GB"),
    candidateAppUrl = "http://localhost:9284",
    emailDomain = "test.com"
  )

  val preferredName = "Preferred\tName"
  val preferredNameSanitized = "Preferred Name"
  val lastName = ""
  val userId = "testUserId"
  val appId = "appId"

  val onlineTestApplication = OnlineTestApplication(applicationId = appId,
    applicationStatus = ApplicationStatus.SUBMITTED,
    userId = userId,
    testAccountId = "testAccountId",
    guaranteedInterview = false,
    needsOnlineAdjustments = false,
    needsAtVenueAdjustments = false,
    preferredName,
    lastName,
    None,
    None
  )

  def uuid: String = UUIDFactory.generateUUID()
  val orderId: String = uuid
  val accessCode = "fdkfdfj"
  val logonUrl = "http://localhost/logonUrl"
  val authenticateUrl = "http://localhost/authenticate"

  val invitationDate = DateTime.parse("2016-05-11")
  val startedDate = invitationDate.plusDays(1)
  val expirationDate = invitationDate.plusDays(5)

  val phase1Test = PsiTest(inventoryId = uuid, orderId = uuid, assessmentId = uuid, reportId = uuid, normId = uuid,
    usedForResults = true, testUrl = authenticateUrl, invitationDate = invitationDate)

  val phase1TestProfile = Phase1TestProfile(expirationDate, List(phase1Test))

  val candidate = model.Candidate(userId = "user123", applicationId = Some("appId123"), testAccountId = Some("testAccountId"),
    email = Some("test@test.com"), firstName = Some("Cid"),lastName = Some("Highwind"), preferredName = None,
    dateOfBirth = None, address = None, postCode = None, country = None,
    applicationRoute = None, applicationStatus = None
  )

  val postcode : Option[PostCode]= Some("WC2B 4")
  val emailContactDetails = "emailfjjfjdf@mailinator.com"
  val contactDetails = ContactDetails(outsideUk = false, Address("Aldwych road"), postcode, Some("UK"), emailContactDetails, "111111")

  val auditDetails = Map("userId" -> userId)
  val auditDetailsWithEmail = auditDetails + ("email" -> emailContactDetails)

  val connectorErrorMessage = "Error in connector"

  val result = OnlineTestCommands.PsiTestResult(status = "Completed", tScore = 23.9999d, raw = 66.9999d)

  val savedResult = persisted.PsiTestResult(tScore = 23.9999d, rawScore = 66.9999d, None)

  val applicationId = "31009ccc-1ac3-4d55-9c53-1908a13dc5e1"
  val expiredApplication = ExpiringOnlineTest(applicationId, userId, preferredName)
  val expiryReminder = NotificationExpiringOnlineTest(applicationId, userId, preferredName, expirationDate)
  val success = Future.successful(())

  "get online test" should {
    "return None if the application id does not exist" in new OnlineTest {
      when(phase1TestRepositoryMock.getTestGroup(any())).thenReturnAsync(None)
      val result = phase1TestService.getTestGroup("nonexistent-userid").futureValue
      result mustBe None
    }

    val validExpireDate = new DateTime(2016, 6, 9, 0, 0)

    "return a valid set of aggregated online test data if the user id is valid" in new OnlineTest {
      when(appRepositoryMock.findCandidateByUserId(any[String]))
        .thenReturnAsync(Some(candidate))

      when(phase1TestRepositoryMock.getTestGroup(any[String]))
        .thenReturnAsync(Some(Phase1TestProfile(expirationDate = validExpireDate, tests = List(phase1Test))))

      val result = phase1TestService.getTestGroup("valid-userid").futureValue

      result.get.expirationDate must equal(validExpireDate)
    }
  }

  "register and invite application" should {
    "Invite to two tests and issue one email for GIS candidates" in new SuccessfulTestInviteFixture {
      when(phase1TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))

      val result = phase1TestService
        .registerAndInviteForTestGroup(List(onlineTestApplication.copy(guaranteedInterview = true)))

      result.futureValue mustBe unit

      verify(onlineTestsGatewayClientMock, times(2)).psiRegisterApplicant(any[RegisterCandidateRequest])
      verify(emailClientMock, times(1)).sendOnlineTestInvitation(
        eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate)
      )(any[HeaderCarrier])

      verify(auditServiceMock, times(2)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationEmailSent", auditDetailsWithEmail)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationProcessComplete", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvited", auditDetails)
      verify(auditServiceMock, times(5)).logEventNoRequest(any[String], any[Map[String, String]])
    }

    "Invite to 4 tests and issue one email for non-GIS candidates" in new SuccessfulTestInviteFixture {
      when(phase1TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))

      val result = phase1TestService
        .registerAndInviteForTestGroup(List(onlineTestApplication.copy(guaranteedInterview = false)))

      result.futureValue mustBe unit

      verify(onlineTestsGatewayClientMock, times(4)).psiRegisterApplicant(any[RegisterCandidateRequest])
      verify(emailClientMock, times(1)).sendOnlineTestInvitation(
        eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate)
      )(any[HeaderCarrier])

      verify(auditServiceMock, times(4)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationEmailSent", auditDetailsWithEmail)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationProcessComplete", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvited", auditDetails)
      verify(auditServiceMock, times(7)).logEventNoRequest(any[String], any[Map[String, String]])
    }

    "fail if registration fails" in new OnlineTest {
      when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest]))
        .thenReturn(Future.failed(new ConnectorException(connectorErrorMessage)))

      val result = phase1TestService.registerAndInviteForTestGroup(onlineTestApplication :: Nil)
      result.failed.futureValue mustBe a[ConnectorException]

      verify(auditServiceMock, never()).logEventNoRequest(any[String], any[Map[String, String]])
    }

    "fail, audit 'UserRegisteredForOnlineTest' and audit 'OnlineTestInvited' " +
      "if there is an exception retrieving the contact details" in new OnlineTest  {
      when(phase1TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))
      when(phase1TestRepositoryMock.insertOrUpdateTestGroup(any[String], any[Phase1TestProfile])).thenReturnAsync()
      when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest])).thenReturnAsync(aoa)
      when(cdRepositoryMock.find(anyString())).thenReturn(Future.failed(new Exception))

      val result = phase1TestService.registerAndInviteForTestGroup(List(onlineTestApplication))
      result.failed.futureValue mustBe an[Exception]

      verify(auditServiceMock, times(4)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvited", auditDetails)
      verify(auditServiceMock, times(5)).logEventNoRequest(any[String], any[Map[String, String]])
    }

    "fail, audit 'UserRegisteredForOnlineTest' and audit 'OnlineTestInvited'" +
      " if there is an exception sending the invitation email" in new OnlineTest {
      when(phase1TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))
      when(phase1TestRepositoryMock.insertOrUpdateTestGroup(any[String], any[Phase1TestProfile]))
        .thenReturnAsync()
      when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest]))
        .thenReturnAsync(aoa)

      when(cdRepositoryMock.find(userId)).thenReturnAsync(contactDetails)

      when(emailClientMock.sendOnlineTestInvitation(
        eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate)
      )(any[HeaderCarrier]))
        .thenReturn(Future.failed(new Exception))

      val result = phase1TestService.registerAndInviteForTestGroup(List(onlineTestApplication))
      result.failed.futureValue mustBe an[Exception]

      verify(auditServiceMock, times(4)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvited", auditDetails)
      verify(auditServiceMock, times(5)).logEventNoRequest(any[String], any[Map[String, String]])
    }

    "fail, audit 'UserRegisteredForOnlineTest', audit 'UserInvitedToOnlineTest'" +
      ", not send invitation email to user " +
      "if there is an exception storing the test profile data to database" in new OnlineTest {
      when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest])).thenReturnAsync(aoa)
      when(phase1TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))
      when(phase1TestRepositoryMock.insertOrUpdateTestGroup(any[String], any[Phase1TestProfile]))
        .thenReturn(Future.failed(new Exception))

      val result = phase1TestService.registerAndInviteForTestGroup(List(onlineTestApplication))
      result.failed.futureValue mustBe an[Exception]

      verify(emailClientMock, never()).sendOnlineTestInvitation(any[String], any[String], any[DateTime])(
        any[HeaderCarrier]
      )
      verify(auditServiceMock, times(4)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, never()).logEventNoRequest("OnlineTestInvited", auditDetails)
      verify(auditServiceMock, times(4)).logEventNoRequest(any[String], any[Map[String, String]])
    }

    "audit 'OnlineTestInvitationProcessComplete' on success" in new OnlineTest {
      when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest])).thenReturnAsync(aoa)
      when(phase1TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))
      when(cdRepositoryMock.find(any[String])).thenReturnAsync(contactDetails)
      when(emailClientMock.sendOnlineTestInvitation(
        eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate))(
        any[HeaderCarrier]
      )).thenReturnAsync()
      when(phase1TestRepositoryMock.insertOrUpdateTestGroup(any[String], any[Phase1TestProfile]))
        .thenReturnAsync()

      val result = phase1TestService.registerAndInviteForTestGroup(List(onlineTestApplication))
      result.futureValue mustBe unit

      verify(emailClientMock, times(1)).sendOnlineTestInvitation(
        eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate)
      )(any[HeaderCarrier])

      verify(auditServiceMock, times(4)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationEmailSent", auditDetailsWithEmail)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationProcessComplete", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvited", auditDetails)
      verify(auditServiceMock, times(7)).logEventNoRequest(any[String], any[Map[String, String]])
    }
  }

  "get adjusted time" should {
    "return minimum if percentage is zero" in new OnlineTest {
      val result = phase1TestService.getAdjustedTime(minimum = 6, maximum = 12, percentageToIncrease = 0)
      result mustBe 6
    }

    "return maximum if percentage is 100%" in new OnlineTest {
      val result = phase1TestService.getAdjustedTime(minimum = 6, maximum = 12, percentageToIncrease = 100)
      result mustBe 12
    }

    "return maximum if percentage is over 100%" in new OnlineTest {
      val result = phase1TestService.getAdjustedTime(minimum = 6, maximum = 12, percentageToIncrease = 101)
      result mustBe 12
    }

    "return adjusted time if percentage is above zero and below 100%" in new OnlineTest {
      val result = phase1TestService.getAdjustedTime(minimum = 6, maximum = 12, percentageToIncrease = 50)
      result mustBe 9
    }
    "return adjusted time round up if percentage is above zero and below 100%" in new OnlineTest {
      val result = phase1TestService.getAdjustedTime(minimum = 6, maximum = 12, percentageToIncrease = 51)
      result mustBe 10
    }
  }

  "mark as started" should {
    "change progress to started" in new OnlineTest {
      when(phase1TestRepositoryMock.updateTestStartTime(any[String], any[DateTime])).thenReturnAsync()
      when(phase1TestRepositoryMock.getTestGroupByOrderId(anyString()))
        .thenReturnAsync(Phase1TestGroupWithUserIds("appId123", userId, phase1TestProfile))
      when(phase1TestRepositoryMock.updateProgressStatus("appId123", ProgressStatuses.PHASE1_TESTS_STARTED))
        .thenReturnAsync()
      when(appRepositoryMock.getProgressStatusTimestamps(anyString())).thenReturnAsync(Nil)

      phase1TestService.markAsStarted(orderId).futureValue

      verify(phase1TestRepositoryMock, times(1)).updateProgressStatus("appId123", ProgressStatuses.PHASE1_TESTS_STARTED)
    }

    //TODO: add back in at end of campaign 2019
    "not change progress to started if status exists" ignore new OnlineTest {
      when(phase1TestRepositoryMock.updateTestStartTime(any[String], any[DateTime])).thenReturnAsync()
      when(phase1TestRepositoryMock.getTestGroupByOrderId(anyString()))
        .thenReturnAsync(Phase1TestGroupWithUserIds("appId123", userId, phase1TestProfile))
      when(phase1TestRepositoryMock.updateProgressStatus("appId123", ProgressStatuses.PHASE1_TESTS_STARTED))
        .thenReturnAsync()
      when(appRepositoryMock.getProgressStatusTimestamps(anyString()))
        .thenReturnAsync(List(("FAKE_STATUS", DateTime.now()), ("PHASE1_TESTS_STARTED", DateTime.now())))

      phase1TestService.markAsStarted(orderId).futureValue

      verify(phase1TestRepositoryMock, never()).updateProgressStatus("appId123", ProgressStatuses.PHASE1_TESTS_STARTED)
    }
  }

  "mark as completed" should {
    "change progress to completed if there are all tests completed and the test profile hasn't expired" in new OnlineTest {
      when(phase1TestRepositoryMock.updateTestCompletionTime(any[String], any[DateTime])).thenReturnAsync()
      val phase1Tests: Phase1TestProfile = phase1TestProfile.copy(
        tests = phase1TestProfile.tests.map(t => t.copy(orderId = orderId, completedDateTime = Some(DateTime.now()))),
        expirationDate = DateTime.now().plusDays(2)
      )
      when(phase1TestRepositoryMock.getTestProfileByOrderId(anyString()))
        .thenReturnAsync(phase1Tests)
      when(phase1TestRepositoryMock.getTestGroupByOrderId(anyString()))
        .thenReturnAsync(Phase1TestGroupWithUserIds("appId123", userId, phase1Tests))
      when(phase1TestRepositoryMock.updateProgressStatus("appId123", ProgressStatuses.PHASE1_TESTS_COMPLETED))
        .thenReturnAsync()

      phase1TestService.markAsCompleted(orderId).futureValue

      verify(phase1TestRepositoryMock).updateProgressStatus("appId123", ProgressStatuses.PHASE1_TESTS_COMPLETED)
    }
  }

  "Reset tests" should {
    "throw exception if test group cannot be found" in new OnlineTest {
      when(phase1TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(None)

      val result = phase1TestService.resetTest(onlineTestApplication, phase1Test.orderId, "")

      result.failed.futureValue mustBe an[CannotFindTestGroupByApplicationIdException]
    }

    "throw exception if test by orderId cannot be found" in new OnlineTest {
      val newTests = phase1Test.copy(orderId = "unknown-uuid") :: Nil
      when(phase1TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile.copy(tests = newTests)))

      val result = phase1TestService.resetTest(onlineTestApplication, phase1Test.orderId, "")

      result.failed.futureValue mustBe an[CannotFindTestByOrderIdException]
    }

    // we are not sending a cancellation request anymore so this test should be ignored for now
    "not register candidate if cancellation request fails" ignore new OnlineTest {
      when(phase1TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))
      when(onlineTestsGatewayClientMock.psiCancelTest(any[CancelCandidateTestRequest]))
        .thenReturnAsync(acaError)

      val result = phase1TestService.resetTest(onlineTestApplication, phase1Test.orderId, "")

      result.failed.futureValue mustBe a[TestCancellationException]

      verify(onlineTestsGatewayClientMock, times(0)).psiRegisterApplicant(any[RegisterCandidateRequest])
      verify(emailClientMock, times(0))
        .sendOnlineTestInvitation(eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate))(any[HeaderCarrier])

      verify(auditServiceMock, times(0)).logEventNoRequest("TestCancelledForCandidate", auditDetails)
      verify(auditServiceMock, times(0)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(0)).logEventNoRequest("OnlineTestInvitationEmailSent", auditDetailsWithEmail)
      verify(auditServiceMock, times(0)).logEventNoRequest("OnlineTestInvited", auditDetails)
    }

    "throw exception if config cant be found" in new OnlineTest {
      val newTests = phase1Test.copy(inventoryId = "unknown-uuid") :: Nil
      when(phase1TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile.copy(tests = newTests)))
      when(onlineTestsGatewayClientMock.psiCancelTest(any[CancelCandidateTestRequest]))
        .thenReturnAsync(acaCompleted)

      val result = phase1TestService.resetTest(onlineTestApplication, phase1Test.orderId, "")

      result.failed.futureValue mustBe a[CannotFindTestByInventoryIdException]
    }

    "not complete invitation if re-registration request connection fails"  in new OnlineTest {
      val newTests = phase1Test.copy(inventoryId = "inventory-id-1") :: Nil
      when(phase1TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile.copy(tests = newTests)))
      when(onlineTestsGatewayClientMock.psiCancelTest(any[CancelCandidateTestRequest]))
        .thenReturnAsync(acaCompleted)
      when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest]))
        .thenReturn(Future.failed(new ConnectorException(connectorErrorMessage)))

      val result = phase1TestService.resetTest(onlineTestApplication, phase1Test.orderId, "")

      result.failed.futureValue mustBe a[ConnectorException]

      verify(onlineTestsGatewayClientMock, times(1)).psiRegisterApplicant(any[RegisterCandidateRequest])
      verify(emailClientMock, times(0))
        .sendOnlineTestInvitation(eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate))(any[HeaderCarrier])

      verify(auditServiceMock, times(0)).logEventNoRequest("TestCancelledForCandidate", auditDetails)
      verify(auditServiceMock, times(0)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(0)).logEventNoRequest("OnlineTestInvitationEmailSent", auditDetailsWithEmail)
      verify(auditServiceMock, times(0)).logEventNoRequest("OnlineTestInvited", auditDetails)
    }

    "not complete invitation if re-registration fails"  in new OnlineTest {
      val newTests = phase1Test.copy(inventoryId = "inventory-id-1") :: Nil
      when(phase1TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile.copy(tests = newTests)))

      when(onlineTestsGatewayClientMock.psiCancelTest(any[CancelCandidateTestRequest]))
        .thenReturnAsync(acaCompleted)
      when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest]))
        .thenReturnAsync(aoaFailed)

      val result = phase1TestService.resetTest(onlineTestApplication, phase1Test.orderId, "")

      result.failed.futureValue mustBe a[TestRegistrationException]

      verify(onlineTestsGatewayClientMock, times(1)).psiRegisterApplicant(any[RegisterCandidateRequest])
      verify(emailClientMock, times(0))
        .sendOnlineTestInvitation(eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate))(any[HeaderCarrier])

      verify(auditServiceMock, times(0)).logEventNoRequest("TestCancelledForCandidate", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(0)).logEventNoRequest("OnlineTestInvitationEmailSent", auditDetailsWithEmail)
      verify(auditServiceMock, times(0)).logEventNoRequest("OnlineTestInvited", auditDetails)
    }

    "complete reset successfully" in new SuccessfulTestInviteFixture {
      val newTests = phase1Test.copy(inventoryId = "inventory-id-1") :: Nil
      when(phase1TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile.copy(tests = newTests)))

      when(onlineTestsGatewayClientMock.psiCancelTest(any[CancelCandidateTestRequest]))
        .thenReturnAsync(acaCompleted)
      when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest]))
        .thenReturnAsync(aoa)

      val result = phase1TestService.resetTest(onlineTestApplication, phase1Test.orderId, "")

      result.futureValue mustBe unit

      verify(onlineTestsGatewayClientMock, times(1)).psiRegisterApplicant(any[RegisterCandidateRequest])
      verify(emailClientMock, times(1))
        .sendOnlineTestInvitation(eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate))(any[HeaderCarrier])

      verify(auditServiceMock, times(0)).logEventNoRequest("TestCancelledForCandidate", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationEmailSent", auditDetailsWithEmail)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvited", auditDetails)
    }
  }

  "store real time results" should {
    "handle not finding an application for the given order id" in new OnlineTest {
      when(phase1TestRepositoryMock.getApplicationIdForOrderId(any[String], any[String])).thenReturnAsync(None)

      val result = phase1TestService.storeRealTimeResults(orderId, realTimeResults)

      val exception = result.failed.futureValue
      exception mustBe an[CannotFindTestByOrderIdException]
      exception.getMessage mustBe s"Application not found for test for orderId=$orderId"
    }

    "handle not finding a test profile for the given order id" in new OnlineTest {
      when(phase1TestRepositoryMock.getApplicationIdForOrderId(any[String], any[String])).thenReturnAsync(Some(appId))

      when(phase1TestRepositoryMock.getTestProfileByOrderId(any[String])).thenReturn(Future.failed(
        CannotFindTestByOrderIdException(s"Cannot find test group by orderId=$orderId")
      ))

      val result = phase1TestService.storeRealTimeResults(orderId, realTimeResults)

      val exception = result.failed.futureValue
      exception mustBe an[CannotFindTestByOrderIdException]
      exception.getMessage mustBe s"Cannot find test group by orderId=$orderId"
    }

    "handle not finding the test group when checking to update the progress status" in new OnlineTest {
      when(phase1TestRepositoryMock.getApplicationIdForOrderId(any[String], any[String])).thenReturnAsync(Some(appId))

      val phase1Tests: Phase1TestProfile = phase1TestProfile.copy(
        tests = phase1TestProfile.tests.map(t => t.copy(orderId = orderId, completedDateTime = Some(DateTime.now()))),
        expirationDate = DateTime.now().plusDays(2)
      )

      when(phase1TestRepositoryMock.getTestProfileByOrderId(any[String])).thenReturnAsync(phase1Tests)
      when(phase1TestRepositoryMock.insertTestResult(any[String], any[PsiTest], any[model.persisted.PsiTestResult])).thenReturnAsync()
      when(phase1TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(None)

      val result = phase1TestService.storeRealTimeResults(orderId, realTimeResults)

      val exception = result.failed.futureValue
      exception mustBe an[Exception]
      exception.getMessage mustBe s"No test profile returned for $appId"

      verify(phase1TestRepositoryMock, never()).updateTestCompletionTime(any[String], any[DateTime])
      verify(phase1TestRepositoryMock, never()).updateProgressStatus(any[String], any[ProgressStatuses.ProgressStatus])
    }

    "process the real time results and update the progress status" in new OnlineTest {
      when(phase1TestRepositoryMock.getApplicationIdForOrderId(any[String], any[String])).thenReturnAsync(Some(appId))

      val phase1Tests: Phase1TestProfile = phase1TestProfile.copy(
        tests = phase1TestProfile.tests.map(t => t.copy(orderId = orderId, completedDateTime = Some(DateTime.now()))),
        expirationDate = DateTime.now().plusDays(2)
      )

      when(phase1TestRepositoryMock.getTestProfileByOrderId(any[String])).thenReturnAsync(phase1Tests)
      when(phase1TestRepositoryMock.insertTestResult(any[String], any[PsiTest], any[model.persisted.PsiTestResult])).thenReturnAsync()

      val phase1TestProfile2 = Phase1TestProfile(expirationDate = now, tests = List(firstPsiTest, secondPsiTest, thirdPsiTest, fourthPsiTest))
      when(phase1TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile2))
      when(phase1TestRepositoryMock.updateProgressStatus(any[String], any[ProgressStatuses.ProgressStatus])).thenReturnAsync()

      phase1TestService.storeRealTimeResults(orderId, realTimeResults).futureValue

      verify(phase1TestRepositoryMock, never()).updateTestCompletionTime(any[String], any[DateTime])
      verify(phase1TestRepositoryMock, times(1)).updateProgressStatus(any[String], eqTo(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED))
    }

    "process the real time results, mark the test as completed and update the progress status" in new OnlineTest {
      when(phase1TestRepositoryMock.getApplicationIdForOrderId(any[String], any[String])).thenReturnAsync(Some(appId))

      val phase1TestsNotCompleted: Phase1TestProfile = phase1TestProfile.copy(
        tests = phase1TestProfile.tests.map(t => t.copy(orderId = orderId)),
        expirationDate = DateTime.now().plusDays(2)
      )

      when(phase1TestRepositoryMock.getTestProfileByOrderId(any[String])).thenReturnAsync(phase1TestsNotCompleted)
      when(phase1TestRepositoryMock.insertTestResult(any[String], any[PsiTest], any[model.persisted.PsiTestResult])).thenReturnAsync()
      when(phase1TestRepositoryMock.updateTestCompletionTime(any[String], any[DateTime])).thenReturnAsync()

      val phase1TestsCompleted: Phase1TestProfile = phase1TestProfile.copy(
        tests = phase1TestProfile.tests.map(t => t.copy(orderId = orderId, completedDateTime = Some(DateTime.now()))),
        expirationDate = DateTime.now().plusDays(2)
      )

      val phase1TestGroupWithUserIds2 = Phase1TestGroupWithUserIds(applicationId = "appId", userId = "userId", testGroup = phase1TestsCompleted)

      when(phase1TestRepositoryMock.getTestGroupByOrderId(any[String])).thenReturnAsync(phase1TestGroupWithUserIds2)
      when(phase1TestRepositoryMock.updateProgressStatus(any[String], eqTo(ProgressStatuses.PHASE1_TESTS_COMPLETED))).thenReturnAsync()

      val phase1TestProfile2 = Phase1TestProfile(expirationDate = now, tests = List(firstPsiTest, secondPsiTest, thirdPsiTest, fourthPsiTest))
      when(phase1TestRepositoryMock.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile2))
      when(phase1TestRepositoryMock.updateProgressStatus(any[String], eqTo(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED))).thenReturnAsync()

      phase1TestService.storeRealTimeResults(orderId, realTimeResults).futureValue

      verify(phase1TestRepositoryMock, times(1)).updateTestCompletionTime(any[String], any[DateTime])
      verify(phase1TestRepositoryMock, times(1)).updateProgressStatus(any[String], eqTo(ProgressStatuses.PHASE1_TESTS_COMPLETED))
      verify(phase1TestRepositoryMock, times(1)).updateProgressStatus(any[String], eqTo(ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED))
    }
  }

  "Progress Sdip for SdipFaststream candidate" should {
    "Set the progress status the candidate has passed Sdip" in new OnlineTest {
      when(phase1TestRepositoryMock.updateProgressStatusOnly(any[String], any[ProgressStatus])).thenReturn(Future.successful(unit))

      val testProfileWithEvaluation = phase1TestProfile.copy(
        evaluation = Some(PassmarkEvaluation("version", None, result = List(SchemeEvaluationResult(SchemeId("Finance"), "Green"),
          SchemeEvaluationResult(SchemeId("Sdip"), "Green")), "version-res", None
        ))
      )

      val phase1TestGroup = Phase1TestGroupWithUserIds("appId1", "userId1", testProfileWithEvaluation)

      val eventCaptor = ArgumentCaptor.forClass(classOf[ProgressStatuses.ProgressStatus])

      val result = phase1TestService.progressSdipFaststreamCandidateForSdip(phase1TestGroup).futureValue

      verify(phase1TestRepositoryMock).updateProgressStatusOnly(any[String], eventCaptor.capture)

      import scala.collection.JavaConverters._
      eventCaptor.getAllValues.asScala.head.toString mustBe
        ProgressStatuses.getProgressStatusForSdipFsSuccess(ApplicationStatus.PHASE1_TESTS).toString
    }

    "Set the progress status the candidate has failed Sdip" in new OnlineTest {
      when(phase1TestRepositoryMock.updateProgressStatusOnly(any[String], any[ProgressStatus])).thenReturn(Future.successful(unit))

      val testProfileWithEvaluation = phase1TestProfile.copy(
        evaluation = Some(PassmarkEvaluation("version", None,
          result = List(SchemeEvaluationResult(SchemeId("Finance"), "Green"), SchemeEvaluationResult(SchemeId("Sdip"), "Red")),
          "version-res", None
        ))
      )

      val phase1TestGroup = Phase1TestGroupWithUserIds("appId1", "userId1", testProfileWithEvaluation)
      val result = phase1TestService.progressSdipFaststreamCandidateForSdip(phase1TestGroup).futureValue

      val eventCaptor = ArgumentCaptor.forClass(classOf[ProgressStatuses.ProgressStatus])
      verify(phase1TestRepositoryMock).updateProgressStatusOnly(any[String], eventCaptor.capture)

      import scala.collection.JavaConverters._
      eventCaptor.getAllValues.asScala.head.toString mustBe
        ProgressStatuses.getProgressStatusForSdipFsFailed(ApplicationStatus.PHASE1_TESTS).toString
    }
  }

  import services.stc.StcEventServiceFixture

  trait OnlineTest extends StcEventServiceFixture {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val rh: RequestHeader = mock[RequestHeader]
    implicit val now: DateTime = DateTime.now

    val appConfigMock = mock[MicroserviceAppConfig]
    val appRepositoryMock = mock[GeneralApplicationRepository]
    val cdRepositoryMock = mock[ContactDetailsRepository]
    val phase1TestRepositoryMock = mock[Phase1TestRepository]
    val onlineTestsGatewayClientMock = mock[OnlineTestsGatewayClient]
    val tokenFactoryMock = mock[UUIDFactory]
    val dateTimeFactoryMock = mock[DateTimeFactory]
    val siftServiceMock = mock[ApplicationSiftService]
    //    val emailClientMock = mock[CSREmailClient] //TODO:fix changed type
    val emailClientMock = mock[OnlineTestEmailClient] //TODO:fix changed type
    val auditServiceMock = mock[AuditService]

    def aoa = AssessmentOrderAcknowledgement(
      customerId = "cust-id", receiptId = "receipt-id", orderId = orderId, testLaunchUrl = authenticateUrl,
      status = AssessmentOrderAcknowledgement.acknowledgedStatus, statusDetails = "", statusDate = LocalDate.now())

    def aoaFailed = AssessmentOrderAcknowledgement(
      customerId = "cust-id", receiptId = "receipt-id", orderId = orderId, testLaunchUrl = authenticateUrl,
      status = AssessmentOrderAcknowledgement.errorStatus, statusDetails = "", statusDate = LocalDate.now())

    def acaCompleted = AssessmentCancelAcknowledgementResponse(
      AssessmentCancelAcknowledgementResponse.completedStatus,
      "Everything is fine!", statusDate = LocalDate.now()
    )

    def acaError = AssessmentCancelAcknowledgementResponse(
      AssessmentCancelAcknowledgementResponse.errorStatus,
      "Something went wrong!", LocalDate.now()
    )

    when(tokenFactoryMock.generateUUID()).thenReturn(uuid)
    when(dateTimeFactoryMock.nowLocalTimeZone).thenReturn(invitationDate)
    when(phase1TestRepositoryMock.resetTestProfileProgresses(any[String], any[List[ProgressStatus]], any[Boolean]))
      .thenReturnAsync()

    when(appConfigMock.onlineTestsGatewayConfig).thenReturn(gatewayConfig)

    val realTimeResults = PsiRealTimeResults(tScore = 10.0, rawScore = 20.0, reportUrl = None)

    val actor = ActorSystem()

    val phase1TestService = new Phase1TestService(
      appConfigMock,
      appRepositoryMock,
      cdRepositoryMock,
      phase1TestRepositoryMock,
      onlineTestsGatewayClientMock,
      tokenFactoryMock,
      dateTimeFactoryMock,
      emailClientMock,
      auditServiceMock,
      siftServiceMock,
      stcEventServiceMock,
      actor
    )
  }

  trait SuccessfulTestInviteFixture extends OnlineTest {
    when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest])).thenReturnAsync(aoa)
    when(cdRepositoryMock.find(any[String])).thenReturnAsync(contactDetails)
    when(emailClientMock.sendOnlineTestInvitation(
      eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate))(
      any[HeaderCarrier]
    )).thenReturnAsync()
    when(phase1TestRepositoryMock.insertOrUpdateTestGroup(any[String], any[Phase1TestProfile])).thenReturnAsync()
    when(phase1TestRepositoryMock.resetTestProfileProgresses(any[String], any[List[ProgressStatus]], any[Boolean])).thenReturnAsync()
  }
}
