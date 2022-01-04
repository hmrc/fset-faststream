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

package services.onlinetesting

import connectors.OnlineTestEmailClient
import factories.{ DateTimeFactory, UUIDFactory }
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses._
import model._
import model.exchange.PsiRealTimeResults
import model.persisted._
import model.stc.StcEventTypes._
import model.stc.{ AuditEvents, DataStoreEvents }
import org.joda.time.DateTime
import play.api.Logging
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.onlinetesting.OnlineTestRepository
import services.AuditService
import services.sift.ApplicationSiftService
import services.stc.EventSink
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

// This is the guice injected version
trait OnlineTestService extends TimeExtension with EventSink with Logging {
  type U <: Test
  type T <: TestProfile[U]
  type RichTestGroup <: TestGroupWithIds[U, T]
  type TestRepository <: OnlineTestRepository // TestRepository must be a class that implements trait OnlineTestRepository

  val emailClient: OnlineTestEmailClient
  val auditService: AuditService
  val tokenFactory: UUIDFactory
  val dateTimeFactory: DateTimeFactory
  val cdRepository: ContactDetailsRepository
  val appRepository: GeneralApplicationRepository
  val testRepository: TestRepository
  val siftService: ApplicationSiftService

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  def nextApplicationsReadyForOnlineTesting(maxBatchSize: Int): Future[List[OnlineTestApplication]]
  def registerAndInviteForTestGroup(application: OnlineTestApplication)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit]
  def registerAndInviteForTestGroup(applications: List[OnlineTestApplication])(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit]
  // PSI specific
  def registerAndInvite(applications: List[OnlineTestApplication])(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit]
  // PSI specific
  def storeRealTimeResults(orderId: String, results: PsiRealTimeResults)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit]
  def emailCandidateForExpiringTestReminder(expiringTest: NotificationExpiringOnlineTest, emailAddress: String, reminder: ReminderNotice)
                                           (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit]
  def nextTestGroupWithReportReady: Future[Option[RichTestGroup]]
  def retrieveTestResult(testProfile: RichTestGroup)(implicit hc: HeaderCarrier): Future[Unit]

  def processNextTestForNotification(notificationType: NotificationTestType, phase: String, operation: String)
                                    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    appRepository.findTestForNotification(notificationType).flatMap {
      case Some(test) =>
        logger.warn(s"Candidate found to notify they successfully $operation tests in $phase - appId=${test.applicationId}")
        processTestForNotification(test, notificationType)
      case None =>
        logger.warn(s"No candidate found to notify they successfully $operation tests in $phase")
        Future.successful(())
    }
  }

  def processNextTestForSdipFsNotification(notificationType: NotificationTestTypeSdipFs)
                                          (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    appRepository.findTestForSdipFsNotification(notificationType).flatMap {
      case Some(test) => processTestForSdipFsNotification(test, notificationType)
      case None => Future.successful(())
    }
  }

  def processNextExpiredTest(expiryTest: TestExpirationEvent)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    testRepository.nextExpiringApplication(expiryTest).flatMap {
      case Some(expiredCandidate) =>
        logger.info(s"Found candidate ${expiredCandidate.applicationId} to expire in ${expiryTest.phase}")
        processExpiredTest(expiredCandidate, expiryTest)
      case None =>
        logger.info(s"No candidates found to expire in ${expiryTest.phase}")
        Future.successful(())
    }
  }

  def processNextTestForReminder(reminder: model.ReminderNotice)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] =
    testRepository.nextTestForReminder(reminder).flatMap {
      case Some(expiringTest) => processReminder(expiringTest, reminder)
      case None => Future.successful(())
    }

  protected def processTestForNotification(toNotify: TestResultNotification, `type`: NotificationTestType)
                                          (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    for {
      emailAddress <- candidateEmailAddress(toNotify.userId)
      _ <- commitProgressStatus(toNotify.applicationId, `type`.notificationProgress)
      _ <- emailCandidate(toNotify.applicationId, toNotify.preferredName, emailAddress, `type`.template, `type`.notificationProgress)
    } yield ()
  }

  protected def processTestForSdipFsNotification(toNotify: TestResultSdipFsNotification, `type`: NotificationTestTypeSdipFs)
                                                (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val notificationProgressStatus = Try {
      (`type`, toNotify.applicationStatus) match {
        case(_: FailedTestTypeSdipFs, status) =>
          getProgressStatusForSdipFsFailedNotified(status)
      }
    }

    for {
      notificationStatus <- Future.fromTry(notificationProgressStatus)
      emailAddress <- candidateEmailAddress(toNotify.userId)
      _ <- commitProgressStatus(toNotify.applicationId, notificationStatus)
      _ <- emailCandidate(toNotify.applicationId, toNotify.preferredName, emailAddress, `type`.template, notificationStatus)
    } yield ()
  }

  protected def emailInviteToApplicant(application: OnlineTestApplication, emailAddress: String,
                                       invitationDate: DateTime, expirationDate: DateTime
                                      )(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val preferredName = application.preferredName
    emailClient.sendOnlineTestInvitation(emailAddress, preferredName, expirationDate).map { _ =>
      audit("OnlineTestInvitationEmailSent", application.userId, Some(emailAddress))
    }
  }

  protected def calcOnlineTestDates(expiryTimeInDays: Int): (DateTime, DateTime) = {
    val invitationDate = dateTimeFactory.nowLocalTimeZone
    val expirationDate = invitationDate.plusDays(expiryTimeInDays)
    (invitationDate, expirationDate)
  }

  @deprecated("use event sink instead", "2016-10-18")
  protected def audit(event: String, userId: String, emailAddress: Option[String] = None): Unit = {
    logger.info(s"$event for user $userId")
    auditService.logEventNoRequest(
      event,
      Map("userId" -> userId) ++ emailAddress.map("email" -> _).toMap
    )
  }

  protected def processExpiredTest(expiringTest: ExpiringOnlineTest, expiryType: TestExpirationEvent)
                                  (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    for {
      emailAddress <- candidateEmailAddress(expiringTest.userId)
      _ <- commitProgressStatus(expiringTest.applicationId, expiryType.expiredStatus)
      _ <- emailCandidate(expiringTest.applicationId, expiringTest.preferredName, emailAddress, expiryType.template, expiryType.expiredStatus)
    } yield ()
  }

  // TODO: cubiks specific
  /*
  def updateTestReportReady(cubiksTest: CubiksTest, reportReady: CubiksTestResultReady) = cubiksTest.copy(
    resultsReadyToDownload = reportReady.reportStatus == "Ready",
    reportId = reportReady.reportId,
    reportLinkURL = reportReady.reportLinkURL,
    reportStatus = Some(reportReady.reportStatus)
  )*/

  // TODO: cubiks specific
  /*
  def updateCubiksTestsById(cubiksUserId: Int, cubiksTests: List[CubiksTest], updateFn: CubiksTest => CubiksTest) = cubiksTests.collect {
    case t if t.cubiksUserId == cubiksUserId => updateFn(t)
    case t => t
  }*/

  // TODO: cubiks specific
  /*
  def assertUniqueTestByCubiksUserId(cubiksTests: List[CubiksTest], cubiksUserId: Int) = {
    val requireUserIdOnOnlyOneTestCount = cubiksTests.count(_.cubiksUserId == cubiksUserId)
    require(requireUserIdOnOnlyOneTestCount == 1, s"Cubiks userid $cubiksUserId was on $requireUserIdOnOnlyOneTestCount tests!")
  }*/

  private[services] def getAdjustedTime(minimum: Int, maximum: Int, percentageToIncrease: Int) = {
    val adjustedValue = math.ceil(minimum.toDouble * (1 + percentageToIncrease / 100.0))
    math.min(adjustedValue, maximum).toInt
  }

  private def emailCandidate(applicationId: String, preferredName: String, emailAddress: String, template: String,
                             status: ProgressStatuses.ProgressStatus)
                            (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    emailClient.sendEmailWithName(emailAddress, preferredName, template).map {
      _ => generateEmailEvents(applicationId, status, emailAddress, preferredName, template)
    }
  }

  def commitProgressStatus(applicationId: String, status: ProgressStatuses.ProgressStatus)
                          (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    appRepository.addProgressStatusAndUpdateAppStatus(applicationId, status).map { _ =>
      generateStatusEvents(applicationId, status: ProgressStatuses.ProgressStatus)
    }
  }

  private[onlinetesting] def generateStatusEvents(applicationId: String, status: ProgressStatuses.ProgressStatus): StcEvents = {
    val expiredStates = PHASE1_TESTS_EXPIRED :: PHASE2_TESTS_EXPIRED :: PHASE3_TESTS_EXPIRED :: Nil
    val passedStates = PHASE3_TESTS_PASSED_NOTIFIED :: Nil

    if(expiredStates.contains(status)) {
      AuditEvents.ApplicationExpired(Map("applicationId" -> applicationId, "status" -> status )) ::
        DataStoreEvents.ApplicationExpired(applicationId) :: Nil
    }
    else if(passedStates.contains(status)) {
      AuditEvents.ApplicationReadyForExport(Map("applicationId" -> applicationId, "status" -> status )) ::
        DataStoreEvents.ApplicationReadyForExport(applicationId) :: Nil
    }
    else { Nil }
  }

  private[onlinetesting] def generateEmailEvents(applicationId: String,
                                                 status: ProgressStatuses.ProgressStatus,
                                                 email: String, to: String, template: String): StcEvents = {

    val expiredStates = PHASE1_TESTS_EXPIRED :: PHASE2_TESTS_EXPIRED :: PHASE3_TESTS_EXPIRED :: Nil
    val failedStates = PHASE1_TESTS_FAILED_NOTIFIED :: PHASE2_TESTS_FAILED_NOTIFIED :: PHASE3_TESTS_FAILED_NOTIFIED :: Nil
    val passedStates = PHASE3_TESTS_PASSED_NOTIFIED :: Nil
    val data = Map("applicationId" -> applicationId, "emailAddress" -> email, "to" -> to, "template" -> template)
    if(expiredStates.contains(status)) {
      AuditEvents.ExpiredTestEmailSent(data) :: Nil
    }
    else if (failedStates.contains(status)) {
      AuditEvents.FailedTestEmailSent(data) :: Nil
    }
    else if(passedStates.contains(status)) {
      AuditEvents.SuccessTestEmailSent(data) :: Nil
    }
    else { Nil }
  }

  protected def candidateEmailAddress(userId: String): Future[String] = cdRepository.find(userId).map(_.email)

  protected def processReminder(expiringTest: NotificationExpiringOnlineTest,
                                reminder: ReminderNotice)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] =
    for {
      emailAddress <- candidateEmailAddress(expiringTest.userId)
      _ <- commitNotificationExpiringTestProgressStatus(expiringTest, reminder, emailAddress)
      _ <- emailCandidateForExpiringTestReminder(expiringTest, emailAddress, reminder)
    } yield ()

  private def commitNotificationExpiringTestProgressStatus(
                                                            expiringTest: NotificationExpiringOnlineTest,
                                                            reminder: ReminderNotice,
                                                            email: String)(
                                                            implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    appRepository.addProgressStatusAndUpdateAppStatus(expiringTest.applicationId, reminder.progressStatuses).map { _ =>
      AuditEvents.ApplicationExpiryReminder(Map("applicationId" -> expiringTest.applicationId, "status" -> reminder.progressStatuses )) ::
        DataStoreEvents.ApplicationExpiryReminder(expiringTest.applicationId) :: Nil
    }
  }
}

trait TimeExtension {
  val dateTimeFactory: DateTimeFactory

  def extendTime(alreadyExpired: Boolean, previousExpirationDate: DateTime): Int => DateTime = { extraDays: Int =>
    if (alreadyExpired) {
      dateTimeFactory.nowLocalTimeZone.plusDays(extraDays)
    } else {
      previousExpirationDate.plusDays(extraDays)
    }
  }
}
