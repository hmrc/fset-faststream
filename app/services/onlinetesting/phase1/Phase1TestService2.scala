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

import akka.actor.ActorSystem
import common.{ FutureEx, Phase1TestConcern2 }
import config.TestIntegrationGatewayConfig
import connectors.ExchangeObjects._
import connectors.{ CSREmailClient, OnlineTestsGatewayClient }
import factories.{ DateTimeFactory, UUIDFactory }
import model.Exceptions.ApplicationNotFound
import model.OnlineTestCommands._
import model._
import model.exchange.{ Phase1TestGroupWithNames2, PsiTestResultReady }
import model.persisted.{ CubiksTest, Phase1TestProfile, PsiTestResult => _, TestResult => _, _ }
import model.stc.DataStoreEvents
import org.joda.time.DateTime
import play.api.Logger
import play.api.mvc.RequestHeader
import repositories._
import repositories.onlinetesting.{ Phase1TestRepository, Phase1TestRepository2 }
import services.AuditService
import services.onlinetesting.{ CubiksSanitizer, OnlineTestService }
import services.sift.ApplicationSiftService
import services.stc.StcEventService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{ Failure, Success }

object Phase1TestService2 extends Phase1TestService2 {

  import config.MicroserviceAppConfig._

  val appRepository = applicationRepository
  val cdRepository = faststreamContactDetailsRepository
  val testRepository = phase1TestRepository //TODO: remove this from OnlineTestService trait
  val testRepository2 = phase1TestRepository2
  val onlineTestsGatewayClient = OnlineTestsGatewayClient
  val tokenFactory = UUIDFactory
  val dateTimeFactory = DateTimeFactory
  val emailClient = CSREmailClient
  val auditService = AuditService
  val integrationGatewayConfig = testIntegrationGatewayConfig
  val actor = ActorSystem()
  val eventService = StcEventService
  val siftService = ApplicationSiftService
}

trait Phase1TestService2 extends OnlineTestService with Phase1TestConcern2 with ResetPhase1Test2 {
  type TestRepository = Phase1TestRepository
  val actor: ActorSystem
  val testRepository: Phase1TestRepository
  val testRepository2: Phase1TestRepository2
  val onlineTestsGatewayClient: OnlineTestsGatewayClient
  val integrationGatewayConfig: TestIntegrationGatewayConfig
  val delaySecsBetweenRegistrations = 1

  //// psi code start
  override def registerAndInviteForPsi(applications: List[OnlineTestApplication])
                                      (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    Future.sequence(applications.map { application =>
      registerAndInviteForTestGroup2(application)
    }).map(_ => ())
  }

