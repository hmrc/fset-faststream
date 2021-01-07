/*
 * Copyright 2021 HM Revenue & Customs
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
import com.google.inject.name.Named
import common.{ FutureEx, Phase1TestConcern2 }
import config.{ MicroserviceAppConfig, PsiTestIds, TestIntegrationGatewayConfig }
import connectors.ExchangeObjects._
import connectors.{ OnlineTestEmailClient, OnlineTestsGatewayClient }
import factories.{ DateTimeFactory, UUIDFactory }
import javax.inject.{ Inject, Singleton }
import model.Exceptions._
import model.OnlineTestCommands._
import model.ProgressStatuses.PHASE1_TESTS_STARTED
import model._
import model.exchange.{ Phase1TestGroupWithNames2, PsiRealTimeResults }
import model.persisted.{ Phase1TestProfile, PsiTestResult => _, TestResult => _, _ }
import model.stc.{ AuditEvents, DataStoreEvents }
import org.joda.time.DateTime
import play.api.Logger
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.onlinetesting.{ Phase1TestRepository, Phase1TestRepository2 }
import services.AuditService
import services.onlinetesting.Exceptions.{ TestCancellationException, TestRegistrationException }
import services.onlinetesting.{ CubiksSanitizer, OnlineTestService }
import services.sift.ApplicationSiftService
import services.stc.StcEventService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{ Failure, Success }

@Singleton
class Phase1TestService2 @Inject() (appConfig: MicroserviceAppConfig,
                                    val appRepository: GeneralApplicationRepository,
                                    val cdRepository: ContactDetailsRepository,
                                    val testRepository: Phase1TestRepository, //Phase1TestRepositoryX must extend trait OnlineTestRepository2
                                    val testRepository2: Phase1TestRepository2,
                                    val onlineTestsGatewayClient: OnlineTestsGatewayClient,
                                    val tokenFactory: UUIDFactory,
                                    val dateTimeFactory: DateTimeFactory,
                                    @Named("CSREmailClient") val emailClient: OnlineTestEmailClient,
                                    val auditService: AuditService,
                                    val siftService: ApplicationSiftService,
                                    val eventService: StcEventService,
                                    val actor: ActorSystem
                                   ) extends OnlineTestService with Phase1TestConcern2 with ResetPhase1Test2 {

  type TestRepository2 = Phase1TestRepository

  val integrationGatewayConfig: TestIntegrationGatewayConfig = appConfig.testIntegrationGatewayConfig

  override def registerAndInvite(applications: List[OnlineTestApplication])
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
      case (testName, delayModifier) =>
        val testIds = testIdsByName(testName)
        val delay = (delayModifier * integrationGatewayConfig.phase1Tests.testRegistrationDelayInSecs).second
        akka.pattern.after(delay, actor.scheduler) {
          Logger.debug(s"Phase1TestService - about to call registerPsiApplicant with testIds - $testIds")
          registerPsiApplicant(application, testIds, invitationDate)
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

  def resetTest(application: OnlineTestApplication, orderIdToReset: String, actionTriggeredBy: String)
               (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {

    val (invitationDate, expirationDate) = calcOnlineTestDates(integrationGatewayConfig.phase1Tests.expiryTimeInDays)

    for {
      // Fetch existing test group that should exist
      maybeTestGroup <- testRepository2.getTestGroup(application.applicationId)
      testGroup = maybeTestGroup
        .getOrElse(throw CannotFindTestGroupByApplicationIdException(s"appId - ${application.applicationId}"))

      // Extract test that requires reset
      testToReset = testGroup.tests.find(_.orderId == orderIdToReset)
        .getOrElse(throw CannotFindTestByOrderIdException(s"OrderId - $orderIdToReset"))
      _ = Logger.info(s"testToReset -- $testToReset")

      // Create PsiIds to use for re-invitation
      psiIds = integrationGatewayConfig.phase1Tests.tests.find {
        case (_, ids) => ids.inventoryId == testToReset.inventoryId
      }.getOrElse(throw CannotFindTestByInventoryIdException(s"InventoryId - ${testToReset.inventoryId}"))._2
      _ = Logger.info(s"psiIds -- $psiIds")

      // Register applicant
      newPsiTest <- registerPsiApplicant(application, psiIds, invitationDate)
      _ = Logger.info(s"newPsiTest -- $newPsiTest")

      // Set old test to inactive
      testsWithInactiveTest = testGroup.tests
        .map { t => if (t.orderId == orderIdToReset) { t.copy(usedForResults = false) } else t }
      _ = Logger.info(s"testsWithInactiveTest -- $testsWithInactiveTest")

      // insert new test and maintain test order
      idxOfResetTest = testGroup.tests.indexWhere(_.orderId == orderIdToReset)
      updatedTests = insertTest(testsWithInactiveTest, idxOfResetTest, newPsiTest)
      _ = Logger.info(s"updatedTests -- $updatedTests")

      _ <- markAsInvited2(application)(Phase1TestProfile2(expirationDate, updatedTests))
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
                                   testIds: PsiTestIds, invitationDate: DateTime)
                                  (implicit hc: HeaderCarrier): Future[PsiTest] = {
    for {
      aoa <- registerApplicant2(application, testIds)
    } yield {
      if (aoa.status != AssessmentOrderAcknowledgement.acknowledgedStatus) {
        val msg = s"Received response status of ${aoa.status} when registering candidate " +
          s"${application.applicationId} to phase1 tests with testIds $testIds"
        Logger.warn(msg)
        throw TestRegistrationException(msg)
      } else {
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

  private def registerApplicant2(application: OnlineTestApplication, testIds: PsiTestIds)(
    implicit hc: HeaderCarrier): Future[AssessmentOrderAcknowledgement] = {

    val orderId = tokenFactory.generateUUID()
    val preferredName = CubiksSanitizer.sanitizeFreeText(application.preferredName)
    val lastName = CubiksSanitizer.sanitizeFreeText(application.lastName)

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
      Logger.debug(s"Response from cancellation for orderId=$orderId is $response")
      if (response.status != AssessmentCancelAcknowledgementResponse.completedStatus) {
        Logger.debug(s"Cancellation failed with errors: ${response.details}")
        throw TestCancellationException(s"appId=$appId, orderId=$orderId")
      } else {
        audit("TestCancelledForCandidate", userId)
        response
      }
    }
  }

  private def testIdsByName(name: String): PsiTestIds = {
    integrationGatewayConfig.phase1Tests.tests
      .getOrElse(name, throw new IllegalArgumentException(s"Incorrect test name: $name"))
  }

  private def buildRedirectionUrl(orderId: String, inventoryId: String) = {
    val scheduleCompletionBaseUrl = s"${integrationGatewayConfig.candidateAppUrl}/fset-fast-stream/online-tests/psi/phase1"
    s"$scheduleCompletionBaseUrl/complete/$orderId"
  }

  private def markAsInvited2(application: OnlineTestApplication)(newOnlineTestProfile: Phase1TestProfile2): Future[Unit] = for {
    currentOnlineTestProfile <- testRepository2.getTestGroup(application.applicationId)
    updatedTestProfile <- insertOrAppendNewTests2(application.applicationId, currentOnlineTestProfile, newOnlineTestProfile)
    _ <- testRepository2.resetTestProfileProgresses(application.applicationId, determineStatusesToRemove2(updatedTestProfile))
  } yield {
    audit("OnlineTestInvited", application.userId)
  }

  // This also handles archiving any existing tests to which the candidate has previously been invited
  // eg if a test is being reset and the candidate is being invited to a replacement test
  private def insertOrAppendNewTests2(applicationId: String, currentProfile: Option[Phase1TestProfile2],
                                      newProfile: Phase1TestProfile2): Future[Phase1TestProfile2] = {

    testRepository2.insertOrUpdateTestGroup(applicationId, newProfile).flatMap { _ =>
      testRepository2.getTestGroup(applicationId).map {
        case Some(testProfile) => testProfile
        case None => throw ApplicationNotFound(applicationId)
      }
    }
  }

  def getTestGroup2(applicationId: String): Future[Option[Phase1TestGroupWithNames2]] = {
    for {
      phase1Opt <- testRepository2.getTestGroup(applicationId)
    } yield {
      phase1Opt.map { testProfile =>
        Phase1TestGroupWithNames2(testProfile.expirationDate, testProfile.activeTests)
      }
    }
  }

  def getTestGroupByOrderId(orderId: String): Future[Phase1TestGroupWithNames2] = {
    for {
      phase1 <- testRepository2.getTestGroupByOrderId(orderId)
    } yield
      Phase1TestGroupWithNames2(
        phase1.testGroup.expirationDate,
        phase1.testGroup.tests
      )
  }

  //TODO: these 2 methods need optimising (just copied across from cubiks impl)
  def markAsCompleted2(orderId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    testRepository2.getTestProfileByOrderId(orderId).flatMap { p =>
      p.tests.find(_.orderId == orderId).map { test => markAsCompleted22(test.orderId) } // TODO: here handle if we do not find the data
        .getOrElse(Future.successful(()))
    }
  }

  def markAsCompleted22(orderId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] =
    eventSink {
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
      //TODO: remove the next line and comment in the following line at end of campaign 2019
      testRepository2.updateProgressStatus(u.applicationId, ProgressStatuses.PHASE1_TESTS_STARTED) map { _ =>
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
        testRepository2.updateProgressStatus(appId, PHASE1_TESTS_STARTED)
      }
    }
  }

  //scalastyle:off method.length
  override def storeRealTimeResults(orderId: String, results: PsiRealTimeResults)
                                   (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {

    def insertResults(applicationId: String, orderId: String, testProfile: Phase1TestProfile2, results: PsiRealTimeResults): Future[Unit] =
      testRepository2.insertTestResult2(
        applicationId,
        testProfile.tests.find(_.orderId == orderId).getOrElse(throw CannotFindTestByOrderIdException(s"Test not found for orderId=$orderId")),
        model.persisted.PsiTestResult.fromCommandObject(results)
      )

    def maybeUpdateProgressStatus(appId: String) = {
      testRepository2.getTestGroup(appId).flatMap { testProfileOpt =>
        val latestProfile = testProfileOpt.getOrElse(throw new Exception(s"No test profile returned for $appId"))
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

    def markTestAsCompleted(profile: PsiTestProfile): Future[Unit] =
      profile.tests.find(_.orderId == orderId).map { test =>
        if (!test.isCompleted) {
          Logger.info(s"Processing real time results - setting completed date on psi test whose orderId=$orderId")
          markAsCompleted22(orderId)
        }
        else {
          Logger.info(s"Processing real time results - completed date is already set on psi test whose orderId=$orderId " +
            s"so will not mark as complete")
          Future.successful(())
        }
      }.getOrElse(throw CannotFindTestByOrderIdException(s"Test not found for orderId=$orderId"))


    (for {
      appIdOpt <- testRepository2.getApplicationIdForOrderId(orderId)
    } yield {
      val appId = appIdOpt.getOrElse(throw CannotFindTestByOrderIdException(s"Application not found for test for orderId=$orderId"))
      for {
        profile <- testRepository2.getTestProfileByOrderId(orderId)
        _ <- markTestAsCompleted(profile)
        _ <- profile.tests.find(_.orderId == orderId).map { test => insertResults(appId, test.orderId, profile, results) }
          .getOrElse(throw CannotFindTestByOrderIdException(s"Test not found for orderId=$orderId"))
        _ <- maybeUpdateProgressStatus(appId)
      } yield ()
    }).flatMap(identity)
  }
  //scalastyle:on

  private def updatePhase1Test2(orderId: String, updatePsiTest: String => Future[Unit]): Future[Phase1TestGroupWithUserIds2] = {
    for {
      _ <- updatePsiTest(orderId)
      updated <- testRepository2.getTestGroupByOrderId(orderId)
    } yield {
      updated
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

  override def nextApplicationsReadyForOnlineTesting(maxBatchSize: Int): Future[List[OnlineTestApplication]] =
    testRepository2.nextApplicationsReadyForOnlineTesting(maxBatchSize)

  override def registerAndInviteForTestGroup(applications: List[OnlineTestApplication])
                                            (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    ??? // This is Cubiks specific so should never be called
  }

  override def registerAndInviteForTestGroup(application: OnlineTestApplication)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    ??? // This is Cubiks specific so should never be called
  }

  override def retrieveTestResult(testProfile: RichTestGroup)(implicit hc: HeaderCarrier): Future[Unit] = {
    ??? // This is Cubiks specific so should never be called
  }
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
