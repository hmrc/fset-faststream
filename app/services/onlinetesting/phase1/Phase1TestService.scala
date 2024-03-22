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

import org.apache.pekko.actor.ActorSystem
import com.google.inject.name.Named
import common.{FutureEx, Phase1TestConcern}
import config.{MicroserviceAppConfig, OnlineTestsGatewayConfig, PsiTestIds}
import connectors.ExchangeObjects._
import connectors.{OnlineTestEmailClient, OnlineTestsGatewayClient}
import factories.{DateTimeFactory, UUIDFactory}

import javax.inject.{Inject, Singleton}
import model.Exceptions._
import model.OnlineTestCommands._
import model.ProgressStatuses.PHASE1_TESTS_STARTED
import model._
import model.exchange.{Phase1TestGroupWithNames, PsiRealTimeResults}
import model.persisted.{Phase1TestProfile, PsiTestResult => _, _}
import model.stc.{AuditEvents, DataStoreEvents}
import play.api.Logging
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.onlinetesting.Phase1TestRepository
import services.AuditService
import services.onlinetesting.Exceptions.{TestCancellationException, TestRegistrationException}
import services.onlinetesting.{OnlineTestService, TextSanitizer}
import services.sift.ApplicationSiftService
import services.stc.StcEventService
import uk.gov.hmrc.http.HeaderCarrier

import java.time.OffsetDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

