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
import common.Phase2TestConcern
import config.{ CubiksGatewayConfig, Phase2Schedule, Phase2TestsConfig }
import connectors.ExchangeObjects._
import connectors.{ AuthProviderClient, CubiksGatewayClient, Phase2OnlineTestEmailClient }
import factories.{ DateTimeFactory, UUIDFactory }
import model.Exceptions._
import model.OnlineTestCommands._
import model.ProgressStatuses._
import model.command.ProgressResponse
import model.events.EventTypes.EventType
import model.events.{ AuditEvent, AuditEvents, DataStoreEvents }
import model.exchange.{ CubiksTestResultReady, Phase2TestGroupWithActiveTest }
import model.persisted._
import model.{ ProgressStatuses, ReminderNotice, TestExpirationEvent, _ }
import org.joda.time.DateTime
import play.api.mvc.RequestHeader
import repositories._
import repositories.onlinetesting.Phase2TestRepository
import services.events.EventService
import services.onlinetesting.ResetPhase2Test._
import services.onlinetesting.phase2.ScheduleSelector
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.{ Failure, Success, Try }

object Phase2TestService extends Phase2TestService {

  import config.MicroserviceAppConfig._

  val appRepository = applicationRepository
  val cdRepository = faststreamContactDetailsRepository
  val phase2TestRepo = phase2TestRepository
  val cubiksGatewayClient = CubiksGatewayClient
  val tokenFactory = UUIDFactory
  val dateTimeFactory = DateTimeFactory
  val emailClient = Phase2OnlineTestEmailClient
  val auditService = AuditService
  val gatewayConfig = cubiksGatewayConfig
  val actor = ActorSystem()
  val eventService = EventService
  val authProvider = AuthProviderClient
}

// scalastyle:off number.of.methods
trait Phase2TestService extends OnlineTestService with Phase2TestConcern with ScheduleSelector {
  val actor: ActorSystem
  val phase2TestRepo: Phase2TestRepository
  val cubiksGatewayClient: CubiksGatewayClient
  val gatewayConfig: CubiksGatewayConfig
  val authProvider: AuthProviderClient

  def testConfig: Phase2TestsConfig = gatewayConfig.phase2Tests

  case class Phase2TestInviteData(application: OnlineTestApplication,
                                  scheduleId: Int,
                                  token: String,
                                  registration: Registration,
                                  invitation: Invitation)

  case class NoActiveTestException(m: String) extends Exception(m)

  def getTestProfile(applicationId: String): Future[Option[Phase2TestGroupWithActiveTest]] = {
    for {
      phase2Opt <- phase2TestRepo.getTestGroup(applicationId)
    } yield phase2Opt.map { phase2 =>
      val test = phase2.activeTests
        .find(_.usedForResults)
        .getOrElse(throw NoActiveTestException(s"No active phase 2 test found for $applicationId"))
      Phase2TestGroupWithActiveTest(
        phase2.expirationDate,
        test,
        schedulesAvailable(phase2.tests.map(_.scheduleId))
      )
    }
  }

  def verifyAccessCode(email: String, accessCode: String): Future[String] = for {
    userId <- cdRepository.findUserIdByEmail(email)
    testGroupOpt <- phase2TestRepo.getTestGroupByUserId(userId)
    testUrl <- Future.fromTry(processEtrayToken(testGroupOpt, accessCode))
  } yield testUrl

  override def nextApplicationReadyForOnlineTesting: Future[List[OnlineTestApplication]] = {
    phase2TestRepo.nextApplicationsReadyForOnlineTesting
  }

