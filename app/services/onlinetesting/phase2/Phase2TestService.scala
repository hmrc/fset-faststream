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

package services.onlinetesting.phase2

import services.AuditService
import akka.actor.ActorSystem
import com.google.inject.name.Named
import common.{FutureEx, Phase2TestConcern}
import config._
import connectors.ExchangeObjects._
import connectors.{AuthProviderClient, OnlineTestEmailClient, OnlineTestsGatewayClient}
import factories.{DateTimeFactory, UUIDFactory}

import javax.inject.{Inject, Singleton}
import model.Exceptions._
import model.OnlineTestCommands._
import model.ProgressStatuses._
import model._
import model.command.{Phase3ProgressResponse, ProgressResponse}
import model.exchange.{Phase2TestGroupWithActiveTest, PsiRealTimeResults, PsiTestResultReady}
import model.persisted._
import model.stc.StcEventTypes.StcEventType
import model.stc.{AuditEvent, AuditEvents, DataStoreEvents}
import play.api.Logging
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.onlinetesting.Phase2TestRepository
import services.onlinetesting.Exceptions.{TestCancellationException, TestRegistrationException}
import services.onlinetesting.phase3.Phase3TestService
import services.onlinetesting.{OnlineTestService, TextSanitizer}
import services.sift.ApplicationSiftService
import services.stc.StcEventService
import uk.gov.hmrc.http.HeaderCarrier

import java.time.OffsetDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

