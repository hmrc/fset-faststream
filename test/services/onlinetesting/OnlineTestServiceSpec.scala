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

import common.Phase1TestConcern
import connectors.OnlineTestEmailClient
import factories.{ DateTimeFactory, UUIDFactory }
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses._
import model.exchange.CubiksTestResultReady
import model.persisted.{ ContactDetails, CubiksTest, NotificationExpiringOnlineTest, TestResultNotification }
import model._
import model.events.{ AuditEvents, DataStoreEvents }
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import services.AuditService
import services.events.{ EventService, EventServiceFixture }
import testkit.UnitSpec
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class OnlineTestServiceSpec extends UnitSpec {

  "commitProgressStatus" should {
    "call the corresponding repo method" in new OnlineTest {

      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatuses.ProgressStatus])).thenReturn(success)
      underTest.commitProgressStatus(applicationId, PHASE1_TESTS_EXPIRED)(hc, rh)

      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(applicationId, PHASE1_TESTS_EXPIRED)
      verifyNoMoreInteractions(appRepositoryMock)
      verifyZeroInteractions(cdRepositoryMock, emailClientMock, auditServiceMock, tokenFactoryMock)
    }
  }

  "processNextTestForNotification" should {
    "return a successful unit future if no test to notify is found" in new OnlineTest {
      when(appRepositoryMock.findTestForNotification(Phase1FailedTestType)).thenReturn(Future.successful(None))

      val result = underTest.processNextTestForNotification(Phase1FailedTestType).futureValue
      result mustBe unit

      verify(appRepositoryMock).findTestForNotification(Phase1FailedTestType)
      verifyNoMoreInteractions(appRepositoryMock)
      verifyZeroInteractions(cdRepositoryMock, emailClientMock)
    }

    "return a successful unit future if a test to notify is found" in new OnlineTest {
      when(appRepositoryMock.findTestForNotification(any[NotificationTestType])).thenReturn(successNotification)
      when(cdRepositoryMock.find(any[String])).thenReturn(successContactDetails)
      when(appRepositoryMock.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatuses.ProgressStatus])).thenReturn(success)
      when(emailClientMock.sendEmailWithName(any[String], any[String], any[String])(any[HeaderCarrier])).thenReturn(success)

      val result = underTest.processNextTestForNotification(Phase1FailedTestType).futureValue
      result mustBe unit

      verify(appRepositoryMock).findTestForNotification(Phase1FailedTestType)
      verify(cdRepositoryMock).find(userId)
      verify(appRepositoryMock).addProgressStatusAndUpdateAppStatus(applicationId, Phase1FailedTestType.notificationProgress)
      verify(emailClientMock).sendEmailWithName(email, preferredName, Phase1FailedTestType.template)(hc)
      verifyNoMoreInteractions(appRepositoryMock, cdRepositoryMock, emailClientMock)
    }
  }

  "generateStatusEvents" should {
    "return empty list of event if no event is associated to the given progress status" in new OnlineTest {
      val result = underTest.generateStatusEvents(applicationId, PHASE1_TESTS_STARTED)
      result mustBe Nil
    }

    "return list of event if some is associated to the given expired progress status" in new OnlineTest {
      val expiredStates = PHASE1_TESTS_EXPIRED :: PHASE2_TESTS_EXPIRED :: PHASE3_TESTS_EXPIRED :: Nil
      expiredStates.foreach(status => {
        underTest.generateStatusEvents(applicationId, status) mustBe
          AuditEvents.ApplicationExpired(Map("applicationId" -> applicationId, "status" -> status)) ::
          DataStoreEvents.ApplicationExpired(applicationId) :: Nil
      })
    }

    "return list of event if some is associated to the given success progress status" in new OnlineTest {
      val passedStates = PHASE3_TESTS_SUCCESS_NOTIFIED :: Nil
      passedStates.foreach(status => {
        underTest.generateStatusEvents(applicationId, status) mustBe
          AuditEvents.ApplicationReadyForExport(Map("applicationId" -> applicationId, "status" -> status )) ::
            DataStoreEvents.ApplicationReadyForExport(applicationId) :: Nil
      })
    }
  }

  "generateEmailEvents" should {
    "return empty list of event if no event is associated to the given progress status" in new OnlineTest {
      val result = underTest.generateEmailEvents(applicationId, PHASE1_TESTS_STARTED, email, preferredName, template)
      result mustBe Nil
    }

    "return list of event if some is associated to the given expired progress status" in new OnlineTest {
      val expected = AuditEvents.ExpiredTestEmailSent(Map(
        "applicationId" -> applicationId,
        "emailAddress" -> email,
        "to" -> preferredName,
        "template" -> template)) :: Nil
      val expiredStates = PHASE1_TESTS_EXPIRED :: PHASE2_TESTS_EXPIRED :: PHASE3_TESTS_EXPIRED :: Nil
      expiredStates.foreach(status => {
        underTest.generateEmailEvents(applicationId, status, email, preferredName, template) mustBe expected
      })
    }

    "return list of event if some is associated to the given failed progress status" in new OnlineTest {
      val expected = AuditEvents.FailedTestEmailSent(Map(
        "applicationId" -> applicationId,
        "emailAddress" -> email,
        "to" -> preferredName,
        "template" -> template)) :: Nil
      val failedStates = PHASE1_TESTS_FAILED_NOTIFIED :: PHASE2_TESTS_FAILED_NOTIFIED :: PHASE3_TESTS_FAILED_NOTIFIED :: Nil
      failedStates.foreach(status => {
        underTest.generateEmailEvents(applicationId, status, email, preferredName, template) mustBe expected
      })
    }

    "return list of event if some is associated to the given success progress status" in new OnlineTest {
      val expected = AuditEvents.SuccessTestEmailSent(Map(
        "applicationId" -> applicationId,
        "emailAddress" -> email,
        "to" -> preferredName,
        "template" -> template)) :: Nil
      val passedStates = PHASE3_TESTS_SUCCESS_NOTIFIED :: Nil
      passedStates.foreach(status => {
        underTest.generateEmailEvents(applicationId, status, email, preferredName, template) mustBe expected
      })
    }
  }


  "updateTestReportReady" should {
    "create an updated copy of the cubiksTest when the report is ready" in new OnlineTest {

      val cubiksTest = getCubiksTest(cubiksUserId)
      val result = underTest.updateTestReportReady(cubiksTest, reportReady)

      result.resultsReadyToDownload mustBe true
      result.reportId mustBe reportReady.reportId
      result.reportLinkURL mustBe reportReady.reportLinkURL
      result.reportStatus mustBe Some(reportReady.reportStatus)
      cubiksTest eq result mustBe false
    }

    "create an updated copy of the cubiksTest when the report is not ready" in new OnlineTest {

      val cubiksTest = getCubiksTest(cubiksUserId)
      val result = underTest.updateTestReportReady(cubiksTest, reportReady.copy(reportStatus = "Bogus"))

      result.resultsReadyToDownload mustBe false
      result.reportId mustBe reportReady.reportId
      result.reportLinkURL mustBe reportReady.reportLinkURL
      result.reportStatus mustBe Some("Bogus")
      cubiksTest eq result mustBe false
    }
  }

  "updateCubiksTestsById" should {
    "return an empty list for an empty list of test" in new OnlineTest {
      underTest.updateCubiksTestsById(cubiksUserId, List.empty, updateFn) mustBe List.empty
    }

    "update only the test with the given cubiksUserId" in new OnlineTest {
      val cubiksTests = List(getCubiksTest(cubiksUserId -1), getCubiksTest(cubiksUserId), getCubiksTest(cubiksUserId + 1))
      val result = underTest.updateCubiksTestsById(cubiksUserId, cubiksTests, updateFn)

      result.size mustBe 3
      result.count(t => t.cubiksUserId == cubiksUserId) mustBe 1
      result.filter(t => t.cubiksUserId == cubiksUserId).foreach(t => t.testUrl mustBe "www.bogustest.test")
      result.count(t => t.cubiksUserId != cubiksUserId) mustBe 2
      result.filter(t => t.cubiksUserId != cubiksUserId).foreach(t => t.testUrl mustBe authenticateUrl)
    }
  }

  trait OnlineTest {

    implicit val hc = HeaderCarrier()
    implicit val rh = mock[RequestHeader]

    val appRepositoryMock = mock[GeneralApplicationRepository]
    val cdRepositoryMock = mock[ContactDetailsRepository]
    val emailClientMock = mock[OnlineTestEmailClient]
    val auditServiceMock = mock[AuditService]
    val tokenFactoryMock = mock[UUIDFactory]
    val onlineTestInvitationDateFactoryMock = mock[DateTimeFactory]
    val eventServiceMock = mock[EventService]

    val applicationId = "31009ccc-1ac3-4d55-9c53-1908a13dc5e1"
    val userId = "353bffd0-447e-47f6-b581-6e37ab2906af"
    val preferredName = "George"
    val template = "email_template_for_event"
    val email = "wilfredo.gomez@zzzzzzzzzzzz.vv"
    val invitationDate = DateTime.parse("2016-05-11")
    val cubiksUserId = 98765
    val cubiksScheduleId = 1686854
    val token = "token"
    val authenticateUrl = "http://localhost/authenticate"
    def getCubiksTest(cubiksId: Int) = CubiksTest(scheduleId = cubiksScheduleId,
      usedForResults = true,
      cubiksUserId = cubiksId,
      token = token,
      testUrl = authenticateUrl,
      invitationDate = invitationDate,
      participantScheduleId = 235
    )
    val reportReady = CubiksTestResultReady(reportId = Some(198), reportStatus = "Ready", reportLinkURL = Some("www.report.com"))
    val successContactDetails = Future.successful(ContactDetails(outsideUk = false, Address("London"), Some("N32 6GH"),
      None, email, "0989836387432"))
    val successNotification = Future.successful(Some(TestResultNotification(applicationId, userId, preferredName)))
    val success = Future.successful(())

    def updateFn(cTest: CubiksTest): CubiksTest = cTest.copy(testUrl = "www.bogustest.test")
    val underTest = new TestableOnlineTestService

    class TestableOnlineTestService extends OnlineTestService with Phase1TestConcern with EventServiceFixture {

      override val emailClient = emailClientMock
      override val auditService = auditServiceMock
      override val tokenFactory = tokenFactoryMock
      override val dateTimeFactory = onlineTestInvitationDateFactoryMock
      override val cdRepository = cdRepositoryMock
      override val appRepository = appRepositoryMock
      override val eventService = eventServiceMock

      def nextApplicationReadyForOnlineTesting: Future[List[OnlineTestApplication]] = Future.successful(List.empty)
      def registerAndInviteForTestGroup(application: OnlineTestApplication)
                                       (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = success
      def registerAndInviteForTestGroup(applications: List[OnlineTestApplication])
                                       (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = success
      def processNextExpiredTest(expiryTest: TestExpirationEvent)
                                (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = success
      def processNextTestForReminder(reminder: ReminderNotice)
                                    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = success
      def emailCandidateForExpiringTestReminder(expiringTest: NotificationExpiringOnlineTest, emailAddress: String, reminder: ReminderNotice)
                                               (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = success

      override def nextTestGroupWithReportReady: Future[Option[RichTestGroup]] = Future.successful(None)

      override def retrieveTestResult(testProfile: RichTestGroup)
        (implicit hc: HeaderCarrier): Future[Unit] = Future.successful(())
    }
  }
}
