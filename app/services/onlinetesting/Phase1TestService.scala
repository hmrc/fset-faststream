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

import _root_.services.AuditService
import akka.actor.ActorSystem
import common.FutureEx
import config.CubiksGatewayConfig
import connectors.ExchangeObjects._
import connectors.{ CSREmailClient, CubiksGatewayClient }
import factories.{ DateTimeFactory, UUIDFactory }
import model.OnlineTestCommands._
import model.events.{ AuditEvents, DataStoreEvents }
import model.exchange.{ CubiksTestResultReady, Phase1TestGroupWithNames }
import model.persisted.{ CubiksTest, Phase1TestProfile, Phase1TestWithUserIds, TestResult => _, _ }
import model.{ ProgressStatuses, ReminderNotice, TestExpirationEvent }
import org.joda.time.DateTime
import play.api.mvc.RequestHeader
import repositories._
import repositories.onlinetesting.Phase1TestRepository
import services.events.EventService
import services.onlinetesting.Exceptions.ReportIdNotDefinedException
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }
import scala.language.postfixOps
import scala.util.{ Failure, Success, Try }

object Phase1TestService extends Phase1TestService {

  import config.MicroserviceAppConfig._

  val appRepository = applicationRepository
  val cdRepository = faststreamContactDetailsRepository
  val phase1TestRepo = phase1TestRepository
  val trRepository = testReportRepository
  val cubiksGatewayClient = CubiksGatewayClient
  val tokenFactory = UUIDFactory
  val dateTimeFactory = DateTimeFactory
  val emailClient = CSREmailClient
  val auditService = AuditService
  val gatewayConfig = cubiksGatewayConfig
  val actor = ActorSystem()
  val eventService = EventService
}

trait Phase1TestService extends OnlineTestService with ResetPhase1Test {
  val actor: ActorSystem
  val phase1TestRepo: Phase1TestRepository
  val trRepository: TestReportRepository
  val cubiksGatewayClient: CubiksGatewayClient
  val gatewayConfig: CubiksGatewayConfig

  override def nextApplicationReadyForOnlineTesting: Future[List[OnlineTestApplication]] = {
    phase1TestRepo.nextApplicationsReadyForOnlineTesting
  }

