/*
 * Copyright 2019 HM Revenue & Customs
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

import services.AuditService
import akka.actor.ActorSystem
import common.{ FutureEx, Phase1TestConcern }
import config.CubiksGatewayConfig
import connectors.ExchangeObjects._
import connectors.{ CSREmailClient, CubiksGatewayClient }
import factories.{ DateTimeFactory, UUIDFactory }
import model.Exceptions.ApplicationNotFound
import model.OnlineTestCommands._
import model.stc.{ AuditEvents, DataStoreEvents }
import model.exchange.{ CubiksTestResultReady, Phase1TestGroupWithNames }
import model.persisted.{ CubiksTest, Phase1TestGroupWithUserIds, Phase1TestProfile, TestResult => _, _ }
import model._
import org.joda.time.DateTime
import play.api.Logger
import play.api.mvc.RequestHeader
import repositories._
import repositories.onlinetesting.Phase1TestRepository
import services.stc.StcEventService
import services.onlinetesting.{ CubiksSanitizer, OnlineTestService }
import services.sift.ApplicationSiftService

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.{ Failure, Success }
import uk.gov.hmrc.http.HeaderCarrier

object Phase1TestService extends Phase1TestService {
  import config.MicroserviceAppConfig._

  val appRepository = applicationRepository
  val cdRepository = faststreamContactDetailsRepository
  val testRepository = phase1TestRepository
  val cubiksGatewayClient = CubiksGatewayClient
  val tokenFactory = UUIDFactory
  val dateTimeFactory = DateTimeFactory
  val emailClient = CSREmailClient
  val auditService = AuditService
  val gatewayConfig = cubiksGatewayConfig
  val actor = ActorSystem()
  val eventService = StcEventService
  val siftService = ApplicationSiftService
}

trait Phase1TestService extends OnlineTestService with Phase1TestConcern with ResetPhase1Test {
  type TestRepository = Phase1TestRepository
  val actor: ActorSystem
  val testRepository: Phase1TestRepository
  val cubiksGatewayClient: CubiksGatewayClient
  val gatewayConfig: CubiksGatewayConfig
  val delaySecsBetweenRegistrations = 1


  override def registerAndInviteForPsi(applications: List[OnlineTestApplication])
                                      (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    cubiksGatewayClient.psiRegisterApplicant().map { response =>
      Logger.info("==================================================")
      Logger.info(s"Response from gateway: $response")
      ()
    }
  }

  override def nextApplicationsReadyForOnlineTesting(maxBatchSize: Int): Future[List[OnlineTestApplication]] =
    testRepository.nextApplicationsReadyForOnlineTesting(maxBatchSize)

  def nextSdipFaststreamCandidateReadyForSdipProgression: Future[Option[Phase1TestGroupWithUserIds]] = {
    phase1TestRepository.nextSdipFaststreamCandidateReadyForSdipProgression
  }

  def progressSdipFaststreamCandidateForSdip(o: Phase1TestGroupWithUserIds): Future[Unit] = {

    o.testGroup.evaluation.map { evaluation =>
      val result = evaluation.result.find(_.schemeId == SchemeId("Sdip")).getOrElse(
        throw new IllegalStateException(s"No SDIP results found for application ${o.applicationId}}")
      )

      val newProgressStatus = result.result match {
        case "Green" => ProgressStatuses.getProgressStatusForSdipFsSuccess(ApplicationStatus.PHASE1_TESTS)
        case "Red" => ProgressStatuses.getProgressStatusForSdipFsFailed(ApplicationStatus.PHASE1_TESTS)
      }

      testRepository.updateProgressStatusOnly(o.applicationId, newProgressStatus)
    }.getOrElse(Future.successful(()))
  }

  override def emailCandidateForExpiringTestReminder(
    expiringTest: NotificationExpiringOnlineTest,
    emailAddress: String,
    reminder: ReminderNotice
  )(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    emailClient.sendTestExpiringReminder(emailAddress, expiringTest.preferredName,
      reminder.hoursBeforeReminder, reminder.timeUnit, expiringTest.expiryDate).map { _ =>
      audit(
        s"ReminderPhase1ExpiringOnlineTestNotificationBefore${reminder.hoursBeforeReminder}HoursEmailed",
        expiringTest.userId, Some(emailAddress)
      )
    }
  }

  override def nextTestGroupWithReportReady: Future[Option[Phase1TestGroupWithUserIds]] = {
    testRepository.nextTestGroupWithReportReady
  }

  def getTestGroup(applicationId: String): Future[Option[Phase1TestGroupWithNames]] = {
    for {
      phase1Opt <- testRepository.getTestGroup(applicationId)
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

  override def registerAndInviteForTestGroup(application: OnlineTestApplication)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    registerAndInviteForTestGroup(application, getScheduleNamesForApplication(application))
  }

  override def processNextExpiredTest(expiryTest: TestExpirationEvent)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    testRepository.nextExpiringApplication(expiryTest).flatMap {
      case Some(expired) => processExpiredTest(expired, expiryTest)
      case None => Future.successful(())
    }
  }

  def resetTests(application: OnlineTestApplication, testNamesToRemove: List[String], actionTriggeredBy: String)
                (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    for {
      _ <- registerAndInviteForTestGroup(application, testNamesToRemove)
    } yield {
      AuditEvents.Phase1TestsReset(Map("userId" -> application.userId, "tests" -> testNamesToRemove.mkString(","))) ::
        DataStoreEvents.OnlineExerciseReset(application.applicationId, actionTriggeredBy) ::
        Nil
    }
  }

  def registerAndInviteForTestGroup(application: OnlineTestApplication, scheduleNames: List[String])
                                   (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val (invitationDate, expirationDate) = calcOnlineTestDates(gatewayConfig.phase1Tests.expiryTimeInDays)

    // TODO work out a better way to do this
    // The problem is that the standard future sequence returns at the point when the first future has failed
    // but doesn't actually wait until all futures are complete. This can be problematic for tests which assert
    // the something has or hasn't worked. It is also a bit nasty in production where processing can still be
    // going on in the background.
    // The approach to fixing it here is to generate futures that return Try[A] and then all futures will be
    // traversed. Afterward, we look at the results and clear up the mess
    // We space out calls to Cubiks because it appears they fail when they are too close together.
    // After zipWithIndex scheduleNames = ( ("sjq", 0), ("bq", 1) )

    val registerAndInvite = FutureEx.traverseToTry(scheduleNames.zipWithIndex) {
      case (scheduleName, delayModifier) =>
        val scheduleId = scheduleIdByName(scheduleName) // sjq = 16196, bq = 16194
        val delay = (delayModifier * delaySecsBetweenRegistrations).second
        akka.pattern.after(delay, actor.scheduler) {
          play.api.Logger.debug(s"Phase1TestService - about to call registerAndInviteApplicant with scheduleId - $scheduleId")
          registerAndInviteApplicant(application, scheduleId, invitationDate, expirationDate)
        }
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

  private def registerAndInviteApplicant(application: OnlineTestApplication,
                                         scheduleId: Int, invitationDate: DateTime,
                                         expirationDate: DateTime)
                                        (implicit hc: HeaderCarrier): Future[CubiksTest] = {
    val authToken = tokenFactory.generateUUID()

    for {
      userId <- registerApplicant(application, authToken)
      invitation <- inviteApplicant(application, authToken, userId, scheduleId)
    } yield {
      CubiksTest(
        scheduleId = scheduleId,
        usedForResults = true,
        cubiksUserId = invitation.userId,
        token = authToken,
        invitationDate = invitationDate,
        participantScheduleId = invitation.participantScheduleId,
        testUrl = invitation.authenticateUrl
      )
    }
  }

  override def retrieveTestResult(testProfile: RichTestGroup)(implicit hc: HeaderCarrier): Future[Unit] = {

    def insertTests(testResults: List[(TestResult, CubiksTest)]): Future[Unit] = {
      Future.sequence(testResults.map {
        case (result, phase1Test) => testRepository.insertTestResult(
          testProfile.applicationId,
          phase1Test, model.persisted.TestResult.fromCommandObject(result)
        )
      }).map(_ => ())
    }

    def maybeUpdateProgressStatus(appId: String) = {
      testRepository.getTestGroup(appId).flatMap { eventualProfile =>

        val latestProfile = eventualProfile.getOrElse(throw new Exception(s"No profile returned for $appId"))

        if (latestProfile.activeTests.forall(_.testResult.isDefined)) {
          testRepository.updateProgressStatus(appId, ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED).map(_ =>
            audit(s"ProgressStatusSet${ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED}", appId))
        } else {
          Future.successful(())
        }
      }
    }

    val testsWithoutTestResult = testProfile.testGroup.activeTests.filterNot(_.testResult.isDefined)

    val testResults = Future.sequence(testsWithoutTestResult.flatMap { test =>
      test.reportId.map { reportId =>
        cubiksGatewayClient.downloadXmlReport(reportId)
      }.map(_.map(_ -> test))
    })

    for {
      eventualTestResults <- testResults
      _ <- insertTests(eventualTestResults)
      _ <- maybeUpdateProgressStatus(testProfile.applicationId)
    } yield {
      audit(s"ResultsRetrievedForSchedule", testProfile.applicationId)
    }
  }

  private def registerApplicant(application: OnlineTestApplication, token: String)(implicit hc: HeaderCarrier): Future[Int] = {
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

  private def markAsInvited(application: OnlineTestApplication)(newOnlineTestProfile: Phase1TestProfile): Future[Unit] = for {
    currentOnlineTestProfile <- testRepository.getTestGroup(application.applicationId)
    updatedTestProfile <- insertOrAppendNewTests(application.applicationId, currentOnlineTestProfile, newOnlineTestProfile)
    _ <- testRepository.resetTestProfileProgresses(application.applicationId, determineStatusesToRemove(updatedTestProfile))
  } yield {
    audit("OnlineTestInvited", application.userId)
  }

  private def insertOrAppendNewTests(applicationId: String, currentProfile: Option[Phase1TestProfile],
                    newProfile: Phase1TestProfile): Future[Phase1TestProfile] = {
    (currentProfile match {
      case None => testRepository.insertOrUpdateTestGroup(applicationId, newProfile)
      case Some(profile) =>
        val scheduleIdsToArchive = newProfile.tests.map(_.scheduleId)
        val existingActiveTests = profile.tests.filter(t =>
          scheduleIdsToArchive.contains(t.scheduleId) && t.usedForResults).map(_.cubiksUserId)
        Future.traverse(existingActiveTests)(testRepository.markTestAsInactive).flatMap { _ =>
          testRepository.insertCubiksTests(applicationId, newProfile)
        }
    }).flatMap { _ => testRepository.getTestGroup(applicationId)
    }.map {
      case Some(testProfile) => testProfile
      case None => throw ApplicationNotFound(applicationId)
    }
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
      InviteApplicant(
        scheduleId,
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
    updatePhase1Test(cubiksUserId, testRepository.updateTestStartTime(_:Int, startedTime)) flatMap { u =>
      testRepository.updateProgressStatus(u.applicationId, ProgressStatuses.PHASE1_TESTS_STARTED) map { _ =>
        DataStoreEvents.OnlineExerciseStarted(u.applicationId) :: Nil
      }
    }
  }

  def markAsCompleted(cubiksUserId: Int)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    updatePhase1Test(cubiksUserId, testRepository.updateTestCompletionTime(_: Int, dateTimeFactory.nowLocalTimeZone)) flatMap { u =>
      require(u.testGroup.activeTests.nonEmpty, "Active tests cannot be found")
      val activeTestsCompleted = u.testGroup.activeTests forall (_.completedDateTime.isDefined)
      if (activeTestsCompleted) {
        testRepository.updateProgressStatus(u.applicationId, ProgressStatuses.PHASE1_TESTS_COMPLETED) map { _ =>
          DataStoreEvents.OnlineExercisesCompleted(u.applicationId) ::
            DataStoreEvents.AllOnlineExercisesCompleted(u.applicationId) ::
            Nil
        }
      } else {
        Future.successful(DataStoreEvents.OnlineExercisesCompleted(u.applicationId) :: Nil)
      }
    }
  }

  def markAsCompleted(token: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    testRepository.getTestProfileByToken(token).flatMap { p =>
      p.tests.find(_.token == token).map { test => markAsCompleted(test.cubiksUserId) }
        .getOrElse(Future.successful(()))
    }
  }

  def markAsReportReadyToDownload(cubiksUserId: Int, reportReady: CubiksTestResultReady): Future[Unit] = {
    updatePhase1Test(cubiksUserId, testRepository.updateTestReportReady(_:Int, reportReady)).flatMap { updated =>
      val allResultReadyToDownload = updated.testGroup.activeTests forall (_.resultsReadyToDownload)
      if (allResultReadyToDownload) {
        testRepository.updateProgressStatus(updated.applicationId, ProgressStatuses.PHASE1_TESTS_RESULTS_READY)
      } else {
        Future.successful(())
      }
    }
  }

  private def updatePhase1Test(cubiksUserId: Int, updateCubiksTest: Int => Future[Unit]): Future[Phase1TestGroupWithUserIds] = {
    for {
      _ <- updateCubiksTest(cubiksUserId)
      updated <- testRepository.getTestProfileByCubiksId(cubiksUserId)
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
      (if (testGroup.hasNotResultReadyToDownloadForAllTestsYet) List(PHASE1_TESTS_RESULTS_RECEIVED, PHASE1_TESTS_RESULTS_READY) else List()) ++
      List(PHASE1_TESTS_FAILED, PHASE1_TESTS_FAILED_NOTIFIED, PHASE1_TESTS_FAILED_SDIP_AMBER, PHASE1_TESTS_FAILED_SDIP_GREEN)
  }
}
