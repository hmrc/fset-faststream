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

import config._
import connectors.ExchangeObjects._
import connectors.{CSREmailClient, CubiksGatewayClient}
import controllers.OnlineTestDetails
import factories.{DateTimeFactory, UUIDFactory}
import model.{Address, ApplicationStatus, Commands, ProgressStatuses}
import model.Commands._
import model.Exceptions.{ConnectorException, NotFoundException}
import model.OnlineTestCommands._
import model.PersistedObjects.ContactDetails
import model.ProgressStatuses.ProgressStatus
import org.joda.time.DateTime
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import repositories.application.{GeneralApplicationRepository, OnlineTestRepository}
import repositories.{ContactDetailsRepository, TestReportRepository}
import services.AuditService
import testkit.ExtendedTimeout
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class OnlineTestServiceSpec extends PlaySpec with BeforeAndAfterEach with MockitoSugar with ScalaFutures with ExtendedTimeout {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val VerbalAndNumericalAssessmentId = 1
  val VerbalSectionId = 1
  val NumericalSectionId = 2
  val verbalTimeInMinutesMinimum = 6
  val verbalTimeInMinutesMaximum = 12
  val numericalTimeInMinutesMinimum = 6
  val numericalTimeInMinutesMaximum = 12

  val emailDomainMock = "mydomain.com"
  val onlineTestCompletedUrlMock = "http://localhost:8000/fset-fast-stream/online-tests/complete/"
  val gisScheduledIdMock = List(11111)
  val standardScheduleIdMock = List(33333, 22222)

  val testGatewayConfig = CubiksGatewayConfig(
    "",
    CubiksOnlineTestConfig(phaseName = "phase",
      expiryTimeInDays = 7,
      scheduleIds = Map("sjq" -> 1, "bq" -> 2),
      List("sjq", "bq"),
      List("sjq")
    ),
    CubiksGatewayVerbalAndNumericalAssessment(
      VerbalAndNumericalAssessmentId,
      normId = 22,
      VerbalSectionId, verbalTimeInMinutesMinimum, verbalTimeInMinutesMaximum, NumericalSectionId,
      numericalTimeInMinutesMinimum, numericalTimeInMinutesMaximum
    ),
    CubiksGatewayStandardAssessment(31, 32),
    CubiksGatewayStandardAssessment(41, 42),
    ReportConfig(1, 2, "en-GB"),
    "http://localhost:8000",
    emailDomainMock
  )

  val ErrorMessage = "Error in connector"

  val ApplicationId = "ApplicationId1"
  val UserId = "1"
  val GuaranteedInterviewFalse = false
  val GuaranteedInterviewTrue = true
  val NeedsAdjustment = false
  val VerbalTimeAdjustmentPercentage = 6
  val NumericalTimeAdjustmentPercentage = 6
  val PreferredName = "Preferred\tName"
  val PreferredNameSanitized = "Preferred Name"
  val applicationForOnlineTestingWithNoTimeAdjustments = OnlineTestApplication(ApplicationId, ApplicationStatus.SUBMITTED, UserId,
    GuaranteedInterviewFalse, NeedsAdjustment, PreferredName, None)
  val timeAdjustments = TimeAdjustmentsOnlineTestApplication(VerbalTimeAdjustmentPercentage, NumericalTimeAdjustmentPercentage)
  val applicationForOnlineTestingWithTimeAdjustments = OnlineTestApplication(ApplicationId, ApplicationStatus.SUBMITTED, UserId,
    GuaranteedInterviewFalse, NeedsAdjustment, PreferredName, Some(timeAdjustments))
  val applicationForOnlineTestingGisWithNoTimeAdjustments = OnlineTestApplication(ApplicationId, ApplicationStatus.SUBMITTED, UserId,
    GuaranteedInterviewTrue, NeedsAdjustment, PreferredName, None)
  val applicationForOnlineTestingGisWithTimeAdjustments = OnlineTestApplication(ApplicationId, ApplicationStatus.SUBMITTED, UserId,
    GuaranteedInterviewTrue, NeedsAdjustment, PreferredName, Some(timeAdjustments))

  val FirstName = PreferredName
  val LastName = ""
  val Token = "2222"
  val EmailCubiks = Token + "@" + emailDomainMock
  val registerApplicant = RegisterApplicant(PreferredNameSanitized, LastName, EmailCubiks)

  val CubiksUserId = 2222
  val registration = Registration(CubiksUserId)

  val ScheduleId = standardScheduleIdMock.head

  val inviteApplicant = InviteApplicant(ScheduleId, CubiksUserId, onlineTestCompletedUrlMock, None, None)
  val inviteApplicantGisWithNoTimeAdjustments = inviteApplicant
  val inviteApplicantNoGisWithNoTimeAdjustments = inviteApplicant
  val timeAdjustmentsForInviteApplicant = TimeAdjustments(VerbalAndNumericalAssessmentId, VerbalSectionId, NumericalSectionId,
    7, 7)
  val inviteApplicantNoGisWithTimeAdjustments = inviteApplicant.copy(timeAdjustments = Some(timeAdjustmentsForInviteApplicant))
  val AccessCode = "fdkfdfj"
  val LogonUrl = "http://localhost/logonUrl"
  val AuthenticateUrl = "http://localhost/authenticate"
  val invitation = Invitation(CubiksUserId, EmailCubiks, AccessCode, LogonUrl, AuthenticateUrl, ScheduleId)

  val InvitationDate = DateTime.parse("2016-05-11")
  val ExpirationDate = InvitationDate.plusDays(7)
  val phase1Test = Phase1Test(scheduleId = standardScheduleIdMock.head,
    usedForResults = true,
    cubiksUserId = CubiksUserId,
    token = Token,
    testUrl = AuthenticateUrl,
    invitationDate = InvitationDate,
    participantScheduleId = 234
  )
  val phase1TestProfile = Phase1TestProfile(ExpirationDate,
    List(phase1Test)
  )

  val candidate = Commands.Candidate(userId = "user123", firstName = Some("Cid"),
    lastName = Some("Highwind"), preferredName = None, applicationId = Some("appId123"),
    email = Some("test@test.com"), dateOfBirth = None, address = None, postCode = None, country = None
  )

  val Postcode = "WC2B 4"
  val EmailContactDetails = "emailfjjfjdf@mailinator.com"
  val contactDetails = ContactDetails(Address("Aldwych road"), Postcode, EmailContactDetails, Some("111111"))

  val auditDetails = Map("userId" -> UserId)
  val auditDetailsWithEmail = auditDetails + ("email" -> EmailContactDetails)

  val MinimumAssessmentTime = 6
  val MaximumAssessmentTime = 12

  "get online test" should {
    "return None if the user does not exist" in new OnlineTest {
      when(appRepositoryMock.findCandidateByUserId(any[String])).thenReturn(Future.successful(None))
      val result = onlineTestService.getPhase1TestProfile("nonexistent-userid").futureValue
      result mustBe None

    }

    val validExpireDate = new DateTime(2016, 6, 9, 0, 0)

    "return a valid set of aggregated online test data if the user id is valid" in new OnlineTest {
      when(appRepositoryMock.findCandidateByUserId(any[String])).thenReturn(Future.successful(
        Some(candidate)
      ))

      when(otRepositoryMock.getPhase1TestProfile(any[String])).thenReturn(Future.successful(
        Some(Phase1TestProfile(expirationDate = validExpireDate,
          tests = List(phase1Test)
        ))
      ))

      val result = onlineTestService.getPhase1TestProfile("valid-userid").futureValue

      result.get.expirationDate must equal(validExpireDate)
      result.get.tests.head.invitationDate must equal(InvitationDate)
    }
  }

  "register and invite application" should {
    "issue one email for invites to SJQ for GIS candidates" in new SuccessfulTestInviteFixture {
      when(otRepositoryMock.getPhase1TestProfile(any[String])).thenReturn(Future.successful(Some(phase1TestProfile)))
      val result = onlineTestService.registerAndInviteForTestGroup(applicationForOnlineTestingGisWithNoTimeAdjustments)
      result.futureValue mustBe (())

      verify(emailClientMock, times(1)).sendOnlineTestInvitation(eqTo(EmailContactDetails), eqTo(PreferredName), eqTo(ExpirationDate))(
        any[HeaderCarrier]
      )
      verify(auditServiceMock, times(1)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("UserInvitedToOnlineTest", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationEmailSent", auditDetailsWithEmail)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationProcessComplete", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvited", auditDetails)
      verify(auditServiceMock, times(5)).logEventNoRequest(any[String], any[Map[String, String]])
    }

    "issue one email for invites to SJQ and BQ tests for non GIS candidates" in new SuccessfulTestInviteFixture {
      when(otRepositoryMock.getPhase1TestProfile(any[String])).thenReturn(Future.successful(Some(phase1TestProfile)))
      val result = onlineTestService.registerAndInviteForTestGroup(applicationForOnlineTestingWithNoTimeAdjustments)
      result.futureValue mustBe (())

      verify(emailClientMock, times(1)).sendOnlineTestInvitation(eqTo(EmailContactDetails), eqTo(PreferredName), eqTo(ExpirationDate))(
        any[HeaderCarrier]
      )
      verify(auditServiceMock, times(2)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(2)).logEventNoRequest("UserInvitedToOnlineTest", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationEmailSent", auditDetailsWithEmail)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationProcessComplete", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvited", auditDetails)
      verify(auditServiceMock, times(7)).logEventNoRequest(any[String], any[Map[String, String]])
    }

    "fail if registration fails" in new OnlineTest {
      when(cubiksGatewayClientMock.registerApplicant(any[RegisterApplicant])(any[HeaderCarrier])).
        thenReturn(Future.failed(new ConnectorException(ErrorMessage)))

      val result = onlineTestService.registerAndInviteForTestGroup(applicationForOnlineTestingWithNoTimeAdjustments)
      result.failed.futureValue mustBe a[ConnectorException]

      verify(auditServiceMock, times(0)).logEventNoRequest(any[String], any[Map[String, String]])
    }
    "fail and audit 'UserRegisteredForOnlineTest' if invitation fails" in new OnlineTest {
      when(cubiksGatewayClientMock.registerApplicant(any[RegisterApplicant])(any[HeaderCarrier]))
        .thenReturn(Future.successful(registration))
      when(cubiksGatewayClientMock.inviteApplicant(any[InviteApplicant])(any[HeaderCarrier])).thenReturn(
        Future.failed(new ConnectorException(ErrorMessage))
      )

      val result = onlineTestService.registerAndInviteForTestGroup(applicationForOnlineTestingWithNoTimeAdjustments)
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
        when(cdRepositoryMock.find(UserId))
          .thenReturn(Future.failed(new Exception))

        val result = onlineTestService.registerAndInviteForTestGroup(applicationForOnlineTestingWithNoTimeAdjustments)
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
        when(cdRepositoryMock.find(UserId))
          .thenReturn(Future.successful(contactDetails))
        when(emailClientMock.sendOnlineTestInvitation(eqTo(EmailContactDetails), eqTo(PreferredName), eqTo(ExpirationDate))(
          any[HeaderCarrier]
        )).thenReturn(Future.failed(new Exception))

        val result = onlineTestService.registerAndInviteForTestGroup(applicationForOnlineTestingWithNoTimeAdjustments)
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
        when(cdRepositoryMock.find(UserId)).thenReturn(Future.successful(contactDetails))
        when(emailClientMock.sendOnlineTestInvitation(eqTo(EmailContactDetails), eqTo(PreferredName), eqTo(ExpirationDate))(
          any[HeaderCarrier]
        )).thenReturn(Future.successful(()))

        when(otRepositoryMock.insertPhase1TestProfile(ApplicationId, phase1TestProfile))
          .thenReturn(Future.failed(new Exception))
        when(trRepositoryMock.remove(ApplicationId)).thenReturn(Future.successful(()))

        val result = onlineTestService.registerAndInviteForTestGroup(applicationForOnlineTestingWithNoTimeAdjustments)
        result.failed.futureValue mustBe an[Exception]

        verify(emailClientMock, times(0)).sendOnlineTestInvitation(any[String], any[String], any[DateTime])(
          any[HeaderCarrier]
        )
        verify(auditServiceMock, times(2)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
        verify(auditServiceMock, times(2)).logEventNoRequest("UserInvitedToOnlineTest", auditDetails)
        verify(auditServiceMock, times(4)).logEventNoRequest(any[String], any[Map[String, String]])
      }
    "audit 'OnlineTestInvitationProcessComplete' on success" in new OnlineTest {
      when(otRepositoryMock.getPhase1TestProfile(any[String])).thenReturn(Future.successful(Some(phase1TestProfile)))
      when(cubiksGatewayClientMock.registerApplicant(eqTo(registerApplicant))(any[HeaderCarrier]))
        .thenReturn(Future.successful(registration))
      when(cubiksGatewayClientMock.inviteApplicant(any[InviteApplicant])(any[HeaderCarrier]))
        .thenReturn(Future.successful(invitation))
      when(cdRepositoryMock.find(any[String])).thenReturn(Future.successful(contactDetails))
      when(emailClientMock.sendOnlineTestInvitation(eqTo(EmailContactDetails), eqTo(PreferredName), eqTo(ExpirationDate))(
        any[HeaderCarrier]
      )).thenReturn(Future.successful(()))
      when(otRepositoryMock.insertPhase1TestProfile(any[String], any[Phase1TestProfile]))
        .thenReturn(Future.successful(()))
      when(trRepositoryMock.remove(any[String])).thenReturn(Future.successful(()))

      val result = onlineTestService.registerAndInviteForTestGroup(applicationForOnlineTestingWithNoTimeAdjustments)
      result.futureValue mustBe (())

      verify(emailClientMock, times(1)).sendOnlineTestInvitation(eqTo(EmailContactDetails), eqTo(PreferredName), eqTo(ExpirationDate))(
        any[HeaderCarrier]
      )
      verify(auditServiceMock, times(2)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(2)).logEventNoRequest("UserInvitedToOnlineTest", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationEmailSent", auditDetailsWithEmail)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationProcessComplete", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvited", auditDetails)
      verify(auditServiceMock, times(7)).logEventNoRequest(any[String], any[Map[String, String]])
    }
  }

  "get time adjustments" should {
    "return None if application's time adjustments are empty" in new OnlineTest {
      onlineTestService.getTimeAdjustments(applicationForOnlineTestingWithNoTimeAdjustments) mustBe (None)
    }

    "return Time Adjustments if application's time adjustments are not empty" in new OnlineTest {
      val result = onlineTestService.getTimeAdjustments(applicationForOnlineTestingWithTimeAdjustments)
      result.isDefined mustBe (true)
      result.get.numericalSectionId mustBe (NumericalSectionId)
      result.get.numericalAbsoluteTime mustBe (7)
      result.get.verbalAndNumericalAssessmentId mustBe (VerbalAndNumericalAssessmentId)
      result.get.verbalSectionId mustBe (VerbalSectionId)
      result.get.verbalAbsoluteTime mustBe (7)
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
    "return an InviteApplication with no time adjustments if gis and application has no time adjustments" in new OnlineTest {
      onlineTestService.buildInviteApplication(applicationForOnlineTestingGisWithNoTimeAdjustments,
        "", CubiksUserId, ScheduleId) must be(inviteApplicantGisWithNoTimeAdjustments)
    }

    "return an InviteApplication with no time adjustments if gis and application has time adjustments" in new OnlineTest {
      onlineTestService.buildInviteApplication(applicationForOnlineTestingGisWithTimeAdjustments,
        "", CubiksUserId, ScheduleId) must be(inviteApplicantGisWithNoTimeAdjustments)
    }

    "return an InviteApplication with no time adjustments if no gis and application has no time adjustments" in new OnlineTest {
      onlineTestService.buildInviteApplication(applicationForOnlineTestingWithNoTimeAdjustments,
        "", CubiksUserId, ScheduleId) must be(inviteApplicantNoGisWithNoTimeAdjustments)
    }

    "return an InviteApplication with time adjustments if no gis and application has time adjustments" in new OnlineTest {
      onlineTestService.buildInviteApplication(applicationForOnlineTestingWithTimeAdjustments,
        "", CubiksUserId, ScheduleId) must be(inviteApplicantNoGisWithTimeAdjustments)
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

    when(tokenFactoryMock.generateUUID()).thenReturn(Token)
    when(onlineTestInvitationDateFactoryMock.nowLocalTimeZone).thenReturn(InvitationDate)

    val onlineTestService = new OnlineTestService {
      val appRepository = appRepositoryMock
      val cdRepository = cdRepositoryMock
      val otRepository = otRepositoryMock
      val trRepository = trRepositoryMock
      val cubiksGatewayClient = cubiksGatewayClientMock
      val emailClient = emailClientMock
      val auditService = auditServiceMock
      val tokenFactory = tokenFactoryMock
      val onlineTestInvitationDateFactory = onlineTestInvitationDateFactoryMock
      val gatewayConfig = testGatewayConfig
    }
  }

  trait SuccessfulTestInviteFixture extends OnlineTest {
    when(cubiksGatewayClientMock.registerApplicant(eqTo(registerApplicant))(any[HeaderCarrier]))
      .thenReturn(Future.successful(registration))
    when(cubiksGatewayClientMock.inviteApplicant(any[InviteApplicant])(any[HeaderCarrier]))
      .thenReturn(Future.successful(invitation))
    when(cdRepositoryMock.find(any[String])).thenReturn(Future.successful(contactDetails))
    when(emailClientMock.sendOnlineTestInvitation(eqTo(EmailContactDetails), eqTo(PreferredName), eqTo(ExpirationDate))(
      any[HeaderCarrier]
    )).thenReturn(Future.successful(()))
    when(otRepositoryMock.insertPhase1TestProfile(any[String], any[Phase1TestProfile])).thenReturn(Future.successful(()))
    when(trRepositoryMock.remove(any[String])).thenReturn(Future.successful(()))
  }
}