  override def processNextTestForReminder(reminder: ReminderNotice)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    phase2TestRepo.nextTestForReminder(reminder).flatMap {
      case Some(expiringTest) => processReminder(expiringTest, reminder)
      case None => Future.successful(())
    }
  }

  override def nextTestGroupWithReportReady: Future[Option[Phase2TestGroupWithAppId]] = {
    phase2TestRepo.nextTestGroupWithReportReady
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

  def resetTests(application: OnlineTestApplication, actionTriggeredBy: String)
                (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    phase2TestRepo.getTestGroup(application.applicationId).flatMap {
      case Some(phase2TestGroup) if !application.isInvigilatedETray && schedulesAvailable(phase2TestGroup.tests.map(_.scheduleId)) =>
        val (scheduleName, schedule) = getRandomScheduleWithName(phase2TestGroup.tests.map(_.scheduleId))
        registerAndInviteForTestGroup(List(application), schedule).map { _ =>
          audit("Phase2TestInvitationProcessComplete", application.userId)
          AuditEvents.Phase2TestsReset(Map("userId" -> application.userId, "tests" -> "e-tray")) ::
            DataStoreEvents.ETrayReset(application.applicationId, actionTriggeredBy) :: Nil
        }
      case Some(phase2TestGroup) if !schedulesAvailable(phase2TestGroup.tests.map(_.scheduleId)) =>
        throw ResetLimitExceededException()
      case _ =>
        throw CannotResetPhase2Tests()
    }
  }

  override def registerAndInviteForTestGroup(application: OnlineTestApplication)
                                            (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    registerAndInviteForTestGroup(List(application))
  }

  override def processNextExpiredTest(expiryTest: TestExpirationEvent)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    phase2TestRepo.nextExpiringApplication(expiryTest).flatMap {
      case Some(expired) => processExpiredTest(expired, expiryTest)
      case None => Future.successful(())
    }
  }

  def registerApplicants(candidates: List[OnlineTestApplication], tokens: Seq[String])
                        (implicit hc: HeaderCarrier): Future[Map[Int, (OnlineTestApplication, String, Registration)]] = {
    cubiksGatewayClient.registerApplicants(candidates.size).map(_.zipWithIndex.map { case (registration, idx) =>
      val candidate = candidates(idx)
      audit("Phase2TestRegistered", candidate.userId)
      (registration.userId, (candidate, tokens(idx), registration))
    }.toMap)
  }

  def inviteApplicants(candidateData: Map[Int, (OnlineTestApplication, String, Registration)],
                      schedule: Phase2Schedule)
                      (implicit hc: HeaderCarrier): Future[List[Phase2TestInviteData]] = {
    val invites = candidateData.values.map { case (application, token, registration) =>
      buildInviteApplication(application, token, registration.userId, schedule)
    }.toList

    // Cubiks does not accept invite batch request with different time adjustments
    // TODO LT: The filter based on the head should be done before registration, not after
    val firstInvite = invites.head
    val filteredInvites = invites.filter(_.timeAdjustments == firstInvite.timeAdjustments)

    cubiksGatewayClient.inviteApplicants(filteredInvites).map(_.map { invitation =>
      val (application, token, registration) = candidateData(invitation.userId)
      audit("Phase2TestInvited", application.userId)
      Phase2TestInviteData(application, schedule.scheduleId, token, registration, invitation)
    })
  }

  override def registerAndInviteForTestGroup(applications: List[OnlineTestApplication])
                                            (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    // Cubiks does not accept invite batch request with different scheduleId.
    // Due to this limitation we cannot have multiple types of invitations, and the filtering is needed
    val firstApplication = applications.head
    val applicationsWithTheSameType = applications filter (_.isInvigilatedETray == firstApplication.isInvigilatedETray)

    val isInvigilatedETrayBatch = applicationsWithTheSameType.head.isInvigilatedETray
    val (scheduleName, schedule) = if (isInvigilatedETrayBatch) {
      val schedule = testConfig.scheduleForInvigilatedETray
      (testConfig.scheduleNameByScheduleId(schedule.scheduleId), schedule)
    } else {
      getRandomScheduleWithName()
    }
    registerAndInviteForTestGroup(applicationsWithTheSameType, schedule) flatMap { candidatesToProgress =>
      eventSink {
        Future.successful {
          candidatesToProgress.map(candidate => {
            // TODO LT: This events should be emit one level down to also be logged by reset path
            DataStoreEvents.OnlineExerciseResultSent(candidate.applicationId) ::
              AuditEvents.Phase2TestInvitationProcessComplete(Map(
                "userId" -> candidate.userId,
                "absoluteTime" -> s"${calculateAbsoluteTimeWithAdjustments(candidate)}",
                "scheduleName" -> s"$scheduleName")) ::
              Nil
          }).flatten
        }
      }
    }
  }

  private def processEtrayToken(phase: Option[Phase2TestGroup], accessCode: String): Try[String] = {
    phase.fold[Try[String]](Failure(new NotFoundException(Some("No Phase2TestGroup found")))){
      group => {
        val eTrayTest = group.activeTests.head
        val accessCodeOpt = eTrayTest.invigilatedAccessCode

        if (accessCodeOpt.contains(accessCode)) {
          if(group.expirationDate.isBefore(dateTimeFactory.nowLocalTimeZone)) {
            Failure(ExpiredTestForTokenException("Test expired for token"))
          } else {
            Success(eTrayTest.testUrl)
          }
        } else {
          Failure(InvalidTokenException("Token mismatch"))
        }
      }
    }
  }

  private def registerAndInviteForTestGroup(applications: List[OnlineTestApplication], schedule: Phase2Schedule)
                                           (implicit hc: HeaderCarrier, rh: RequestHeader): Future[List[OnlineTestApplication]] = {
    require(applications.map(_.isInvigilatedETray).distinct.size <= 1, "the batch can have only one type of invigilated e-tray")

    applications match {
      case Nil => Future.successful(Nil)
      case candidatesToProcess =>
        val tokens = for (i <- 1 to candidatesToProcess.size) yield tokenFactory.generateUUID()
        val isInvigilatedETrayBatch = applications.head.isInvigilatedETray
        val expiryTimeInDays = if (isInvigilatedETrayBatch) {
          gatewayConfig.phase2Tests.expiryTimeInDaysForInvigilatedETray
        } else {
          gatewayConfig.phase2Tests.expiryTimeInDays
        }
        implicit val (invitationDate, expirationDate) = calcOnlineTestDates(expiryTimeInDays)

        for {
          registeredApplicants <- registerApplicants(candidatesToProcess, tokens)
          invitedApplicants <- inviteApplicants(registeredApplicants, schedule)
          _ <- insertPhase2TestGroups(invitedApplicants)(invitationDate, expirationDate, hc)
          _ <- emailInviteToApplicants(candidatesToProcess)(hc, rh, invitationDate, expirationDate)
        } yield {
          candidatesToProcess
        }
    }
  }

  def buildInviteApplication(application: OnlineTestApplication, token: String, userId: Int, schedule: Phase2Schedule) = {
    val scheduleCompletionBaseUrl = s"${gatewayConfig.candidateAppUrl}/fset-fast-stream/online-tests/phase2"

    InviteApplicant(schedule.scheduleId,
      userId,
      s"$scheduleCompletionBaseUrl/complete/$token",
      resultsURL = None,
      timeAdjustments = buildTimeAdjustments(schedule.assessmentId, application)
    )
  }

  private def insertPhase2TestGroups(o: List[Phase2TestInviteData])
                                    (implicit invitationDate: DateTime, expirationDate: DateTime, hc: HeaderCarrier): Future[Unit] =
    Future.sequence(o.map { completedInvite =>
      val maybeInvigilatedAccessCodeFut = if (completedInvite.application.isInvigilatedETray) {
        authProvider.generateAccessCode.map(ac => Some(ac.token))
      } else {
        Future.successful(None)
      }

      for {
        maybeInvigilatedAccessCode <- maybeInvigilatedAccessCodeFut
        newTestGroup = Phase2TestGroup(expirationDate = expirationDate,
          List(CubiksTest(scheduleId = completedInvite.scheduleId,
            usedForResults = true,
            cubiksUserId = completedInvite.registration.userId,
            token = completedInvite.token,
            testUrl = completedInvite.invitation.authenticateUrl,
            invitationDate = invitationDate,
            participantScheduleId = completedInvite.invitation.participantScheduleId,
            invigilatedAccessCode = maybeInvigilatedAccessCode
          ))
        )
        _ <- insertOrUpdateTestGroup(completedInvite.application)(newTestGroup)
      } yield {}
    }).map(_ => ())

  private def insertOrUpdateTestGroup(application: OnlineTestApplication)
                                     (newOnlineTestProfile: Phase2TestGroup): Future[Unit] = for {
    currentOnlineTestProfile <- phase2TestRepo.getTestGroup(application.applicationId)
    updatedTestProfile <- insertOrAppendNewTests(application.applicationId, currentOnlineTestProfile, newOnlineTestProfile)
    _ <- phase2TestRepo.resetTestProfileProgresses(application.applicationId, determineStatusesToRemove(updatedTestProfile))
  } yield ()

  private def insertOrAppendNewTests(applicationId: String, currentProfile: Option[Phase2TestGroup],
                                     newProfile: Phase2TestGroup): Future[Phase2TestGroup] = {
    (currentProfile match {
      case None => phase2TestRepo.insertOrUpdateTestGroup(applicationId, newProfile)
      case Some(profile) =>
        val existingActiveTests = profile.tests.filter(_.usedForResults).map(_.cubiksUserId)
        Future.traverse(existingActiveTests)(phase2TestRepo.markTestAsInactive).flatMap { _ =>
          phase2TestRepo.insertCubiksTests(applicationId, newProfile)
        }
    }).flatMap { _ => phase2TestRepo.getTestGroup(applicationId)
    }.map {
      case Some(testProfile) => testProfile
      case None => throw ApplicationNotFound(applicationId)
    }
  }

  def markAsStarted(cubiksUserId: Int, startedTime: DateTime = dateTimeFactory.nowLocalTimeZone)
                   (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    updatePhase2Test(cubiksUserId, phase2TestRepo.updateTestStartTime(_: Int, startedTime)).flatMap { u =>
      phase2TestRepo.updateProgressStatus(u.applicationId, ProgressStatuses.PHASE2_TESTS_STARTED) map { _ =>
        DataStoreEvents.ETrayStarted(u.applicationId) :: Nil
      }
    }
  }

  def markAsCompleted(cubiksUserId: Int)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    updatePhase2Test(cubiksUserId, phase2TestRepo.updateTestCompletionTime(_: Int, dateTimeFactory.nowLocalTimeZone)).flatMap { u =>
      require(u.testGroup.activeTests.nonEmpty, "Active tests cannot be found")
      val activeTestsCompleted = u.testGroup.activeTests forall (_.completedDateTime.isDefined)
      activeTestsCompleted match {
        case true => phase2TestRepo.updateProgressStatus(u.applicationId, ProgressStatuses.PHASE2_TESTS_COMPLETED) map { _ =>
          DataStoreEvents.ETrayCompleted(u.applicationId) :: Nil
        }

        case false => Future.successful(List.empty[EventType])
      }
    }
  }

  def markAsCompleted(token: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    phase2TestRepo.getTestProfileByToken(token).flatMap { p =>
      p.tests.find(_.token == token).map { test => markAsCompleted(test.cubiksUserId) }
        .getOrElse(Future.successful(()))
    }
  }

  def markAsReportReadyToDownload(cubiksUserId: Int, reportReady: CubiksTestResultReady): Future[Unit] = {
    updatePhase2Test(cubiksUserId, phase2TestRepo.updateTestReportReady(_: Int, reportReady)).flatMap { updated =>
      if (updated.testGroup.activeTests forall (_.resultsReadyToDownload)) {
        phase2TestRepo.updateProgressStatus(updated.applicationId, ProgressStatuses.PHASE2_TESTS_RESULTS_READY)
      } else {
        Future.successful(())
      }
    }
  }

  private def updatePhase2Test(cubiksUserId: Int, updateCubiksTest: Int => Future[Unit]): Future[Phase2TestGroupWithAppId] = {
    for {
      _ <- updateCubiksTest(cubiksUserId)
      updated <- phase2TestRepo.getTestProfileByCubiksId(cubiksUserId)
    } yield {
      updated
    }
  }

  def buildTimeAdjustments(assessmentId: Int, application: OnlineTestApplication) = {
    application.eTrayAdjustments.flatMap(_.timeNeeded).map { extraTime =>
      List(TimeAdjustments(assessmentId, sectionId = 1, absoluteTime = calculateAbsoluteTimeWithAdjustments(application)))
    }.getOrElse(Nil)
  }

  def emailInviteToApplicants(candidates: List[OnlineTestApplication])
    (implicit hc: HeaderCarrier, rh: RequestHeader, invitationDate: DateTime, expirationDate: DateTime): Future[Unit] =
  Future.sequence(candidates.map { candidate =>
    if (candidate.isInvigilatedETray) {
      Future.successful(())
    } else {
      candidateEmailAddress(candidate.userId).flatMap(emailInviteToApplicant(candidate, _ , invitationDate, expirationDate))
    }
  }).map( _ => () )

  def extendTestGroupExpiryTime(applicationId: String, extraDays: Int, actionTriggeredBy: String)
                               (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    val progressFut = appRepository.findProgress(applicationId)
    val phase2TestGroup = phase2TestRepo.getTestGroup(applicationId)
      .map(tg => tg.getOrElse(throw new IllegalStateException("Expiration date for Phase 2 cannot be extended. Test group not found.")))

    for {
      progress <- progressFut
      phase2 <- phase2TestGroup
      isAlreadyExpired = progress.phase2ProgressResponse.phase2TestsExpired
      extendDays = extendTime(isAlreadyExpired, phase2.expirationDate)
      newExpiryDate = extendDays(extraDays)
      _ <- phase2TestRepo.updateGroupExpiryTime(applicationId, newExpiryDate, phase2TestRepo.phaseName)
      _ <- progressStatusesToRemoveWhenExtendTime(newExpiryDate, phase2, progress)
        .fold(Future.successful(()))(p => appRepository.removeProgressStatuses(applicationId, p))
    } yield {
      audit(isAlreadyExpired, applicationId) ::
        DataStoreEvents.ETrayExtended(applicationId, actionTriggeredBy) ::
        Nil
    }
  }

  private def progressStatusesToRemoveWhenExtendTime(extendedExpiryDate: DateTime,
                                                     profile: Phase2TestGroup,
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

    def insertTests(testResults: List[(OnlineTestCommands.TestResult, U)]): Future[Unit] = {
      Future.sequence(testResults.map {
        case (result, phase1Test) => phase2TestRepo.insertTestResult(
          testProfile.applicationId,
          phase1Test, model.persisted.TestResult.fromCommandObject(result)
        )
      }).map(_ => ())
    }

    def maybeUpdateProgressStatus(appId: String) = {
      phase2TestRepo.getTestGroup(appId).flatMap { eventualProfile =>

        val latestProfile = eventualProfile.getOrElse(throw new Exception(s"No profile returned for $appId"))
        if (latestProfile.activeTests.forall(_.testResult.isDefined)) {
          phase2TestRepo.updateProgressStatus(appId, ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED).map(_ =>
            audit(s"ProgressStatusSet${ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED}", appId))
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
      eventualTestResults.foreach { _ =>
        audit(s"ResultsRetrievedForSchedule", testProfile.applicationId)
      }
    }
  }
}

object ResetPhase2Test {

  import ProgressStatuses._

  case class CannotResetPhase2Tests() extends NotFoundException

  case class ResetLimitExceededException() extends Exception

  def determineStatusesToRemove(testGroup: Phase2TestGroup): List[ProgressStatus] = {
    (if (testGroup.hasNotStartedYet) List(PHASE2_TESTS_STARTED) else List()) ++
      (if (testGroup.hasNotCompletedYet) List(PHASE2_TESTS_COMPLETED) else List()) ++
      (if (testGroup.hasNotResultReadyToDownloadForAllTestsYet) List(PHASE2_TESTS_RESULTS_RECEIVED, PHASE2_TESTS_RESULTS_READY) else List()) ++
      List(PHASE2_TESTS_FAILED)
  }
}

//scalastyle:on number.of.methods
