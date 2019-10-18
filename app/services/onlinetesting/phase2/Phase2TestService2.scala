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

package services.onlinetesting.phase2

import _root_.services.AuditService
import akka.actor.ActorSystem
import common.{ FutureEx, Phase2TestConcern2 }
import config.{ Phase2Schedule, Phase2TestsConfig, Phase2TestsConfig2, PsiTestIds, TestIntegrationGatewayConfig }
import connectors.ExchangeObjects._
import connectors.{ AuthProviderClient, OnlineTestsGatewayClient, Phase2OnlineTestEmailClient }
import factories.{ DateTimeFactory, UUIDFactory }
import model.Exceptions._
import model.OnlineTestCommands._
import model.ProgressStatuses._
import model._
import model.command.{ Phase3ProgressResponse, ProgressResponse }
import model.exchange.{ CubiksTestResultReady, Phase2TestGroupWithActiveTest2, PsiRealTimeResults, PsiTestResultReady }
import model.persisted._
import model.stc.StcEventTypes.StcEventType
import model.stc.{ AuditEvent, AuditEvents, DataStoreEvents }
import org.joda.time.DateTime
import play.api.Logger
import play.api.mvc.RequestHeader
import repositories._
import repositories.onlinetesting.{ Phase2TestRepository, Phase2TestRepository2 }
import services.onlinetesting.Exceptions.{ TestCancellationException, TestRegistrationException }
import services.onlinetesting.phase3.Phase3TestService
import services.onlinetesting.{ CubiksSanitizer, OnlineTestService }
import services.sift.ApplicationSiftService
import services.stc.StcEventService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.{ Failure, Success, Try }

object Phase2TestService2 extends Phase2TestService2 {

  import config.MicroserviceAppConfig._

  val appRepository = applicationRepository
  val cdRepository = faststreamContactDetailsRepository
  val testRepository = phase2TestRepository
  val testRepository2 = phase2TestRepository2
  val onlineTestsGatewayClient = OnlineTestsGatewayClient
  val tokenFactory = UUIDFactory
  val dateTimeFactory = DateTimeFactory
  val emailClient = Phase2OnlineTestEmailClient
  val auditService = AuditService
  val actor = ActorSystem()
  val eventService = StcEventService
  val authProvider = AuthProviderClient
  val phase3TestService = Phase3TestService
  val siftService = ApplicationSiftService
  val integrationGatewayConfig = testIntegrationGatewayConfig
}