  private def registerAndInviteForTestGroup2(application: OnlineTestApplication)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    registerAndInviteForTestGroup2(application, getScheduleNamesForApplication(application))
  }

  def registerAndInviteForTestGroup2(application: OnlineTestApplication, scheduleNames: List[String])
                                    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val (invitationDate, expirationDate) = calcOnlineTestDates(integrationGatewayConfig.phase1Tests.expiryTimeInDays)

    // TODO work out a better way to do this
    // The problem is that the standard future sequence returns at the point when the first future has failed
    // but doesn't actually wait until all futures are complete. This can be problematic for tests which assert
    // the something has or hasn't worked. It is also a bit nasty in production where processing can still be
    // going on in the background.
    // The approach to fixing it here is to generate futures that return Try[A] and then all futures will be
    // traversed. Afterward, we look at the results and clear up the mess
    // We space out calls to Cubiks because it appears they fail when they are too close together.
    // After zipWithIndex scheduleNames = ( ("sjq", 0), ("bq", 1) )

    val registerCandidate = FutureEx.traverseToTry(scheduleNames.zipWithIndex) {
      case (scheduleName, delayModifier) =>
        val inventoryId = inventoryIdByName2(scheduleName) // sjq = 16196, bq = 16194
      val delay = (delayModifier * delaySecsBetweenRegistrations).second
        akka.pattern.after(delay, actor.scheduler) {
          play.api.Logger.debug(s"Phase1TestService - about to call registerPsiApplicant with scheduleId - $inventoryId")
          registerPsiApplicant(application, inventoryId, invitationDate, expirationDate)
        }
    }

    val processRegistration = registerCandidate.flatMap { phase1TestsRegistrations =>
      phase1TestsRegistrations.collect { case Failure(e) => throw e }
      val successfullyRegisteredTests = phase1TestsRegistrations.collect { case Success(t) => t }.toList
      markAsInvited2(application)(Phase1TestProfile2(expirationDate = expirationDate, tests = successfullyRegisteredTests))
    }

    for {
      _ <- processRegistration
      emailAddress <- candidateEmailAddress(application.userId)
      _ <- emailInviteToApplicant(application, emailAddress, invitationDate, expirationDate)
    } yield audit("OnlineTestInvitationProcessComplete", application.userId)
  }

  private def registerPsiApplicant(application: OnlineTestApplication,
                                   inventoryId: String, invitationDate: DateTime,
                                   expirationDate: DateTime)
                                  (implicit hc: HeaderCarrier): Future[PsiTest] = {
    for {
      aoa <- registerApplicant2(application, inventoryId)
    } yield {
      if (aoa.status != AssessmentOrderAcknowledgement.acknowledgedStatus) {
        val msg = s"Received response status of ${aoa.status} when registering candidate " +
          s"${application.applicationId} to phase1 tests whose inventoryId=$inventoryId"
        Logger.warn(msg)
        throw new RuntimeException(msg)
      } else {
        PsiTest(
          inventoryId = inventoryId,
          orderId = aoa.orderId,
          usedForResults = true,
          testUrl = aoa.testLaunchUrl,
          invitationDate = invitationDate
        )
      }
    }
  }

  private def registerApplicant2(application: OnlineTestApplication, inventoryId: String)(
    implicit hc: HeaderCarrier): Future[AssessmentOrderAcknowledgement] = {

    val orderId = tokenFactory.generateUUID()
    val preferredName = CubiksSanitizer.sanitizeFreeText(application.preferredName)
    val lastName = CubiksSanitizer.sanitizeFreeText(application.lastName)

    // TODO: This is for phase 2
    val maybePercentage = application.eTrayAdjustments.flatMap(_.percentage)

    val registerCandidateRequest = RegisterCandidateRequest(
      inventoryId = inventoryId, // Read from config to identify the test we are registering for
      orderId = orderId, // Identifier we generate to uniquely identify the test
      accountId = application.testAccountId, // Candidate's account across all tests
      preferredName = preferredName,
      lastName = lastName,
      // The url psi will redirect to when the candidate completes the test
      redirectionUrl = buildRedirectionUrl(orderId, inventoryId)
    )

    onlineTestsGatewayClient.psiRegisterApplicant(registerCandidateRequest).map { response =>
      audit("UserRegisteredForOnlineTest", application.userId)
      response
    }
  }

  private def inventoryIdByName2(name: String): String = {
    integrationGatewayConfig.phase1Tests.inventoryIds.getOrElse(name, throw new IllegalArgumentException(s"Incorrect test name: $name"))
  }

  private def buildRedirectionUrl(orderId: String, inventoryId: String) = {
    val scheduleCompletionBaseUrl = s"${integrationGatewayConfig.candidateAppUrl}/fset-fast-stream/online-tests/psi/phase1"
    s"$scheduleCompletionBaseUrl/complete/$orderId"
  }

  private def markAsInvited2(application: OnlineTestApplication)(newOnlineTestProfile: Phase1TestProfile2): Future[Unit] = for {
    currentOnlineTestProfile <- testRepository2.getTestGroup(application.applicationId)
    updatedTestProfile <- insertOrAppendNewTests2(application.applicationId, currentOnlineTestProfile, newOnlineTestProfile)
    _ <- testRepository.resetTestProfileProgresses(application.applicationId, determineStatusesToRemove2(updatedTestProfile))
  } yield {
    audit("OnlineTestInvited", application.userId)
  }

  // This also handles archiving any existing tests to which the candidate has previously been invited
  // eg if a test is being reset and the candidate is being invited to a replacement test
  private def insertOrAppendNewTests2(applicationId: String, currentProfile: Option[Phase1TestProfile2],
                                     newProfile: Phase1TestProfile2): Future[Phase1TestProfile2] = {
    (currentProfile match {
      case None => testRepository2.insertOrUpdateTestGroup(applicationId, newProfile)
      case Some(profile) =>
        val orderIdsToArchive = newProfile.tests.map(_.orderId)
        val existingActiveTestOrderIds = profile.tests.filter(t =>
          orderIdsToArchive.contains(t.orderId) && t.usedForResults).map(_.orderId)
        Future.traverse(existingActiveTestOrderIds)(testRepository2.markTestAsInactive2).flatMap { _ =>
          testRepository2.insertPsiTests(applicationId, newProfile)
        }
    }).flatMap { _ => testRepository2.getTestGroup(applicationId)
    }.map {
      case Some(testProfile) => testProfile
      case None => throw ApplicationNotFound(applicationId)
    }
  }

  def getTestGroup2(applicationId: String): Future[Option[Phase1TestGroupWithNames2]] = {
    for {
      phase1Opt <- testRepository2.getTestGroup(applicationId)
    } yield {
      phase1Opt.map { testProfile =>
        Phase1TestGroupWithNames2(testProfile.expirationDate, testProfile.tests)
      }
    }
  }

  //TODO: these 2 methods need optimising (just copied across from cubiks impl)
  def markAsCompleted2(orderId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    testRepository2.getTestProfileByOrderId(orderId).flatMap { p =>
      p.tests.find(_.orderId == orderId).map { test => markAsCompleted22(test.orderId) } // TODO: here handle if we do not find the data
        .getOrElse(Future.successful(()))
    }
  }

  def markAsCompleted22(orderId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    updatePhase1Test2(orderId, testRepository2.updateTestCompletionTime2(_: String, dateTimeFactory.nowLocalTimeZone)) flatMap { u =>
      require(u.testGroup.activeTests.nonEmpty, "Active tests cannot be found")
      val activeTestsCompleted = u.testGroup.activeTests forall (_.completedDateTime.isDefined)
      if (activeTestsCompleted) {
        testRepository2.updateProgressStatus(u.applicationId, ProgressStatuses.PHASE1_TESTS_COMPLETED) map { _ =>
          DataStoreEvents.OnlineExercisesCompleted(u.applicationId) ::
            DataStoreEvents.AllOnlineExercisesCompleted(u.applicationId) ::
            Nil
        }
      } else {
        Future.successful(DataStoreEvents.OnlineExercisesCompleted(u.applicationId) :: Nil)
      }
    }
  }

  def markAsStarted2(orderId: String, startedTime: DateTime = dateTimeFactory.nowLocalTimeZone)
                   (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    updatePhase1Test2(orderId, testRepository2.updateTestStartTime(_: String, startedTime)) flatMap { u =>
      testRepository2.updateProgressStatus(u.applicationId, ProgressStatuses.PHASE1_TESTS_STARTED) map { _ =>
        DataStoreEvents.OnlineExerciseStarted(u.applicationId) :: Nil
      }
    }
  }

  private def updatePhase1Test2(orderId: String, updatePsiTest: String => Future[Unit]): Future[Phase1TestGroupWithUserIds2] = {
    for {
      _ <- updatePsiTest(orderId)
      updated <- testRepository2.getTestGroupByOrderId(orderId)
    } yield {
      updated
    }
  }

  def markAsReportReadyToDownload2(orderId: String, reportReady: PsiTestResultReady): Future[Unit] = {
    updatePhase1Test2(orderId, testRepository2.updateTestReportReady2(_: String, reportReady)).flatMap { updated =>
      val allResultReadyToDownload = updated.testGroup.activeTests forall (_.resultsReadyToDownload)
      if (allResultReadyToDownload) {
        testRepository2.updateProgressStatus(updated.applicationId, ProgressStatuses.PHASE1_TESTS_RESULTS_READY)
      } else {
        Future.successful(())
      }
    }
  }

  override def retrieveTestResult(testProfile: RichTestGroup)(implicit hc: HeaderCarrier): Future[Unit] = {

    def insertTests(testResults: List[(PsiTestResult, PsiTest)]): Future[Unit] = {
      Future.sequence(testResults.map {
        case (result, phase1Test) => testRepository2.insertTestResult2(
          testProfile.applicationId,
          phase1Test, model.persisted.PsiTestResult.fromCommandObject(result)
        )
      }).map(_ => ())
    }

    def maybeUpdateProgressStatus(appId: String) = {
      testRepository2.getTestGroup(appId).flatMap { eventualProfile =>

        val latestProfile = eventualProfile.getOrElse(throw new Exception(s"No test profile returned for $appId"))
        if (latestProfile.activeTests.forall(_.testResult.isDefined)) {
          testRepository2.updateProgressStatus(appId, ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED).map(_ =>
            audit(s"ProgressStatusSet${ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED}", appId))
        } else {
          val msg = s"Did not update progress status to ${ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED} for $appId - " +
            s"not all active tests have a testResult saved"
          Logger.warn(msg)
          Future.successful(())
        }
      }
    }

    val testsWithoutTestResult = testProfile.testGroup.activeTests.filterNot(_.testResult.isDefined)

    val testResults = Future.sequence(testsWithoutTestResult.flatMap { test =>
      test.reportId.map { reportId =>
        Logger.info(s"Now fetching test results for reportId=$reportId and orderId=${test.orderId} for candidate ${testProfile.applicationId}")
        onlineTestsGatewayClient.downloadPsiTestResults(reportId)
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

  private def getScheduleNamesForApplication(application: OnlineTestApplication) = {
    if (application.guaranteedInterview) {
      integrationGatewayConfig.phase1Tests.gis
    } else {
      integrationGatewayConfig.phase1Tests.standard
    }
  }

  override def nextTestGroupWithReportReady: Future[Option[Phase1TestGroupWithUserIds2]] = {
    testRepository2.nextTestGroupWithReportReady
  }
  //// psi code  end

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

  //TODO: look at removing the cubiks specific code
  override def registerAndInviteForTestGroup(applications: List[OnlineTestApplication])
                                            (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    Future.sequence(applications.map { application =>
      registerAndInviteForTestGroup(application)
    }).map(_ => ())
  }

  override def registerAndInviteForTestGroup(application: OnlineTestApplication)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    registerAndInviteForTestGroup(application, getScheduleNamesForApplication(application))
  }

  def registerAndInviteForTestGroup(application: OnlineTestApplication, scheduleNames: List[String])
                                   (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val (invitationDate, expirationDate) = calcOnlineTestDates(integrationGatewayConfig.phase1Tests.expiryTimeInDays)

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

  // TODO: cubiks specific - we need to remove
  private def scheduleIdByName(name: String): Int = {
    16196
//    integrationGatewayConfig.phase1Tests.inventoryIds.getOrElse(name, throw new IllegalArgumentException(s"Incorrect test name: $name"))
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

  // TODO: this is cubiks specific - check to see if we can remove
  private def registerApplicant(application: OnlineTestApplication, token: String)(implicit hc: HeaderCarrier): Future[Int] = {
    val preferredName = CubiksSanitizer.sanitizeFreeText(application.preferredName)
    val registerApplicant = RegisterApplicant(preferredName, "", token + "@" + integrationGatewayConfig.emailDomain)
    onlineTestsGatewayClient.registerApplicant(registerApplicant).map { registration =>
      audit("UserRegisteredForOnlineTest", application.userId)
      registration.userId
    }
  }

  private def inviteApplicant(application: OnlineTestApplication, authToken: String, userId: Int, scheduleId: Int)
                             (implicit hc: HeaderCarrier): Future[Invitation] = {

    val inviteApplicant = buildInviteApplication(application, authToken, userId, scheduleId)
    onlineTestsGatewayClient.inviteApplicant(inviteApplicant).map { invitation =>
      audit("UserInvitedToOnlineTest", application.userId)
      invitation
    }
  }

  //TODO: cubiks specific - check to see if we can remove
  private[services] def buildInviteApplication(application: OnlineTestApplication, token: String, userId: Int, scheduleId: Int) = {
    val scheduleCompletionBaseUrl = s"${integrationGatewayConfig.candidateAppUrl}/fset-fast-stream/online-tests/phase1"
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

  override def nextApplicationsReadyForOnlineTesting(maxBatchSize: Int): Future[List[OnlineTestApplication]] =
    testRepository.nextApplicationsReadyForOnlineTesting(maxBatchSize)
}

trait ResetPhase1Test2 {

  import ProgressStatuses._

  def determineStatusesToRemove(testGroup: Phase1TestProfile): List[ProgressStatus] = {
    (if (testGroup.hasNotStartedYet) List(PHASE1_TESTS_STARTED) else List()) ++
      (if (testGroup.hasNotCompletedYet) List(PHASE1_TESTS_COMPLETED) else List()) ++
      (if (testGroup.hasNotResultReadyToDownloadForAllTestsYet) List(PHASE1_TESTS_RESULTS_RECEIVED, PHASE1_TESTS_RESULTS_READY) else List()) ++
      List(PHASE1_TESTS_FAILED, PHASE1_TESTS_FAILED_NOTIFIED, PHASE1_TESTS_FAILED_SDIP_AMBER, PHASE1_TESTS_FAILED_SDIP_GREEN)
  }

  def determineStatusesToRemove2(testGroup: Phase1TestProfile2): List[ProgressStatus] = {
    (if (testGroup.hasNotStartedYet) List(PHASE1_TESTS_STARTED) else List()) ++
      (if (testGroup.hasNotCompletedYet) List(PHASE1_TESTS_COMPLETED) else List()) ++
      (if (testGroup.hasNotResultReadyToDownloadForAllTestsYet) List(PHASE1_TESTS_RESULTS_RECEIVED, PHASE1_TESTS_RESULTS_READY) else List()) ++
      List(PHASE1_TESTS_FAILED, PHASE1_TESTS_FAILED_NOTIFIED, PHASE1_TESTS_FAILED_SDIP_AMBER, PHASE1_TESTS_FAILED_SDIP_GREEN)
  }
}