  override def processNextTestForReminder(reminder: ReminderNotice)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    phase1TestRepo.nextTestForReminder(reminder).flatMap {
      case Some(expiringTest) => processReminder(expiringTest, reminder)
      case None => Future.successful(())
    }
  }

  override def emailCandidateForExpiringTestReminder(expiringTest: NotificationExpiringOnlineTest,
                                                     emailAddress: String,
                                                     reminder: ReminderNotice)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    emailClient.sendTestExpiringReminder(emailAddress, expiringTest.preferredName,
      reminder.hoursBeforeReminder, reminder.timeUnit, expiringTest.expiryDate).map { _ =>
      audit(s"ReminderPhase1ExpiringOnlineTestNotificationBefore${reminder.hoursBeforeReminder}HoursEmailed",
        expiringTest.userId, Some(emailAddress))
    }
  }

  override def nextTestGroupWithReportReady: Future[Option[Phase1TestWithUserIds]] = {
    phase1TestRepo.nextTestGroupWithReportReady
  }

  def getTestProfile(applicationId: String): Future[Option[Phase1TestGroupWithNames]] = {
    for {
      phase1Opt <- phase1TestRepo.getTestGroup(applicationId)
    } yield {
      phase1Opt.map { phase1 =>
        val sjqTests = phase1.activeTests filter (_.scheduleId == sjq)
        val bqTests = phase1.activeTests filter (_.scheduleId == bq)
        require(sjqTests.length <= 1)
        require(bqTests.length <= 1)

        Phase1TestGroupWithNames(
          phase1.expirationDate, Map()
            ++ (if (sjqTests.nonEmpty) Map("sjq" -> sjqTests.head) else Map())
            ++ (if (bqTests.nonEmpty) Map("bq" -> bqTests.head) else Map())
        )
      }
    }
  }

  private def sjq = gatewayConfig.phase1Tests.scheduleIds("sjq")

  private def bq = gatewayConfig.phase1Tests.scheduleIds("bq")

  override def registerAndInviteForTestGroup(applications: List[OnlineTestApplication])
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    Future.sequence(applications.map { application =>
      registerAndInviteForTestGroup(application)
    }).map(_ => ())
  }

  override def registerAndInviteForTestGroup(application: OnlineTestApplication)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    registerAndInviteForTestGroup(application, getScheduleNamesForApplication(application))
  }

  override def processNextExpiredTest(expiryTest: TestExpirationEvent)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    phase1TestRepo.nextExpiringApplication(expiryTest).flatMap{
      case Some(expired) => processExpiredTest(expired, expiryTest)
      case None => Future.successful(())
    }
  }

  def resetTests(application: OnlineTestApplication, testNamesToRemove: List[String], actionTriggeredBy: String)
                (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    for {
      - <- registerAndInviteForTestGroup(application, testNamesToRemove)
    } yield {
      AuditEvents.Phase1TestsReset(Map("userId" -> application.userId, "tests" -> testNamesToRemove.mkString(","))) ::
        DataStoreEvents.OnlineExerciseReset(application.applicationId, actionTriggeredBy) ::
        Nil
    }
  }

  def registerAndInviteForTestGroup(application: OnlineTestApplication, scheduleNames: List[String])
                                   (implicit hc: HeaderCarrier): Future[Unit] = {
    val (invitationDate, expirationDate) = calcOnlineTestDates(gatewayConfig.phase1Tests.expiryTimeInDays)

    def mapValue[T](f: Future[T]): Future[Try[T]] = {
      val prom = Promise[Try[T]]()
      f onComplete prom.success
      prom.future
    }

    // TODO work out a better way to do this
    // The problem is that the standard future sequence returns at the point when the first future has failed
    // but doesn't actually wait until all futures are complete. This can be problematic for tests which assert
    // the something has or hasn't worked. It is also a bit nasty in production where processing can still be
    // going on in the background.
    // The approach to fixing it here is to generate futures that return Try[A] and then all futures will be
    // traversed. Afterward, we look at the results and clear up the mess
    // We space out calls to Cubiks because it appears they fail when they are too close together.
    val registerAndInvite = FutureEx.traverseToTry(scheduleNames.zipWithIndex) {
      case (scheduleName, delayModifier) =>
        val scheduleId = scheduleIdByName(scheduleName)
        val delay = delayModifier.second
        akka.pattern.after(delay, actor.scheduler)(
          registerAndInviteApplicant(application, scheduleId, invitationDate, expirationDate)
        )
    }

    val registerAndInviteProcess = registerAndInvite.flatMap { phase1TestsRegs =>
      phase1TestsRegs.collect { case Failure(e) => throw e }
      val successfullyRegisteredTests = phase1TestsRegs.collect { case Success(t) => t }.toList
      markAsInvited(application)(Phase1TestProfile(expirationDate = expirationDate, tests = successfullyRegisteredTests))
    }

    for {
      _ <- registerAndInviteProcess
      emailAddress <- candidateEmailAddress(application.userId)
      _ <- emailInviteToApplicant(application, emailAddress, invitationDate, expirationDate)
    } yield audit("OnlineTestInvitationProcessComplete", application.userId)
  }

  private def registerAndInviteApplicant(application: OnlineTestApplication, scheduleId: Int, invitationDate: DateTime,
                                         expirationDate: DateTime
                                        )(implicit hc: HeaderCarrier): Future[CubiksTest] = {
    val authToken = tokenFactory.generateUUID()

    for {
      userId <- registerApplicant(application, authToken)
      invitation <- inviteApplicant(application, authToken, userId, scheduleId)
      _ <- trRepository.remove(application.applicationId)
    } yield {
      CubiksTest(scheduleId = scheduleId,
        usedForResults = true,
        cubiksUserId = invitation.userId,
        token = authToken,
        invitationDate = invitationDate,
        participantScheduleId = invitation.participantScheduleId,
        testUrl = invitation.authenticateUrl
      )
    }
  }

  override def retrieveTestResult(testProfile: Phase1TestWithUserIds)(implicit hc: HeaderCarrier): Future[Unit] = {

    def insertTests(testResults: List[(TestResult, CubiksTest)]): Future[Unit] = {
      Future.sequence(testResults.map {
        case (result, phase1Test) => phase1TestRepo.insertPhase1TestResult(testProfile.applicationId,
          phase1Test, model.persisted.TestResult.fromCommandObject(result)
        )
      }).map(_ => ())
    }

    def maybeUpdateProgressStatus(appId: String) = {
      phase1TestRepo.getTestGroup(appId).flatMap { eventualProfile =>

        val latestProfile = eventualProfile.getOrElse(throw new Exception(s"No profile returned for $appId"))

        if (latestProfile.activeTests.forall(_.testResult.isDefined)) {
          phase1TestRepo.updateProgressStatus(appId, ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED).map( _ =>
            audit(s"ProgressStatusSet${ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED}", appId)
          )
        } else {
          Future.successful(())
        }
      }
    }

    val testResults = Future.sequence(testProfile.testGroup.activeTests.map { test =>
      test.reportId.map { reportId =>
        cubiksGatewayClient.downloadXmlReport(reportId)
      }.map(_.map(_ -> test))
    }.flatten)

    for {
      eventualTestResults <- testResults
      _ <- insertTests(eventualTestResults)
      _ <- maybeUpdateProgressStatus(testProfile.applicationId)
    } yield {
      audit(s"ResultsRetrievedForSchedule", testProfile.applicationId)
    }

  }

  def registerApplicant(application: OnlineTestApplication, token: String)(implicit hc: HeaderCarrier): Future[Int] = {
    val preferredName = CubiksSanitizer.sanitizeFreeText(application.preferredName)
    val registerApplicant = RegisterApplicant(preferredName, "", token + "@" + gatewayConfig.emailDomain)
    cubiksGatewayClient.registerApplicant(registerApplicant).map { registration =>
      audit("UserRegisteredForOnlineTest", application.userId)
      registration.userId
    }
  }

  private def inviteApplicant(application: OnlineTestApplication, authToken: String, userId: Int, scheduleId: Int)
                             (implicit hc: HeaderCarrier): Future[Invitation] = {

    val inviteApplicant = buildInviteApplication(application, authToken, userId, scheduleId)
    cubiksGatewayClient.inviteApplicant(inviteApplicant).map { invitation =>
      audit("UserInvitedToOnlineTest", application.userId)
      invitation
    }
  }

  private def markAsInvited(application: OnlineTestApplication)
                           (newOnlineTestProfile: Phase1TestProfile): Future[Unit] = for {
    currentOnlineTestProfile <- phase1TestRepo.getTestGroup(application.applicationId)
    updatedOnlineTestProfile = merge(currentOnlineTestProfile, newOnlineTestProfile)
    _ <- phase1TestRepo.insertOrUpdateTestGroup(application.applicationId, updatedOnlineTestProfile)
    _ <- phase1TestRepo.removeTestProfileProgresses(application.applicationId, determineStatusesToRemove(updatedOnlineTestProfile))
  } yield {
    audit("OnlineTestInvited", application.userId)
  }

  private def merge(currentProfile: Option[Phase1TestProfile], newProfile: Phase1TestProfile): Phase1TestProfile = currentProfile match {
    case None =>
      newProfile
    case Some(profile) =>
      val scheduleIdsToArchive = newProfile.tests.map(_.scheduleId)
      val existingTestsAfterUpdate = profile.tests.map(t =>
        if (scheduleIdsToArchive.contains(t.scheduleId)) {
          t.copy(usedForResults = false)
        } else {
          t
        }
      )
      Phase1TestProfile(newProfile.expirationDate, existingTestsAfterUpdate ++ newProfile.tests)
  }

  private def getScheduleNamesForApplication(application: OnlineTestApplication) = {
    if (application.guaranteedInterview) {
      gatewayConfig.phase1Tests.gis
    } else {
      gatewayConfig.phase1Tests.standard
    }
  }

  private def scheduleIdByName(name: String): Int = {
    gatewayConfig.phase1Tests.scheduleIds.getOrElse(name, throw new IllegalArgumentException(s"Incorrect test name: $name"))
  }

  private[services] def buildInviteApplication(application: OnlineTestApplication, token: String, userId: Int, scheduleId: Int) = {
    val scheduleCompletionBaseUrl = s"${gatewayConfig.candidateAppUrl}/fset-fast-stream/online-tests/phase1"
    if (application.guaranteedInterview) {
      InviteApplicant(scheduleId,
        userId,
        s"$scheduleCompletionBaseUrl/complete/$token",
        resultsURL = None
      )
    } else {
      val scheduleCompletionUrl = if (scheduleIdByName("sjq") == scheduleId) {
        s"$scheduleCompletionBaseUrl/continue/$token"
      } else {
        s"$scheduleCompletionBaseUrl/complete/$token"
      }

      InviteApplicant(scheduleId, userId, scheduleCompletionUrl, resultsURL = None)
    }
  }

  def markAsStarted(cubiksUserId: Int, startedTime: DateTime = dateTimeFactory.nowLocalTimeZone)
                   (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    val updatedPhase1Test = updatePhase1Test(cubiksUserId, t => t.copy(startedDateTime = Some(startedTime)))
    updatedPhase1Test flatMap { u =>
      phase1TestRepo.updateProgressStatus(u.applicationId, ProgressStatuses.PHASE1_TESTS_STARTED) map { _ =>
        DataStoreEvents.OnlineExerciseStarted(u.applicationId) :: Nil
      }
    }
  }

  def markAsCompleted(cubiksUserId: Int)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    val updatedPhase1Test = updatePhase1Test(cubiksUserId, t => t.copy(completedDateTime = Some(dateTimeFactory.nowLocalTimeZone)))
    updatedPhase1Test flatMap { u =>
      require(u.testGroup.activeTests.nonEmpty, "Active tests cannot be found")
      val activeTestsCompleted = u.testGroup.activeTests forall (_.completedDateTime.isDefined)
      activeTestsCompleted match {
        case true =>
          phase1TestRepo.updateProgressStatus(u.applicationId, ProgressStatuses.PHASE1_TESTS_COMPLETED) map { _ =>
            DataStoreEvents.OnlineExercisesCompleted(u.applicationId) ::
              DataStoreEvents.AllOnlineExercisesCompleted(u.applicationId) ::
              Nil
          }
        case false =>
          Future.successful(DataStoreEvents.OnlineExercisesCompleted(u.applicationId) :: Nil)
      }
    }
  }

  def markAsCompleted(token: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    phase1TestRepo.getTestProfileByToken(token).flatMap { p =>
      p.tests.find(_.token == token).map {test => markAsCompleted(test.cubiksUserId)}
        .getOrElse(Future.successful(()))
    }
  }

  def markAsReportReadyToDownload(cubiksUserId: Int, reportReady: CubiksTestResultReady): Future[Unit] = {
    updatePhase1Test(cubiksUserId, updateTestReportReady(_:CubiksTest, reportReady)).flatMap { updated =>
      val allResultReadyToDownload = updated.testGroup.activeTests forall (_.resultsReadyToDownload)
      allResultReadyToDownload match {
        case true => phase1TestRepo.updateProgressStatus(updated.applicationId, ProgressStatuses.PHASE1_TESTS_RESULTS_READY)
        case false => Future.successful(())
      }
    }
  }

  // TODO: We need to stop updating the entire group here and use selective $set, this method of replacing the entire document
  // invites race conditions
  private def updatePhase1Test(cubiksUserId: Int, update: CubiksTest => CubiksTest):
  Future[Phase1TestWithUserIds] = {
    def createUpdateTestGroup(p: Phase1TestWithUserIds): Phase1TestWithUserIds = {
      val testGroup = p.testGroup
      assertUniqueTestByCubiksUserId(testGroup.tests, cubiksUserId)
      val updatedTestGroup = testGroup.copy(tests = updateCubiksTestsById(cubiksUserId, testGroup.tests, update))
      Phase1TestWithUserIds(p.applicationId, p.userId, updatedTestGroup)
    }
    for {
      p1TestProfile <- phase1TestRepo.getTestProfileByCubiksId(cubiksUserId)
      updated = createUpdateTestGroup(p1TestProfile)
      _ <- phase1TestRepo.insertOrUpdateTestGroup(updated.applicationId, updated.testGroup)
    } yield {
      updated
    }
  }

}

trait ResetPhase1Test {

  import ProgressStatuses._

  def determineStatusesToRemove(testGroup: Phase1TestProfile): List[ProgressStatus] = {
    (if (testGroup.hasNotStartedYet) List(PHASE1_TESTS_STARTED) else List()) ++
    (if (testGroup.hasNotCompletedYet) List(PHASE1_TESTS_COMPLETED) else List()) ++
    (if (testGroup.hasNotResultReadyToDownloadForAllTestsYet) List(PHASE1_TESTS_RESULTS_RECEIVED, PHASE1_TESTS_RESULTS_READY) else List())
  }
}