// scalastyle:off number.of.methods
trait Phase2TestService2 extends OnlineTestService with Phase2TestConcern2 with
  ResetPhase2Test2 {
  type TestRepository = Phase2TestRepository
  val testRepository2: Phase2TestRepository2
  val actor: ActorSystem
  val onlineTestsGatewayClient: OnlineTestsGatewayClient
  val integrationGatewayConfig: TestIntegrationGatewayConfig
  val authProvider: AuthProviderClient
  val phase3TestService: Phase3TestService

  def testConfig2: Phase2TestsConfig2 = integrationGatewayConfig.phase2Tests
  // TODO: Get rid of this
  def testConfig: Phase2TestsConfig = ???

  case class Phase2TestInviteData(application: OnlineTestApplication,
                                  scheduleId: Int,
                                  token: String,
                                  registration: Registration,
                                  invitation: Invitation)

  case class Phase2TestInviteData2(application: OnlineTestApplication, psiTest: PsiTest)


  def getTestGroup(applicationId: String): Future[Option[Phase2TestGroupWithActiveTest2]] = {
    for {
      phase2Opt <- testRepository2.getTestGroup(applicationId)
    } yield phase2Opt.map { phase2 =>
      Phase2TestGroupWithActiveTest2(
        phase2.expirationDate,
        phase2.activeTests,
        resetAllowed = true
      )
    }
  }

  def getTestGroupByOrderId(orderId: String): Future[Option[Phase2TestGroupWithActiveTest2]] = {
    for {
      phase2Opt <- testRepository2.getTestGroupByOrderId(orderId)
    } yield phase2Opt.map { phase2 =>
      Phase2TestGroupWithActiveTest2(
        phase2.expirationDate,
        phase2.activeTests,
        resetAllowed = true
      )
    }
  }

  def verifyAccessCode(email: String, accessCode: String): Future[String] = for {
    userId <- cdRepository.findUserIdByEmail(email)
    testGroupOpt <- testRepository2.getTestGroupByUserId(userId)
    testUrl <- Future.fromTry(processInvigilatedEtrayAccessCode(testGroupOpt, accessCode))
  } yield testUrl

  override def nextApplicationsReadyForOnlineTesting(maxBatchSize: Int): Future[List[OnlineTestApplication]] = {
    testRepository2.nextApplicationsReadyForOnlineTesting(maxBatchSize)
  }

  override def nextTestGroupWithReportReady: Future[Option[Phase2TestGroupWithAppId2]] = {
    testRepository2.nextTestGroupWithReportReady
  }

  override def emailCandidateForExpiringTestReminder(expiringTest: NotificationExpiringOnlineTest,
                                                     emailAddress: String,
                                                     reminder: ReminderNotice)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    emailClient.sendTestExpiringReminder(emailAddress, expiringTest.preferredName,
      reminder.hoursBeforeReminder, reminder.timeUnit, expiringTest.expiryDate).map { _ =>
      audit(s"ReminderPhase2ExpiringOnlineTestNotificationBefore${reminder.hoursBeforeReminder}HoursEmailed",
        expiringTest.userId, Some(emailAddress))
    }
  }

  def resetTest(application: OnlineTestApplication, orderIdToReset: String, actionTriggeredBy: String)
                (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {

    val (invitationDate, expirationDate) = calcOnlineTestDates(integrationGatewayConfig.phase2Tests.expiryTimeInDays)

    for {
      // Fetch existing test group that should exist
      testGroupOpt <- testRepository2.getTestGroup(application.applicationId)
      testGroup = testGroupOpt
        .getOrElse(throw CannotFindTestGroupByApplicationIdException(s"appId - ${application.applicationId}"))

      // Extract test that requires reset
      testToReset = testGroup.tests.find(_.orderId == orderIdToReset)
        .getOrElse(throw CannotFindTestByOrderIdException(s"OrderId - $orderIdToReset"))
      _ = Logger.info(s"testToReset -- $testToReset")

      // Create PsiIds to use for re-invitation
      psiIds = integrationGatewayConfig.phase2Tests.tests.find {
        case (_, ids) => ids.inventoryId == testToReset.inventoryId
      }.getOrElse(throw CannotFindTestByInventoryIdException(s"InventoryId - ${testToReset.inventoryId}"))._2
      _ = Logger.info(s"psiIds -- $psiIds")

      // Register applicant
      testInvite <- registerPsiApplicant(application, psiIds, invitationDate)
      newPsiTest = testInvite.psiTest
      _ = Logger.info(s"newPsiTest -- $newPsiTest")

      // Set old test to inactive
      testsWithInactiveTest = testGroup.tests
        .map { t => if (t.orderId == orderIdToReset) { t.copy(usedForResults = false) } else t }
      _ = Logger.info(s"testsWithInactiveTest -- $testsWithInactiveTest")

      // insert new test and maintain test order
      idxOfResetTest = testGroup.tests.indexWhere(_.orderId == orderIdToReset)
      updatedTests = insertTest(testsWithInactiveTest, idxOfResetTest, newPsiTest)
      _ = Logger.info(s"updatedTests -- $updatedTests")

      _ <- insertOrUpdateTestGroup(application)(testGroup.copy(expirationDate = expirationDate, tests = updatedTests))
      _ <- emailInviteToApplicant(application)(hc, rh, invitationDate, expirationDate)

    } yield {

    }
  }

  private def insertTest(ls: List[PsiTest], i: Int, value: PsiTest): List[PsiTest] = {
    val (front, back) = ls.splitAt(i)
    front ++ List(value) ++ back
  }

  private def cancelPsiTest(appId: String,
                            userId: String,
                            orderId: String): Future[AssessmentCancelAcknowledgementResponse] = {
    val req = CancelCandidateTestRequest(orderId)
    onlineTestsGatewayClient.psiCancelTest(req).map { response =>
      Logger.debug(s"Response from cancellation for orderId=$orderId")
      if (response.status != AssessmentCancelAcknowledgementResponse.completedStatus) {
        Logger.debug(s"Cancellation failed with errors: ${response.details}")
        throw TestCancellationException(s"appId=$appId, orderId=$orderId")
      } else {
        audit("TestCancelledForCandidate", userId)
        response
      }
    }
  }

  private def inPhase3TestsInvited(applicationId: String): Future[Boolean] = {
    for {
      progressResponse <- appRepository.findProgress(applicationId)
    } yield {
      progressResponse.phase3ProgressResponse match {
        case response: Phase3ProgressResponse if response.phase3TestsInvited => true
        case _ => false
      }
    }
  }

  override def registerAndInviteForTestGroup(application: OnlineTestApplication)
                                            (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    registerAndInviteForTestGroup(List(application))
  }

  override def registerAndInvite(applications: List[OnlineTestApplication])
                                (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    registerAndInviteForTestGroup(applications)
  }

  override def processNextExpiredTest(expiryTest: TestExpirationEvent)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    testRepository2.nextExpiringApplication(expiryTest).flatMap {
      case Some(expired) => processExpiredTest(expired, expiryTest)
      case None => Future.successful(())
    }
  }


  override def registerAndInviteForTestGroup(applications: List[OnlineTestApplication])
                                            (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val firstApplication = applications.head
    val applicationsWithTheSameType = applications filter (_.isInvigilatedETray == firstApplication.isInvigilatedETray)

    val tests = integrationGatewayConfig.phase2Tests.tests
    val standardTests = integrationGatewayConfig.phase2Tests.standard // use this for the order of tests

    FutureEx.traverseSerial(applicationsWithTheSameType) { application =>
      FutureEx.traverseSerial(standardTests) { testName =>
        val testIds = tests.getOrElse(testName, throw new Exception(s"Unable to find inventoryId for $testName"))
        registerAndInviteForTestGroup2(application, testIds).map(_ => ())
      }
    }.map(_ => ())
  }

  private def processInvigilatedEtrayAccessCode(phase: Option[Phase2TestGroup2], accessCode: String): Try[String] = {
    phase.fold[Try[String]](Failure(new NotFoundException(Some("No Phase2TestGroup found")))){
      phase2TestGroup => {

        val psiTestOpt = phase2TestGroup.activeTests.find( _.invigilatedAccessCode == Option(accessCode) )

        psiTestOpt.map { psiTest =>
          if(phase2TestGroup.expirationDate.isBefore(dateTimeFactory.nowLocalTimeZone)) {
            Failure(ExpiredTestForTokenException("Phase 2 test expired for invigilated access code"))
          } else {
            Success(psiTest.testUrl)
          }

        }.getOrElse(Failure(InvalidTokenException("Invigilated access code not found")))
      }
    }
  }

  private def registerAndInviteForTestGroup2(application: OnlineTestApplication,
                                             testIds: PsiTestIds,
                                             expiresDate: Option[DateTime] = None)
                                           (implicit hc: HeaderCarrier, rh: RequestHeader): Future[OnlineTestApplication] = {
    //TODO: Do we need to worry about this for PSI?
    //    require(applications.map(_.isInvigilatedETray).distinct.size <= 1, "the batch can have only one type of invigilated e-tray")

    val isInvigilatedETray = application.isInvigilatedETray
    val expiryTimeInDays = if (isInvigilatedETray) {
      integrationGatewayConfig.phase2Tests.expiryTimeInDaysForInvigilatedETray
    } else {
      integrationGatewayConfig.phase2Tests.expiryTimeInDays
    }

    implicit val (invitationDate, expirationDate) = expiresDate match {
      case Some(expDate) => (dateTimeFactory.nowLocalTimeZone, expDate)
      case _ => calcOnlineTestDates(expiryTimeInDays)
    }

    def maybeEmailCandidate(cond: Boolean)
                           (emailFunc: OnlineTestApplication => Future[Unit],
                            application: OnlineTestApplication): Future[Unit] = {
      if(cond) {
        Future.successful(())
      } else {
        emailFunc(application)
      }
    }

    for {
      registeredApplicant <- registerPsiApplicant(application, testIds, invitationDate)
      testGroupOpt <- getTestGroup(application.applicationId)
      _ <- insertPhase2TestGroups(registeredApplicant)(invitationDate, expirationDate, hc)
      testExist = testGroupOpt.exists(_.activeTests.nonEmpty)
      emailFunc = emailInviteToApplicant(_: OnlineTestApplication)(hc, rh, invitationDate, expirationDate)
      _ <- maybeEmailCandidate(testExist)(emailFunc, application)
    } yield {
      application
    }
  }

  private def registerAndInviteForTestGroup(applications: List[OnlineTestApplication],
                                            testIds: PsiTestIds,
                                            expiresDate: Option[DateTime] = None)
                                           (implicit hc: HeaderCarrier, rh: RequestHeader): Future[List[OnlineTestApplication]] = {
    //TODO: Do we need to worry about this for PSI?
//    require(applications.map(_.isInvigilatedETray).distinct.size <= 1, "the batch can have only one type of invigilated e-tray")

    applications match {
      case Nil => Future.successful(Nil)
      case testApplications =>
        val isInvigilatedETrayBatch = applications.head.isInvigilatedETray
        val expiryTimeInDays = if (isInvigilatedETrayBatch) {
          integrationGatewayConfig.phase2Tests.expiryTimeInDaysForInvigilatedETray
        } else {
          integrationGatewayConfig.phase2Tests.expiryTimeInDays
        }

        implicit val (invitationDate, expirationDate) = expiresDate match {
          case Some(expDate) => (dateTimeFactory.nowLocalTimeZone, expDate)
          case _ => calcOnlineTestDates(expiryTimeInDays)
        }

        for {
          registeredApplicants <- registerPsiApplicants(testApplications, testIds, invitationDate)
          _ <- insertPhase2TestGroups(registeredApplicants)(invitationDate, expirationDate, hc)
          _ <- emailInviteToApplicants(testApplications)(hc, rh, invitationDate, expirationDate)
        } yield {
          testApplications
        }
    }
  }

  def registerPsiApplicants(applications: List[OnlineTestApplication],
                            testIds: PsiTestIds, invitationDate: DateTime)
                           (implicit hc: HeaderCarrier): Future[List[Phase2TestInviteData2]] = {
    Future.sequence(
      applications.map { application =>
        registerPsiApplicant(application, testIds, invitationDate)
    })
  }

  private def registerPsiApplicant(application: OnlineTestApplication,
                                   testIds: PsiTestIds,
                                   invitationDate: DateTime)
                                  (implicit hc: HeaderCarrier): Future[Phase2TestInviteData2] = {
    registerApplicant2(application, testIds).map { aoa =>
      if (aoa.status != AssessmentOrderAcknowledgement.acknowledgedStatus) {
        val msg = s"Received response status of ${aoa.status} when registering candidate " +
          s"${application.applicationId} to phase1 tests with Ids=$testIds"
        Logger.warn(msg)
        throw TestRegistrationException(msg)
      } else {
        val psiTest = PsiTest(
          inventoryId = testIds.inventoryId,
          orderId = aoa.orderId,
          usedForResults = true,
          testUrl = aoa.testLaunchUrl,
          invitationDate = invitationDate,
          assessmentId = testIds.assessmentId,
          reportId = testIds.reportId,
          normId = testIds.normId
        )
        Phase2TestInviteData2(application, psiTest)
      }
    }
  }

  private def registerApplicant2(application: OnlineTestApplication, testIds: PsiTestIds)
                                (implicit hc: HeaderCarrier): Future[AssessmentOrderAcknowledgement] = {

    val orderId = tokenFactory.generateUUID()
    val preferredName = CubiksSanitizer.sanitizeFreeText(application.preferredName)
    val lastName = CubiksSanitizer.sanitizeFreeText(application.lastName)

    val maybePercentage = application.eTrayAdjustments.flatMap(_.percentage)

    val registerCandidateRequest = RegisterCandidateRequest(
      inventoryId = testIds.inventoryId, // Read from config to identify the test we are registering for
      orderId = orderId, // Identifier we generate to uniquely identify the test
      accountId = application.testAccountId, // Candidate's account across all tests
      preferredName = preferredName,
      lastName = lastName,
      // The url psi will redirect to when the candidate completes the test
      redirectionUrl = buildRedirectionUrl(orderId, testIds.inventoryId),
      adjustment = maybePercentage.map(TestAdjustment.apply),
      assessmentId = testIds.assessmentId,
      reportId = testIds.reportId,
      normId = testIds.normId
    )

    onlineTestsGatewayClient.psiRegisterApplicant(registerCandidateRequest).map { response =>
      audit("UserRegisteredForPhase2Test", application.userId)
      response
    }
  }

  private def buildRedirectionUrl(orderId: String, inventoryId: String) = {
    val appUrl = integrationGatewayConfig.candidateAppUrl
    val scheduleCompletionBaseUrl = s"$appUrl/fset-fast-stream/online-tests/psi/phase2"
    s"$scheduleCompletionBaseUrl/complete/$orderId"
  }


  def buildInviteApplication(application: OnlineTestApplication, token: String,
                             userId: Int, schedule: Phase2Schedule): InviteApplicant = {
    val scheduleCompletionBaseUrl = s"${integrationGatewayConfig.candidateAppUrl}/fset-fast-stream/online-tests/phase2"

    InviteApplicant(schedule.scheduleId,
      userId,
      s"$scheduleCompletionBaseUrl/complete/$token",
      resultsURL = None,
      timeAdjustments = buildTimeAdjustments(schedule.assessmentId, application)
    )
  }

  private def insertPhase2TestGroups(o: List[Phase2TestInviteData2])
                                    (implicit invitationDate: DateTime,
                                     expirationDate: DateTime, hc: HeaderCarrier): Future[Unit] = {
    Future.sequence(o.map { completedInvite =>
      val maybeInvigilatedAccessCodeFut = if (completedInvite.application.isInvigilatedETray) {
        authProvider.generateAccessCode.map(ac => Some(ac.token))
      } else {
        Future.successful(None)
      }

      for {
        maybeInvigilatedAccessCode <- maybeInvigilatedAccessCodeFut
        testWithAccessCode = completedInvite.psiTest.copy(invigilatedAccessCode = maybeInvigilatedAccessCode)
        newTestGroup = Phase2TestGroup2(expirationDate = expirationDate, List(testWithAccessCode))
        _ <- insertOrUpdateTestGroup(completedInvite.application)(newTestGroup)
      } yield {}
    }).map(_ => ())
  }

  private def insertPhase2TestGroups(completedInvite: Phase2TestInviteData2)
                                    (implicit invitationDate: DateTime,
                                     expirationDate: DateTime, hc: HeaderCarrier): Future[Unit] = {

    val maybeInvigilatedAccessCodeFut = if (completedInvite.application.isInvigilatedETray) {
      authProvider.generateAccessCode.map(ac => Some(ac.token))
    } else {
      Future.successful(None)
    }
    val appId = completedInvite.application.applicationId

    for {
      maybeInvigilatedAccessCode <- maybeInvigilatedAccessCodeFut
      currentTestGroupOpt <- testRepository2.getTestGroup(appId)
      existingTests = currentTestGroupOpt.map(_.tests).getOrElse(Nil)
      testWithAccessCode = completedInvite.psiTest.copy(invigilatedAccessCode = maybeInvigilatedAccessCode)
      newTestGroup = Phase2TestGroup2(expirationDate = expirationDate, existingTests :+ testWithAccessCode)
      _ <- insertOrUpdateTestGroup(completedInvite.application)(newTestGroup)
    } yield {}
  }

  private def insertOrUpdateTestGroup(application: OnlineTestApplication)
                                     (newOnlineTestProfile: Phase2TestGroup2): Future[Unit] = for {
    currentOnlineTestProfile <- testRepository2.getTestGroup(application.applicationId)
    updatedTestProfile <- insertOrAppendNewTests(application.applicationId, currentOnlineTestProfile, newOnlineTestProfile)
    _ <- testRepository2.resetTestProfileProgresses(application.applicationId, determineStatusesToRemove(updatedTestProfile))
  } yield ()

  private def insertOrAppendNewTests(applicationId: String,
                                     currentProfile: Option[Phase2TestGroup2],
                                     newProfile: Phase2TestGroup2): Future[Phase2TestGroup2] = {

    val insertFut = testRepository2.insertOrUpdateTestGroup(applicationId, newProfile)

    insertFut.flatMap { _ =>
      testRepository2.getTestGroup(applicationId).map {
        case Some(testProfile) => testProfile
        case None => throw ApplicationNotFound(applicationId)
      }
    }
  }

  def markAsStarted2(orderId: String, startedTime: DateTime = dateTimeFactory.nowLocalTimeZone)
                   (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    updatePhase2Test2(orderId, testRepository2.updateTestStartTime(_: String, startedTime)).flatMap { u =>
      maybeMarkAsStarted(u.applicationId).map { _ =>
        DataStoreEvents.ETrayStarted(u.applicationId) :: Nil
      }
    }
  }

  private def maybeMarkAsStarted(appId: String): Future[Unit] = {
    appRepository.getProgressStatusTimestamps(appId).map { timestamps =>
      val hasStarted = timestamps.exists { case (progressStatus, _) => progressStatus == PHASE2_TESTS_STARTED.key }
      if (hasStarted) {
        Future.successful(())
      } else {
        testRepository2.updateProgressStatus(appId, PHASE2_TESTS_STARTED)
      }
    }
  }

  def markAsCompleted2(orderId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    val updateTestFunc = testRepository2.updateTestCompletionTime2(_: String, dateTimeFactory.nowLocalTimeZone)
    updatePhase2Test2(orderId, updateTestFunc).flatMap { u =>
      val msg = s"Active tests cannot be found when marking phase2 test complete for orderId: $orderId"
      require(u.testGroup.activeTests.nonEmpty, msg)
      val activeTestsCompleted = u.testGroup.activeTests forall (_.completedDateTime.isDefined)
      if (activeTestsCompleted) {
        testRepository2.updateProgressStatus(u.applicationId, ProgressStatuses.PHASE2_TESTS_COMPLETED) map { _ =>
          DataStoreEvents.ETrayCompleted(u.applicationId) :: Nil
        }
      } else {
        Future.successful(List.empty[StcEventType])
      }
    }
  }

  def markAsReportReadyToDownload2(orderId: String, reportReady: PsiTestResultReady): Future[Unit] = {
    updatePhase2Test2(orderId, testRepository2.updateTestReportReady2(_: String, reportReady)).flatMap { updated =>
      if (updated.testGroup.activeTests forall (_.resultsReadyToDownload)) {
        testRepository2.updateProgressStatus(updated.applicationId, ProgressStatuses.PHASE2_TESTS_RESULTS_READY)
      } else {
        Future.successful(())
      }
    }
  }

  private def updatePhase2Test(cubiksUserId: Int, updateCubiksTest: Int => Future[Unit]): Future[Phase2TestGroupWithAppId] = {
    for {
      _ <- updateCubiksTest(cubiksUserId)
      updated <- testRepository.getTestProfileByCubiksId(cubiksUserId)
    } yield {
      updated
    }
  }

  private def updatePhase2Test2(orderId: String,
                               updatePsiTest: String => Future[Unit]): Future[Phase2TestGroupWithAppId2] = {
    updatePsiTest(orderId).flatMap { _ =>
      testRepository2.getTestProfileByOrderId(orderId).map(testGroup => testGroup)
    }
  }

  //scalastyle:off method.length
  override def storeRealTimeResults(orderId: String, results: PsiRealTimeResults)
                                   (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {

    def insertResults(applicationId: String, orderId: String, testProfile: Phase2TestGroupWithAppId2,
                      results: PsiRealTimeResults): Future[Unit] =
      testRepository2.insertTestResult2(
        applicationId,
        testProfile.testGroup.tests.find(_.orderId == orderId)
          .getOrElse(throw CannotFindTestByOrderIdException(s"Test not found for orderId=$orderId")),
        model.persisted.PsiTestResult.fromCommandObject(results)
      )

    def maybeUpdateProgressStatus(appId: String) = {
      testRepository2.getTestGroup(appId).flatMap { testProfileOpt =>
        val latestProfile = testProfileOpt.getOrElse(throw new Exception(s"No test profile returned for $appId"))
        if (latestProfile.activeTests.forall(_.testResult.isDefined)) {
          testRepository2.updateProgressStatus(appId, ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED).map(_ =>
            audit(s"ProgressStatusSet${ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED}", appId))
        } else {
          val msg = s"Did not update progress status to ${ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED} for $appId - " +
            s"not all active tests have a testResult saved"
          Logger.warn(msg)
          Future.successful(())
        }
      }
    }

    def markTestAsCompleted(profile: Phase2TestGroupWithAppId2): Future[Unit] = {
      profile.testGroup.tests.find(_.orderId == orderId).map { test =>
        if (!test.isCompleted) {
          Logger.info(s"Processing real time results - setting completed date on psi test whose orderId=$orderId")
          markAsCompleted2(orderId)
        }
        else {
          Logger.info(s"Processing real time results - completed date is already set on psi test whose orderId=$orderId")
          Future.successful(())
        }
      }.getOrElse(throw CannotFindTestByOrderIdException(s"Test not found for orderId=$orderId"))
    }

    (for {
      appIdOpt <- testRepository2.getApplicationIdForOrderId(orderId, "PHASE2")
    } yield {
      val appId = appIdOpt.getOrElse(throw CannotFindTestByOrderIdException(s"Application not found for test for orderId=$orderId"))
      for {
        profile <- testRepository2.getTestProfileByOrderId(orderId)
        _ <- markTestAsCompleted(profile)
        _ <- profile.testGroup.tests.find(_.orderId == orderId).map { test => insertResults(appId, test.orderId, profile, results) }
          .getOrElse(throw CannotFindTestByOrderIdException(s"Test not found for orderId=$orderId"))
        _ <- maybeUpdateProgressStatus(appId)
      } yield ()
    }).flatMap(identity)
  }
  //scalastyle:on

  def buildTimeAdjustments(assessmentId: Int, application: OnlineTestApplication): List[TimeAdjustments] = {
    application.eTrayAdjustments.flatMap(_.timeNeeded).map { _ =>
      List(TimeAdjustments(assessmentId, sectionId = 1, absoluteTime = calculateAbsoluteTimeWithAdjustments(application)))
    }.getOrElse(Nil)
  }

  def emailInviteToApplicants(candidates: List[OnlineTestApplication])
                             (implicit hc: HeaderCarrier, rh: RequestHeader,
                              invitationDate: DateTime, expirationDate: DateTime): Future[Unit] = {
    Future.sequence(candidates.map { candidate =>
      emailInviteToApplicant(candidate)(hc, rh, invitationDate, expirationDate)
    }).map(_ => ())
  }

  private def emailInviteToApplicant(candidate: OnlineTestApplication)
                                    (implicit hc: HeaderCarrier,
                                     rh: RequestHeader,
                                     invitationDate: DateTime,
                                     expirationDate: DateTime): Future[Unit] = {
    if (candidate.isInvigilatedETray) {
      Future.successful(())
    } else {
      candidateEmailAddress(candidate.userId).flatMap(emailInviteToApplicant(candidate, _ , invitationDate, expirationDate))
    }
  }

  def extendTestGroupExpiryTime(applicationId: String, extraDays: Int, actionTriggeredBy: String)
                               (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    val progressFut = appRepository.findProgress(applicationId)
    val phase2TestGroup = testRepository2.getTestGroup(applicationId)
      .map(tg => tg.getOrElse(throw new IllegalStateException("Expiration date for Phase 2 cannot be extended. Test group not found.")))

    for {
      progress <- progressFut
      phase2 <- phase2TestGroup
      isAlreadyExpired = progress.phase2ProgressResponse.phase2TestsExpired
      extendDays = extendTime(isAlreadyExpired, phase2.expirationDate)
      newExpiryDate = extendDays(extraDays)
      _ <- testRepository.updateGroupExpiryTime(applicationId, newExpiryDate, testRepository.phaseName)
      _ <- progressStatusesToRemoveWhenExtendTime(newExpiryDate, phase2, progress)
        .fold(Future.successful(()))(p => appRepository.removeProgressStatuses(applicationId, p))
    } yield {
      audit(isAlreadyExpired, applicationId) ::
        DataStoreEvents.ETrayExtended(applicationId, actionTriggeredBy) ::
        Nil
    }
  }

  private def progressStatusesToRemoveWhenExtendTime(extendedExpiryDate: DateTime,
                                                     profile: Phase2TestGroup2,
                                                     progress: ProgressResponse): Option[List[ProgressStatus]] = {
    val shouldRemoveExpired = progress.phase2ProgressResponse.phase2TestsExpired
    val today = dateTimeFactory.nowLocalTimeZone
    val shouldRemoveSecondReminder = extendedExpiryDate.minusHours(Phase2SecondReminder.hoursBeforeReminder).isAfter(today)
    val shouldRemoveFirstReminder = extendedExpiryDate.minusHours(Phase2FirstReminder.hoursBeforeReminder).isAfter(today)

    val progressStatusesToRemove = (Set.empty[ProgressStatus]
      ++ (if (shouldRemoveExpired) Set(PHASE2_TESTS_EXPIRED) else Set.empty)
      ++ (if (shouldRemoveSecondReminder) Set(PHASE2_TESTS_SECOND_REMINDER) else Set.empty)
      ++ (if (shouldRemoveFirstReminder) Set(PHASE2_TESTS_FIRST_REMINDER) else Set.empty)).toList

    if (progressStatusesToRemove.isEmpty) {
      None
    } else {
      Some(progressStatusesToRemove)
    }
  }

  private def audit(isAlreadyExpired: Boolean, applicationId: String): AuditEvent = {
    val details = Map("applicationId" -> applicationId)
    if (isAlreadyExpired) {
      AuditEvents.ExpiredTestsExtended(details)
    } else {
      AuditEvents.NonExpiredTestsExtended(details)
    }
  }

  protected[onlinetesting] def calculateAbsoluteTimeWithAdjustments(application: OnlineTestApplication): Int = {
    val baseEtrayTestDurationInMinutes = 80
    (application.eTrayAdjustments.flatMap { etrayAdjustments => etrayAdjustments.timeNeeded }.getOrElse(0)
      * baseEtrayTestDurationInMinutes / 100) + baseEtrayTestDurationInMinutes
  }

  // TODO this method is exactly the same as the Phase1 version (with the exception of the progress status)
  // It's a bit fiddly to extract up to the OnlineTestService/Repository traits without defining another common
  // CubiksTestService/Repository layer as it will be different for Launchapd.
  // Still feels wrong to leave it here when it's 99% the same as phase1.
  def retrieveTestResult(testProfile: RichTestGroup)(implicit hc: HeaderCarrier): Future[Unit] = {
/*
    def insertTests(testResults: List[(OnlineTestCommands.PsiTestResult, U)]): Future[Unit] = {
      Future.sequence(testResults.map {
        case (result, phaseTest) => testRepository2.insertTestResult2(
          testProfile.applicationId,
          phaseTest, model.persisted.PsiTestResult.fromCommandObject(result)
        )
      }).map(_ => ())
    }

    def maybeUpdateProgressStatus(appId: String) = {
      testRepository2.getTestGroup(appId).flatMap { eventualProfile =>

        val latestProfile = eventualProfile.getOrElse(throw new Exception(s"No profile returned for $appId"))
        if (latestProfile.activeTests.forall(_.testResult.isDefined)) {
          testRepository.updateProgressStatus(appId, ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED).map(_ =>
            audit(s"ProgressStatusSet${ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED}", appId))
        } else {
          Future.successful(())
        }
      }
    }

    val testResults = Future.sequence(testProfile.testGroup.activeTests.flatMap { test =>
      test.reportId.map { reportId =>
        onlineTestsGatewayClient.downloadPsiTestResults(reportId)
      }.map(_.map(_ -> test))
    })

    for {
      eventualTestResults <- testResults
      _ <- insertTests(eventualTestResults)
      _ <- maybeUpdateProgressStatus(testProfile.applicationId)
    } yield {
      eventualTestResults.foreach { _ =>
        audit(s"ResultsRetrievedForSchedule", testProfile.applicationId)
      }
    }
*/
    Future.successful(())
  }
}

trait ResetPhase2Test2 {

  import ProgressStatuses._

  def determineStatusesToRemove(testGroup: Phase2TestGroup2): List[ProgressStatus] = {
    (if (testGroup.hasNotStartedYet) List(PHASE2_TESTS_STARTED) else List()) ++
      (if (testGroup.hasNotCompletedYet) List(PHASE2_TESTS_COMPLETED) else List()) ++
      (if (testGroup.hasNotResultReadyToDownloadForAllTestsYet) List(PHASE2_TESTS_RESULTS_RECEIVED, PHASE2_TESTS_RESULTS_READY) else List()) ++
      List(PHASE2_TESTS_FAILED, PHASE2_TESTS_EXPIRED, PHASE2_TESTS_PASSED, PHASE2_TESTS_FAILED_NOTIFIED, PHASE2_TESTS_FAILED_SDIP_AMBER)
  }
}

//scalastyle:on number.of.methods