// scalastyle:off number.of.methods
@Singleton
class Phase2TestService @Inject() (val appRepository: GeneralApplicationRepository,
                                   val cdRepository: ContactDetailsRepository,
                                   val testRepository: Phase2TestRepository,
                                   val onlineTestsGatewayClient: OnlineTestsGatewayClient,
                                   val tokenFactory: UUIDFactory,
                                   val dateTimeFactory: DateTimeFactory,
                                   @Named("Phase2OnlineTestEmailClient") val emailClient: OnlineTestEmailClient,
                                   val auditService: AuditService,
                                   authProvider: AuthProviderClient,
                                   phase3TestService: Phase3TestService,
                                   val siftService: ApplicationSiftService,
                                   appConfig: MicroserviceAppConfig,
                                   val eventService: StcEventService,
                                   actor: ActorSystem
                                  )(
  implicit ec: ExecutionContext) extends OnlineTestService with Phase2TestConcern with ResetPhase2Test with Logging {
  type TestRepository = Phase2TestRepository

  val onlineTestsGatewayConfig = appConfig.onlineTestsGatewayConfig

  def testConfig: Phase2TestsConfig = onlineTestsGatewayConfig.phase2Tests

  case class Phase2TestInviteData(application: OnlineTestApplication, psiTest: PsiTest)

  def getTestGroup(applicationId: String): Future[Option[Phase2TestGroupWithActiveTest]] = {
    for {
      phase2Opt <- testRepository.getTestGroup(applicationId)
    } yield phase2Opt.map { phase2 =>
      Phase2TestGroupWithActiveTest(
        phase2.expirationDate,
        phase2.activeTests.map(_.toExchange),
        resetAllowed = true
      )
    }
  }

  def getTestGroupByOrderId(orderId: String): Future[Option[Phase2TestGroupWithActiveTest]] = {
    for {
      phase2Opt <- testRepository.getTestGroupByOrderId(orderId)
    } yield phase2Opt.map { phase2 =>
      Phase2TestGroupWithActiveTest(
        phase2.expirationDate,
        phase2.activeTests.map(_.toExchange),
        resetAllowed = true
      )
    }
  }

  def verifyAccessCode(email: String, accessCode: String): Future[String] = for {
    userId <- cdRepository.findUserIdByEmail(email)
    testGroupOpt <- testRepository.getTestGroupByUserId(userId)
    testUrl <- Future.fromTry(processInvigilatedEtrayAccessCode(testGroupOpt, accessCode))
  } yield testUrl

  override def nextApplicationsReadyForOnlineTesting(maxBatchSize: Int): Future[Seq[OnlineTestApplication]] = {
    testRepository.nextApplicationsReadyForOnlineTesting(maxBatchSize)
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

    val (invitationDate, expirationDate) = calcOnlineTestDates(onlineTestsGatewayConfig.phase2Tests.expiryTimeInDays)

    for {
      // Fetch existing test group that should exist
      testGroupOpt <- testRepository.getTestGroup(application.applicationId)
      testGroup = testGroupOpt
        .getOrElse(throw CannotFindTestGroupByApplicationIdException(s"appId - ${application.applicationId}"))

      // Extract test that requires reset
      testToReset = testGroup.tests.find(_.orderId == orderIdToReset)
        .getOrElse(throw CannotFindTestByOrderIdException(s"OrderId - $orderIdToReset"))
      _ = logger.info(s"testToReset -- $testToReset")

      // Create PsiIds to use for re-invitation
      psiIds = onlineTestsGatewayConfig.phase2Tests.tests.find {
        case (_, ids) => ids.inventoryId == testToReset.inventoryId
      }.getOrElse(throw CannotFindTestByInventoryIdException(s"InventoryId - ${testToReset.inventoryId}"))._2
      _ = logger.info(s"psiIds -- $psiIds")

      // Register applicant
      testInvite <- registerPsiApplicant(application, psiIds, invitationDate)
      newPsiTest = testInvite.psiTest
      _ = logger.info(s"newPsiTest -- $newPsiTest")

      // Set old test to inactive
      testsWithInactiveTest = testGroup.tests
        .map { t => if (t.orderId == orderIdToReset) { t.copy(usedForResults = false) } else t }
      _ = logger.info(s"testsWithInactiveTest -- $testsWithInactiveTest")

      // insert new test and maintain test order
      idxOfResetTest = testGroup.tests.indexWhere(_.orderId == orderIdToReset)
      updatedTests = insertTest(testsWithInactiveTest, idxOfResetTest, newPsiTest)
      _ = logger.info(s"updatedTests -- $updatedTests")

      _ <- insertOrUpdateTestGroup(application)(testGroup.copy(expirationDate = expirationDate, tests = updatedTests))
      _ <- emailInviteToApplicant(application)(hc, rh, invitationDate, expirationDate)

    } yield ()
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
      logger.debug(s"Response from cancellation for orderId=$orderId")
      if (response.status != AssessmentCancelAcknowledgementResponse.completedStatus) {
        logger.debug(s"Cancellation failed with errors: ${response.details}")
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
    registerAndInviteForTestGroup(Seq(application))
  }

  override def processNextExpiredTest(expiryTest: TestExpirationEvent)(
    implicit hc: HeaderCarrier, rh: RequestHeader, ec: ExecutionContext): Future[Unit] = {
    testRepository.nextExpiringApplication(expiryTest).flatMap {
      case Some(expired) =>
        logger.warn(s"Expiring candidates for PHASE2 - expiring candidate ${expired.applicationId}")
        processExpiredTest(expired, expiryTest)
      case None =>
        logger.warn(s"Expiring candidates for PHASE2 - none found")
        Future.successful(())
    }
  }

  override def registerAndInviteForTestGroup(applications: Seq[OnlineTestApplication])
                                            (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val firstApplication = applications.head
    val applicationsWithTheSameType = applications filter (_.isInvigilatedETray == firstApplication.isInvigilatedETray)

    val standardTests = onlineTestsGatewayConfig.phase2Tests.standard // use this for the order of tests

    FutureEx.traverseSerial(applicationsWithTheSameType) { application =>
      registerCandidateForTests(application, standardTests).recover {
        case e: Exception =>
          logger.error(s"Error occurred registering candidate ${application.applicationId} with phase 2 tests - ${e.getMessage}")
      }
    }.map { _ => () }
  }

  // Register a single candidate with all the phase 2 tests.
  private def registerCandidateForTests(application: OnlineTestApplication, testNames: List[String])(
    implicit hc: HeaderCarrier, request: RequestHeader) = {

    val tests = onlineTestsGatewayConfig.phase2Tests.tests
    logger.warn(s"Processing phase2 candidate ${application.applicationId} - inviting to ${tests.size} tests")

    // Register the 2 tests with a delay between each test
    val candidateRegistrations = FutureEx.traverseToTry(testNames.zipWithIndex) {
      case (testName, delayModifier) =>
        val testIds = tests.getOrElse(testName, throw new Exception(s"Unable to find test ids when registering phase 2 candidate for $testName"))
        val delay = (delayModifier * onlineTestsGatewayConfig.phase2Tests.testRegistrationDelayInSecs).second
        akka.pattern.after(delay, actor.scheduler) {
          logger.debug(s"Phase2TestService - about to call registerPsiApplicant for application=$application with testIds=$testIds")
          registerAndInviteForTestGroup(application, testIds).map(_ => ())
        }
    }

    val processFailedRegistrations = candidateRegistrations.flatMap { phase2TestsRegistrations =>
      phase2TestsRegistrations.collect {
        case Failure(e) => throw e
      }
      Future.successful(())
    }

    for {
      _ <- processFailedRegistrations
      emailAddress <- candidateEmailAddress(application.userId)
      (invitationDate, expirationDate) = calculateDates(application)
      _ <- emailInviteToApplicant(application, emailAddress, invitationDate, expirationDate)
    } yield audit("Phase2InvitationComplete", application.userId)
  }

  //scalastyle:off method.length
  def inviteP2CandidateToMissingTest(applicationId: String): Future[Unit] = {

    def allInventoryIds = {
      val standardTests = onlineTestsGatewayConfig.phase2Tests.standard
      standardTests.map { testName =>
        onlineTestsGatewayConfig.phase2Tests.tests.getOrElse(testName, throw new Exception(s"Unable to find inventoryId for $testName"))
      }.map(_.inventoryId).toSet
    }

    def getPsiTestsIds(inventoryId: String) = {
      onlineTestsGatewayConfig.phase2Tests.tests.values.filter( _.inventoryId == inventoryId ).head
    }

    def getCurrentlyRegisteredInventoryIds(phase2TestGroupOpt: Option[Phase2TestGroup]) = {
      phase2TestGroupOpt.map { phase2TestGroup =>
        phase2TestGroup.tests.map( _.inventoryId )
      }.getOrElse(Nil).toSet
    }

    def identifyInventoryIdsCandidateIsMissing(registeredIds: Set[String], allIds: Set[String]) = allIds.diff(registeredIds)

    def registerCandidateForMissingTest(applicationId: String, psiTestIds: PsiTestIds) = {
      logger.warn(s"Candidate $applicationId needs to register for inventoryId:${psiTestIds.inventoryId}")
      implicit val hc = HeaderCarrier()
      for {
        onlineTestApplicationOpt <- testRepository.applicationReadyForOnlineTesting(applicationId)
        application = onlineTestApplicationOpt.getOrElse(throw new Exception(s"No application found for $applicationId"))
        invitationDate = dateTimeFactory.nowLocalTimeZoneJavaTime
        registeredApplicant <- registerPsiApplicant(application, psiTestIds, invitationDate)

        currentTestGroupOpt <- testRepository.getTestGroup(applicationId)
        currentTestGroup = currentTestGroupOpt.getOrElse(throw new Exception(s"No existing p2 test group found for $applicationId"))

        _ <- insertPhase2TestGroups(registeredApplicant)(invitationDate, currentTestGroup.expirationDate, hc)
      } yield ()
    }

    logger.warn(s"Attempting to invite candidate $applicationId to missing P2 tests")
    logger.warn(s"Candidate $applicationId - the full set of inventoryIds=${allInventoryIds.mkString(",")}")
    for {
      status <- appRepository.findStatus(applicationId)
      _ = if (ApplicationStatus.PHASE2_TESTS.toString != status.status) {
        throw new Exception(s"Candidate $applicationId application status is ${status.status}. Expecting ${ApplicationStatus.PHASE2_TESTS}")
      }
      phase2TestGroupOpt <- testRepository.getTestGroup(applicationId)
      registeredInventoryIds = getCurrentlyRegisteredInventoryIds(phase2TestGroupOpt)
      _ = logger.warn(s"Candidate $applicationId is currently registered with tests whose inventory ids=${registeredInventoryIds.mkString(",")}")
      idsToRegisterFor = identifyInventoryIdsCandidateIsMissing(registeredInventoryIds, allInventoryIds)
      _ = if (idsToRegisterFor.size != 1) {
        val idsToRegisterForText = if (idsToRegisterFor.isEmpty){ "empty" } else { idsToRegisterFor.mkString(",") }
        val msg = s"Candidate $applicationId has incorrect number of tests to register for (should be 1). " +
          s"InventoryIds to register for = $idsToRegisterForText"
        throw new Exception(msg)
      }
      _ <- registerCandidateForMissingTest(applicationId, getPsiTestsIds(idsToRegisterFor.head))
    } yield ()
  } //scalastyle:on

  private def processInvigilatedEtrayAccessCode(phase: Option[Phase2TestGroup], accessCode: String): Try[String] = {
    phase.fold[Try[String]](Failure(new NotFoundException(Some("No Phase2TestGroup found")))){
      phase2TestGroup => {

        val psiTestOpt = phase2TestGroup.activeTests.find( _.invigilatedAccessCode == Option(accessCode) )

        psiTestOpt.map { psiTest =>
          if(phase2TestGroup.expirationDate.isBefore(dateTimeFactory.nowLocalTimeZoneJavaTime)) {
            Failure(ExpiredTestForTokenException("Phase 2 test expired for invigilated access code"))
          } else {
            Success(psiTest.testUrl)
          }

        }.getOrElse(Failure(InvalidTokenException("Invigilated access code not found")))
      }
    }
  }

  private def calculateDates(application: OnlineTestApplication, expiresDate: Option[OffsetDateTime] = None) = {
    val isInvigilatedETray = application.isInvigilatedETray
    val expiryTimeInDays = if (isInvigilatedETray) {
      onlineTestsGatewayConfig.phase2Tests.expiryTimeInDaysForInvigilatedETray
    } else {
      onlineTestsGatewayConfig.phase2Tests.expiryTimeInDays
    }

    val (invitationDate, expirationDate) = expiresDate match {
      case Some(expDate) => (dateTimeFactory.nowLocalTimeZoneJavaTime, expDate)
      case _ => calcOnlineTestDates(expiryTimeInDays)
    }

    invitationDate -> expirationDate
  }

  private def registerAndInviteForTestGroup(application: OnlineTestApplication,
                                             testIds: PsiTestIds,
                                             expiresDate: Option[OffsetDateTime] = None)
                                            (implicit hc: HeaderCarrier, rh: RequestHeader): Future[OnlineTestApplication] = {
    //TODO: Do we need to worry about this for PSI?
    //    require(applications.map(_.isInvigilatedETray).distinct.size <= 1, "the batch can have only one type of invigilated e-tray")

    implicit val (invitationDate, expirationDate) = calculateDates(application, expiresDate)

    for {
      registeredApplicant <- registerPsiApplicant(application, testIds, invitationDate)
      _ <- insertPhase2TestGroups(registeredApplicant)(invitationDate, expirationDate, hc)
    } yield {
      logger.warn(s"Phase2 candidate ${application.applicationId} successfully invited to P2 test - inventoryId:${testIds.inventoryId}")
      application
    }
  }

  def registerPsiApplicants(applications: List[OnlineTestApplication],
                            testIds: PsiTestIds, invitationDate: OffsetDateTime)
                           (implicit hc: HeaderCarrier): Future[List[Phase2TestInviteData]] = {
    Future.sequence(
      applications.map { application =>
        registerPsiApplicant(application, testIds, invitationDate)
      })
  }

  private def registerPsiApplicant(application: OnlineTestApplication,
                                   testIds: PsiTestIds,
                                   invitationDate: OffsetDateTime)
                                  (implicit hc: HeaderCarrier): Future[Phase2TestInviteData] = {
    registerApplicant(application, testIds).map { aoa =>
      if (aoa.status != AssessmentOrderAcknowledgement.acknowledgedStatus) {
        val msg = s"Received response status of ${aoa.status} when registering candidate " +
          s"${application.applicationId} to phase2 tests with Ids=$testIds"
        logger.warn(msg)
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
        Phase2TestInviteData(application, psiTest)
      }
    }
  }

  private def registerApplicant(application: OnlineTestApplication, testIds: PsiTestIds)
                                (implicit hc: HeaderCarrier): Future[AssessmentOrderAcknowledgement] = {

    val orderId = tokenFactory.generateUUID()
    val preferredName = TextSanitizer.sanitizeFreeText(application.preferredName)
    val lastName = TextSanitizer.sanitizeFreeText(application.lastName)

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
    val appUrl = onlineTestsGatewayConfig.candidateAppUrl
    val scheduleCompletionBaseUrl = s"$appUrl/fset-fast-stream/online-tests/psi/phase2"
    s"$scheduleCompletionBaseUrl/complete/$orderId"
  }

  private def insertPhase2TestGroups(o: List[Phase2TestInviteData])
                                    (implicit invitationDate: OffsetDateTime,
                                     expirationDate: OffsetDateTime, hc: HeaderCarrier): Future[Unit] = {
    Future.sequence(o.map { completedInvite =>
      val maybeInvigilatedAccessCodeFut = if (completedInvite.application.isInvigilatedETray) {
        authProvider.generateAccessCode.map(ac => Some(ac.token))
      } else {
        Future.successful(None)
      }

      for {
        maybeInvigilatedAccessCode <- maybeInvigilatedAccessCodeFut
        testWithAccessCode = completedInvite.psiTest.copy(invigilatedAccessCode = maybeInvigilatedAccessCode)
        newTestGroup = Phase2TestGroup(expirationDate = expirationDate, List(testWithAccessCode))
        _ <- insertOrUpdateTestGroup(completedInvite.application)(newTestGroup)
      } yield {}
    }).map(_ => ())
  }

  private def insertPhase2TestGroups(completedInvite: Phase2TestInviteData)
                                    (implicit invitationDate: OffsetDateTime,
                                     expirationDate: OffsetDateTime, hc: HeaderCarrier): Future[Unit] = {

    val maybeInvigilatedAccessCodeFut = if (completedInvite.application.isInvigilatedETray) {
      authProvider.generateAccessCode.map(ac => Some(ac.token))
    } else {
      Future.successful(None)
    }
    val appId = completedInvite.application.applicationId

    for {
      maybeInvigilatedAccessCode <- maybeInvigilatedAccessCodeFut
      currentTestGroupOpt <- testRepository.getTestGroup(appId)
      existingTests = currentTestGroupOpt.map(_.tests).getOrElse(Nil)
      testWithAccessCode = completedInvite.psiTest.copy(invigilatedAccessCode = maybeInvigilatedAccessCode)
      newTestGroup = Phase2TestGroup(expirationDate = expirationDate, existingTests :+ testWithAccessCode)
      _ <- insertOrUpdateTestGroup(completedInvite.application)(newTestGroup)
    } yield {}
  }

  private def insertOrUpdateTestGroup(application: OnlineTestApplication)
                                     (newOnlineTestProfile: Phase2TestGroup): Future[Unit] = for {
    currentOnlineTestProfile <- testRepository.getTestGroup(application.applicationId)
    updatedTestProfile <- insertOrAppendNewTests(application.applicationId, currentOnlineTestProfile, newOnlineTestProfile)
    _ <- testRepository.resetTestProfileProgresses(
      application.applicationId, determineStatusesToRemove(updatedTestProfile), ignoreNoRecordUpdated = true
    )
  } yield ()

  private def insertOrAppendNewTests(applicationId: String,
                                     currentProfile: Option[Phase2TestGroup],
                                     newProfile: Phase2TestGroup): Future[Phase2TestGroup] = {

    val insertFut = testRepository.insertOrUpdateTestGroup(applicationId, newProfile)

    insertFut.flatMap { _ =>
      testRepository.getTestGroup(applicationId).map {
        case Some(testProfile) => testProfile
        case None => throw ApplicationNotFound(applicationId)
      }
    }
  }

  def markAsStarted(orderId: String, startedTime: OffsetDateTime = dateTimeFactory.nowLocalTimeZoneJavaTime)
                   (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    updatePhase2Test(orderId, testRepository.updateTestStartTime(_: String, startedTime)).flatMap { u =>
      //TODO: remove the next line and comment in the following line at end of campaign 2019
      testRepository.updateProgressStatus(u.applicationId, ProgressStatuses.PHASE2_TESTS_STARTED) map { _ =>
        //      maybeMarkAsStarted(u.applicationId).map { _ =>
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
        testRepository.updateProgressStatus(appId, PHASE2_TESTS_STARTED)
      }
    }
  }

  def markAsCompleted(orderId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    val updateTestFunc = testRepository.updateTestCompletionTime(_: String, dateTimeFactory.nowLocalTimeZoneJavaTime)
    updatePhase2Test(orderId, updateTestFunc).flatMap { u =>
      val msg = s"Active tests cannot be found when marking phase2 test complete for orderId: $orderId"
      require(u.testGroup.activeTests.nonEmpty, msg)
      val activeTestsCompleted = u.testGroup.activeTests forall (_.completedDateTime.isDefined)
      if (activeTestsCompleted) {
        testRepository.updateProgressStatus(u.applicationId, ProgressStatuses.PHASE2_TESTS_COMPLETED) map { _ =>
          DataStoreEvents.ETrayCompleted(u.applicationId) :: Nil
        }
      } else {
        Future.successful(List.empty[StcEventType])
      }
    }
  }

  private def updatePhase2Test(orderId: String,
                                updatePsiTest: String => Future[Unit]): Future[Phase2TestGroupWithAppId] = {
    updatePsiTest(orderId).flatMap { _ =>
      testRepository.getTestProfileByOrderId(orderId).map(testGroup => testGroup)
    }
  }

  //scalastyle:off method.length
  override def storeRealTimeResults(orderId: String, results: PsiRealTimeResults)
                                   (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {

    def insertResults(applicationId: String, orderId: String, testProfile: Phase2TestGroupWithAppId,
                      results: PsiRealTimeResults): Future[Unit] =
      testRepository.insertTestResult(
        applicationId,
        testProfile.testGroup.tests.find(_.orderId == orderId)
          .getOrElse(throw CannotFindTestByOrderIdException(s"Test not found for orderId=$orderId")),
        model.persisted.PsiTestResult.fromCommandObject(results)
      )

    def maybeUpdateProgressStatus(appId: String) = {
      testRepository.getTestGroup(appId).flatMap { testProfileOpt =>
        val latestProfile = testProfileOpt.getOrElse(throw new Exception(s"No test profile returned for $appId"))
        if (latestProfile.activeTests.forall(_.testResult.isDefined)) {
          testRepository.updateProgressStatus(appId, ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED).map(_ =>
            audit(s"ProgressStatusSet${ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED}", appId))
        } else {
          val msg = s"Did not update progress status to ${ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED} for $appId - " +
            s"not all active tests have a testResult saved"
          logger.warn(msg)
          Future.successful(())
        }
      }
    }

    def markTestAsCompleted(profile: Phase2TestGroupWithAppId): Future[Unit] = {
      profile.testGroup.tests.find(_.orderId == orderId).map { test =>
        if (!test.isCompleted) {
          logger.info(s"Processing real time results - setting completed date on psi test whose orderId=$orderId")
          markAsCompleted(orderId)
        }
        else {
          logger.info(s"Processing real time results - completed date is already set on psi test whose orderId=$orderId " +
            s"so will not mark as complete")
          Future.successful(())
        }
      }.getOrElse(throw CannotFindTestByOrderIdException(s"Test not found for orderId=$orderId"))
    }

    (for {
      appIdOpt <- testRepository.getApplicationIdForOrderId(orderId, "PHASE2")
    } yield {
      val appId = appIdOpt.getOrElse(throw CannotFindTestByOrderIdException(s"Application not found for test for orderId=$orderId"))
      for {
        profile <- testRepository.getTestProfileByOrderId(orderId)
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
                              invitationDate: OffsetDateTime, expirationDate: OffsetDateTime): Future[Unit] = {
    Future.sequence(candidates.map { candidate =>
      emailInviteToApplicant(candidate)(hc, rh, invitationDate, expirationDate)
    }).map(_ => ())
  }

  private def emailInviteToApplicant(candidate: OnlineTestApplication)
                                    (implicit hc: HeaderCarrier,
                                     rh: RequestHeader,
                                     invitationDate: OffsetDateTime,
                                     expirationDate: OffsetDateTime): Future[Unit] = {
    if (candidate.isInvigilatedETray) {
      Future.successful(())
    } else {
      candidateEmailAddress(candidate.userId).flatMap(emailInviteToApplicant(candidate, _ , invitationDate, expirationDate))
    }
  }

  def extendTestGroupExpiryTime(applicationId: String, extraDays: Int, actionTriggeredBy: String)
                               (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    val progressFut = appRepository.findProgress(applicationId)
    val phase2TestGroup = testRepository.getTestGroup(applicationId)
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

  private def progressStatusesToRemoveWhenExtendTime(extendedExpiryDate: OffsetDateTime,
                                                     profile: Phase2TestGroup,
                                                     progress: ProgressResponse): Option[List[ProgressStatus]] = {
    val shouldRemoveExpired = progress.phase2ProgressResponse.phase2TestsExpired
    val today = dateTimeFactory.nowLocalTimeZoneJavaTime
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
}

trait ResetPhase2Test {

  import ProgressStatuses._

  def determineStatusesToRemove(testGroup: Phase2TestGroup): List[ProgressStatus] = {
    (if (testGroup.hasNotStartedYet) List(PHASE2_TESTS_STARTED) else List()) ++
      (if (testGroup.hasNotCompletedYet) List(PHASE2_TESTS_COMPLETED) else List()) ++
      (if (testGroup.hasNotResultReadyToDownloadForAllTestsYet) List(PHASE2_TESTS_RESULTS_RECEIVED, PHASE2_TESTS_RESULTS_READY) else List()) ++
      List(PHASE2_TESTS_FAILED, PHASE2_TESTS_EXPIRED, PHASE2_TESTS_PASSED, PHASE2_TESTS_FAILED_NOTIFIED, PHASE2_TESTS_FAILED_SDIP_AMBER)
  }
}

//scalastyle:on number.of.methods