@Singleton
class Phase1TestService @Inject() (appConfig: MicroserviceAppConfig,
                                   val appRepository: GeneralApplicationRepository,
                                   val cdRepository: ContactDetailsRepository,
                                   val testRepository: Phase1TestRepository,
                                   val onlineTestsGatewayClient: OnlineTestsGatewayClient,
                                   val tokenFactory: UUIDFactory,
                                   val dateTimeFactory: DateTimeFactory,
                                   @Named("CSREmailClient") val emailClient: OnlineTestEmailClient,
                                   val auditService: AuditService,
                                   val siftService: ApplicationSiftService,
                                   val eventService: StcEventService,
                                   val actor: ActorSystem
                                  )(
  implicit ec: ExecutionContext) extends OnlineTestService with Phase1TestConcern with ResetPhase1Test with Logging with Schemes {

  type TestRepository = Phase1TestRepository

  val onlineTestsGatewayConfig: OnlineTestsGatewayConfig = appConfig.onlineTestsGatewayConfig

  override def nextApplicationsReadyForOnlineTesting(maxBatchSize: Int): Future[Seq[OnlineTestApplication]] =
    testRepository.nextApplicationsReadyForOnlineTesting(maxBatchSize)

  def nextSdipFaststreamCandidateReadyForSdipProgression: Future[Option[Phase1TestGroupWithUserIds]] = {
    testRepository.nextSdipFaststreamCandidateReadyForSdipProgression
  }

  def progressSdipFaststreamCandidateForSdip(o: Phase1TestGroupWithUserIds): Future[Unit] = {

    o.testGroup.evaluation.map { evaluation =>
      val result = evaluation.result.find(_.schemeId == Sdip).getOrElse(
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

  def getTestGroup(applicationId: String): Future[Option[Phase1TestGroupWithNames]] = {
    for {
      phase1Opt <- testRepository.getTestGroup(applicationId)
    } yield {
      phase1Opt.map { testProfile =>
        Phase1TestGroupWithNames(applicationId, testProfile.expirationDate, testProfile.activeTests.map(_.toExchange))
      }
    }
  }

  private def registerCandidateForTests(application: OnlineTestApplication, testNames: List[String])
                                    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val (invitationDate, expirationDate) = calcOnlineTestDates(onlineTestsGatewayConfig.phase1Tests.expiryTimeInDays)

    // TODO work out a better way to do this
    // The problem is that the standard future sequence returns at the point when the first future has failed
    // but doesn't actually wait until all futures are complete. This can be problematic for tests which assert
    // the something has or hasn't worked. It is also a bit nasty in production where processing can still be
    // going on in the background.
    // The approach to fixing it here is to generate futures that return Try[A] and then all futures will be
    // traversed. Afterward, we look at the results and clear up the mess
    // We space out calls to the test provider because it appears they fail when they are too close together.
    // After zipWithIndex testNames = ( ("test1", 0), ("test2", 1) , ("test3", 2), ("test4", 3))

    val registerCandidate = FutureEx.traverseToTry(testNames.zipWithIndex) {
      case (testName, delayModifier) =>
        val testIds = testIdsByName(testName)
        val delay = (delayModifier * onlineTestsGatewayConfig.phase1Tests.testRegistrationDelayInSecs).second
        org.apache.pekko.pattern.after(delay, actor.scheduler) {
          logger.debug(s"Phase1TestService - about to call registerPsiApplicant with testIds - $testIds")
          registerPsiApplicant(application, testIds, invitationDate)
        }
    }

    val processRegistration = registerCandidate.flatMap { phase1TestsRegistrations =>
      phase1TestsRegistrations.collect { case Failure(e) => throw e }
      val successfullyRegisteredTests = phase1TestsRegistrations.collect { case Success(t) => t }.toList
      markAsInvited(application)(Phase1TestProfile(expirationDate = expirationDate, tests = successfullyRegisteredTests))
    }

    for {
      _ <- processRegistration
      emailAddress <- candidateEmailAddress(application.userId)
      _ <- emailInviteToApplicant(application, emailAddress, invitationDate, expirationDate)
    } yield audit("OnlineTestInvitationProcessComplete", application.userId)
  }

  override def registerAndInviteForTestGroup(applications: Seq[OnlineTestApplication])
                                            (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    Future.sequence(applications.map { application =>
      registerAndInviteForTestGroup(application)
    }).map(_ => ())
  }

  override def registerAndInviteForTestGroup(application: OnlineTestApplication)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    registerCandidateForTests(application, getTestNamesForApplication(application))
  }

  override def processNextExpiredTest(expiryTest: TestExpirationEvent)(
    implicit hc: HeaderCarrier, rh: RequestHeader, ec: ExecutionContext): Future[Unit] = {
    testRepository.nextExpiringApplication(expiryTest).flatMap {
      case Some(expired) =>
        logger.warn(s"Expiring candidates for PHASE1 - expiring candidate ${expired.applicationId}")
        processExpiredTest(expired, expiryTest)
      case None =>
        logger.warn(s"Expiring candidates for PHASE1 - none found")
        Future.successful(())
    }
  }

  def resetTest(application: OnlineTestApplication, orderIdToReset: String, actionTriggeredBy: String)
               (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {

    val (invitationDate, expirationDate) = calcOnlineTestDates(onlineTestsGatewayConfig.phase1Tests.expiryTimeInDays)

    for {
      // Fetch existing test group that should exist
      maybeTestGroup <- testRepository.getTestGroup(application.applicationId)
      testGroup = maybeTestGroup
        .getOrElse(throw CannotFindTestGroupByApplicationIdException(s"appId - ${application.applicationId}"))

      // Extract test that requires reset
      testToReset = testGroup.tests.find(_.orderId == orderIdToReset)
        .getOrElse(throw CannotFindTestByOrderIdException(s"OrderId - $orderIdToReset"))
      _ = logger.info(s"testToReset -- $testToReset")

      // Create PsiIds to use for re-invitation
      psiIds = onlineTestsGatewayConfig.phase1Tests.tests.find {
        case (_, ids) => ids.inventoryId == testToReset.inventoryId
      }.getOrElse(throw CannotFindTestByInventoryIdException(s"InventoryId - ${testToReset.inventoryId}"))._2
      _ = logger.info(s"psiIds -- $psiIds")

      // Register applicant
      newPsiTest <- registerPsiApplicant(application, psiIds, invitationDate)
      _ = logger.info(s"newPsiTest -- $newPsiTest")

      // Set old test to inactive
      testsWithInactiveTest = testGroup.tests
        .map { t => if (t.orderId == orderIdToReset) { t.copy(usedForResults = false) } else t }
      _ = logger.info(s"testsWithInactiveTest -- $testsWithInactiveTest")

      // insert new test and maintain test order
      idxOfResetTest = testGroup.tests.indexWhere(_.orderId == orderIdToReset)
      updatedTests = insertTest(testsWithInactiveTest, idxOfResetTest, newPsiTest)
      _ = logger.info(s"updatedTests -- $updatedTests")

      _ <- markAsInvited(application)(Phase1TestProfile(expirationDate, updatedTests))
      emailAddress <- candidateEmailAddress(application.userId)
      _ <- emailInviteToApplicant(application, emailAddress, invitationDate, expirationDate)
    } yield {
      List(
        AuditEvents.Phase1TestsReset(Map("userId" -> application.userId, "orderId" -> orderIdToReset)),
        DataStoreEvents.OnlineExerciseReset(application.applicationId, actionTriggeredBy)
      )
    }
  }

  private def insertTest(ls: List[PsiTest], i: Int, value: PsiTest): List[PsiTest] = {
    val (front, back) = ls.splitAt(i)
    front ++ List(value) ++ back
  }

  private def registerPsiApplicant(application: OnlineTestApplication,
                                   testIds: PsiTestIds, invitationDate: OffsetDateTime)
                                  (implicit hc: HeaderCarrier): Future[PsiTest] = {
    for {
      aoa <- registerApplicant(application, testIds)
    } yield {
      if (aoa.status != AssessmentOrderAcknowledgement.acknowledgedStatus) {
        val msg = s"Received unexpected response status of ${aoa.status} when registering candidate " +
          s"${application.applicationId} to phase1 tests with inventoryId:${testIds.inventoryId}"
        logger.warn(msg)
        throw TestRegistrationException(msg)
      } else {
        logger.warn(s"Phase1 candidate ${application.applicationId} successfully invited to P1 test - inventoryId:${testIds.inventoryId}")
        PsiTest(
          inventoryId = testIds.inventoryId,
          orderId = aoa.orderId,
          usedForResults = true,
          testUrl = aoa.testLaunchUrl,
          invitationDate = invitationDate,
          assessmentId = testIds.assessmentId,
          reportId = testIds.reportId,
          normId = testIds.normId
        )
      }
    }
  }

  private def registerApplicant(application: OnlineTestApplication, testIds: PsiTestIds)(
    implicit hc: HeaderCarrier): Future[AssessmentOrderAcknowledgement] = {

    val orderId = tokenFactory.generateUUID()
    val preferredName = TextSanitizer.sanitizeFreeText(application.preferredName)
    val lastName = TextSanitizer.sanitizeFreeText(application.lastName)

    val registerCandidateRequest = RegisterCandidateRequest(
      inventoryId = testIds.inventoryId, // Read from config to identify the test we are registering for
      orderId = orderId, // Identifier we generate to uniquely identify the test
      accountId = application.testAccountId, // Candidate's account across all tests
      preferredName = preferredName,
      lastName = lastName,
      // The url psi will redirect to when the candidate completes the test
      redirectionUrl = buildRedirectionUrl(orderId, testIds.inventoryId),
      assessmentId = testIds.assessmentId,
      reportId = testIds.reportId,
      normId = testIds.normId
    )

    onlineTestsGatewayClient.psiRegisterApplicant(registerCandidateRequest).map { response =>
      audit("UserRegisteredForOnlineTest", application.userId)
      response
    }
  }

  private def cancelPsiTest(appId: String,
                            userId: String,
                            orderId: String): Future[AssessmentCancelAcknowledgementResponse] = {
    val req = CancelCandidateTestRequest(orderId)
    onlineTestsGatewayClient.psiCancelTest(req).map { response =>
      logger.debug(s"Response from cancellation for orderId=$orderId is $response")
      if (response.status != AssessmentCancelAcknowledgementResponse.completedStatus) {
        logger.debug(s"Cancellation failed with errors: ${response.details}")
        throw TestCancellationException(s"appId=$appId, orderId=$orderId")
      } else {
        audit("TestCancelledForCandidate", userId)
        response
      }
    }
  }

  private def testIdsByName(name: String): PsiTestIds = {
    onlineTestsGatewayConfig.phase1Tests.tests
      .getOrElse(name, throw new IllegalArgumentException(s"Incorrect test name: $name"))
  }

  private def buildRedirectionUrl(orderId: String, inventoryId: String) = {
    val scheduleCompletionBaseUrl = s"${onlineTestsGatewayConfig.candidateAppUrl}/fset-fast-stream/online-tests/psi/phase1"
    s"$scheduleCompletionBaseUrl/complete/$orderId"
  }

  private def markAsInvited(application: OnlineTestApplication)(newOnlineTestProfile: Phase1TestProfile): Future[Unit] = for {
    currentOnlineTestProfile <- testRepository.getTestGroup(application.applicationId)
    updatedTestProfile <- insertOrAppendNewTests(application.applicationId, currentOnlineTestProfile, newOnlineTestProfile)
    _ <- testRepository.resetTestProfileProgresses(
      application.applicationId, determineStatusesToRemove(updatedTestProfile), ignoreNoRecordUpdated = true
    )
  } yield {
    audit("OnlineTestInvited", application.userId)
  }

  // This also handles archiving any existing tests to which the candidate has previously been invited
  // eg if a test is being reset and the candidate is being invited to a replacement test
  private def insertOrAppendNewTests(applicationId: String, currentProfile: Option[Phase1TestProfile],
                                      newProfile: Phase1TestProfile): Future[Phase1TestProfile] = {

    testRepository.insertOrUpdateTestGroup(applicationId, newProfile).flatMap { _ =>
      testRepository.getTestGroup(applicationId).map {
        case Some(testProfile) => testProfile
        case None => throw ApplicationNotFound(applicationId)
      }
    }
  }

  def getTestGroupByOrderId(orderId: String): Future[Phase1TestGroupWithNames] = {
    for {
      phase1 <- testRepository.getTestGroupByOrderId(orderId)
    } yield
      Phase1TestGroupWithNames(
        phase1.applicationId,
        phase1.testGroup.expirationDate,
        phase1.testGroup.tests.map(_.toExchange)
      )
  }

  def markAsCompleted(orderId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    testRepository.getTestProfileByOrderId(orderId).flatMap { p =>
      p.tests.find(_.orderId == orderId).map { test => setTestCompletedTime(test.orderId) } // TODO: here handle if we do not find the data
        .getOrElse(Future.successful(()))
    }
  }

  private def setTestCompletedTime(orderId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] =
    eventSink {
      updatePhase1Test(orderId, testRepository.updateTestCompletionTime(_: String, dateTimeFactory.nowLocalTimeZone)) flatMap { u =>
        require(u.testGroup.activeTests.nonEmpty, s"Active tests cannot be found orderId=$orderId")
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

  def markAsStarted(orderId: String, startedTime: OffsetDateTime = dateTimeFactory.nowLocalTimeZone)
                   (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    updatePhase1Test(orderId, testRepository.updateTestStartTime(_: String, startedTime)) flatMap { u =>
      //TODO: remove the next line and comment in the following line at end of campaign 2019
      testRepository.updateProgressStatus(u.applicationId, ProgressStatuses.PHASE1_TESTS_STARTED) map { _ =>
        //      maybeMarkAsStarted(u.applicationId).map { _ =>
        DataStoreEvents.OnlineExerciseStarted(u.applicationId) :: Nil
      }
    }
  }

  private def maybeMarkAsStarted(appId: String): Future[Unit] = {
    appRepository.getProgressStatusTimestamps(appId).map { timestamps =>
      val hasStarted = timestamps.exists { case (progressStatus, _) => progressStatus == PHASE1_TESTS_STARTED.key }
      if (hasStarted) {
        Future.successful(())
      } else {
        testRepository.updateProgressStatus(appId, PHASE1_TESTS_STARTED)
      }
    }
  }

  //scalastyle:off method.length
  override def storeRealTimeResults(orderId: String, results: PsiRealTimeResults)
                                   (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {

    def insertResults(applicationId: String, orderId: String, testProfile: Phase1TestProfile, results: PsiRealTimeResults): Future[Unit] =
      testRepository.insertTestResult(
        applicationId,
        testProfile.tests.find(_.orderId == orderId).getOrElse(throw CannotFindTestByOrderIdException(s"Test not found for orderId=$orderId")),
        model.persisted.PsiTestResult.fromCommandObject(results)
      )

    def maybeUpdateProgressStatus(appId: String) = {
      testRepository.getTestGroup(appId).flatMap { testProfileOpt =>
        val latestProfile = testProfileOpt.getOrElse(throw new Exception(s"No test profile returned for $appId"))
        if (latestProfile.activeTests.forall(_.testResult.isDefined)) {
          testRepository.updateProgressStatus(appId, ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED).map(_ =>
            audit(s"Progress status updated to ${ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED}", appId))
        } else {
          val msg = s"Did not update progress status to ${ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED} for $appId - " +
            s"not all active tests have a testResult saved"
          logger.warn(msg)
          Future.successful(())
        }
      }
    }

    def markTestAsCompleted(profile: PsiTestProfile): Future[Unit] =
      profile.tests.find(_.orderId == orderId).map { test =>
        if (!test.isCompleted) {
          logger.info(s"Processing real time results - setting completed date on psi test whose orderId=$orderId")
          setTestCompletedTime(orderId)
        }
        else {
          logger.info(s"Processing real time results - completed date is already set on psi test whose orderId=$orderId " +
            s"so will not mark as complete")
          Future.successful(())
        }
      }.getOrElse(throw CannotFindTestByOrderIdException(s"Test not found for orderId=$orderId"))


    (for {
      appIdOpt <- testRepository.getApplicationIdForOrderId(orderId)
    } yield {
      val appId = appIdOpt.getOrElse(throw CannotFindTestByOrderIdException(s"Application not found for test for orderId=$orderId"))
      for {
        profile <- testRepository.getTestProfileByOrderId(orderId)
        _ <- markTestAsCompleted(profile)
        _ <- profile.tests.find(_.orderId == orderId).map { test => insertResults(appId, test.orderId, profile, results) }
          .getOrElse(throw CannotFindTestByOrderIdException(s"Test not found for orderId=$orderId"))
        _ <- maybeUpdateProgressStatus(appId)
      } yield ()
    }).flatMap(identity)
  }
  //scalastyle:on

  private def updatePhase1Test(orderId: String, updatePsiTest: String => Future[Unit]): Future[Phase1TestGroupWithUserIds] = {
    for {
      _ <- updatePsiTest(orderId)
      updated <- testRepository.getTestGroupByOrderId(orderId)
    } yield {
      updated
    }
  }

  private def getTestNamesForApplication(application: OnlineTestApplication) = {
    if (application.guaranteedInterview) {
      val tests = onlineTestsGatewayConfig.phase1Tests.gis
      logger.warn(s"Processing phase1 GIS candidate ${application.applicationId} - inviting to ${tests.size} tests")
      tests
    } else {
      val tests = onlineTestsGatewayConfig.phase1Tests.standard
      logger.warn(s"Processing phase1 standard candidate ${application.applicationId} - inviting to ${tests.size} tests")
      tests
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
