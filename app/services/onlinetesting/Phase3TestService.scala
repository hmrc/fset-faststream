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
import common.Phase3TestConcern
import config.LaunchpadGatewayConfig
import connectors._
import connectors.launchpadgateway.LaunchpadGatewayClient
import connectors.launchpadgateway.exchangeobjects.out._
import factories.{ DateTimeFactory, UUIDFactory }
import model.Exceptions.{ ConnectorException, NotFoundException }
import model.OnlineTestCommands._
import model.ProgressStatuses._
import model._
import model.command.ProgressResponse
import model.events.EventTypes.EventType
import model.events.{ AuditEvents, DataStoreEvents }
import model.exchange.Phase3TestGroupWithActiveTest
import model.persisted.phase3tests.{ LaunchpadTest, LaunchpadTestCallbacks, Phase3TestGroup }
import model.persisted.{ NotificationExpiringOnlineTest, Phase3TestGroupWithAppId }
import org.joda.time.{ DateTime, LocalDate }
import play.api.mvc.RequestHeader
import repositories._
import repositories.onlinetesting.Phase3TestRepository
import services.events.EventService
import services.onlinetesting.Phase2TestService.NoActiveTestException
import services.onlinetesting.ResetPhase3Test.CannotResetPhase3Tests
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps

object Phase3TestService extends Phase3TestService {

  import config.MicroserviceAppConfig._

  val appRepository = applicationRepository
  val phase3TestRepo = phase3TestRepository
  val cdRepository = faststreamContactDetailsRepository
  val launchpadGatewayClient = LaunchpadGatewayClient
  val tokenFactory = UUIDFactory
  val dateTimeFactory = DateTimeFactory
  val emailClient = Phase3OnlineTestEmailClient
  val auditService = AuditService
  val gatewayConfig = launchpadGatewayConfig
  val eventService = EventService

}

//scalastyle:off number.of.methods
trait Phase3TestService extends OnlineTestService with Phase3TestConcern {

  val phase3TestRepo: Phase3TestRepository
  val launchpadGatewayClient: LaunchpadGatewayClient
  val gatewayConfig: LaunchpadGatewayConfig

  override def nextApplicationReadyForOnlineTesting: Future[List[OnlineTestApplication]] = {
    phase3TestRepo.nextApplicationsReadyForOnlineTesting
  }

  def getTestGroup(applicationId: String): Future[Option[Phase3TestGroup]] = phase3TestRepo.getTestGroup(applicationId)

  def getTestGroupWithActiveTest(applicationId: String): Future[Option[Phase3TestGroupWithActiveTest]] = {
    for {
      phase3Opt <- phase3TestRepo.getTestGroup(applicationId)
    } yield phase3Opt.map { phase3 =>
      val test = phase3.activeTests
        .find(_.usedForResults)
        .getOrElse(throw NoActiveTestException(s"No active phase 3 test found for $applicationId"))
      Phase3TestGroupWithActiveTest(
        phase3.expirationDate,
        test
      )
    }
  }

  def removeTestGroup(applicationId: String)(implicit hc: HeaderCarrier,
                                             rh: RequestHeader): Future[Unit] = eventSink {
    phase3TestRepo.removeTestGroup(applicationId).map { _ =>
      AuditEvents.VideoInterviewRemoved(applicationId) ::
        DataStoreEvents.VideoInterviewRemoved(applicationId) ::
        Nil
    }
  }

  override def registerAndInviteForTestGroup(applications: List[OnlineTestApplication])
                                            (implicit hc: HeaderCarrier,
                                             rh: RequestHeader): Future[Unit] =
    Future.sequence(applications.map(app => registerAndInviteForTestGroup(app))).map(_ => ())

  override def registerAndInviteForTestGroup(application: OnlineTestApplication)
                                            (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    registerAndInviteForTestGroup(application, getInterviewIdForApplication(application), None)
  }

  def registerAndInviteForTestGroup(application: OnlineTestApplication, phase3TestGroup: Option[Phase3TestGroup] = None)
                                   (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    registerAndInviteForTestGroup(application, getInterviewIdForApplication(application), phase3TestGroup)
  }

  override def nextTestGroupWithReportReady: Future[Option[Phase3TestGroupWithAppId]] =
    Future.successful(None)

  override def retrieveTestResult(testProfile: Phase3TestGroupWithAppId)
                                 (implicit hc: HeaderCarrier): Future[Unit] = Future.successful(())

  override def processNextExpiredTest(expiryTest: TestExpirationEvent)
                                     (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    phase3TestRepo.nextExpiringApplication(expiryTest).flatMap {
      case Some(expired) => processExpiredTest(expired, expiryTest)
      case None => Future.successful(())
    }
  }

  override def processNextTestForReminder(reminder: model.ReminderNotice)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] =
    phase3TestRepo.nextTestForReminder(reminder).flatMap {
      case Some(expiringTest) => processReminder(expiringTest, reminder)
      case None => Future.successful(())
    }

  override def emailCandidateForExpiringTestReminder(expiringTest: NotificationExpiringOnlineTest,
                                                     emailAddress: String,
                                                     reminder: ReminderNotice)
                                                    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    for {
      _ <- emailClient.sendTestExpiringReminder(emailAddress, expiringTest.preferredName,
        reminder.hoursBeforeReminder, reminder.timeUnit, expiringTest.expiryDate)
    } yield {
      AuditEvents.VideoInterviewTestExpiryReminder(
        Map(
          "EmailReminderType" -> s"ReminderPhase3VideoInterviewExpiring${reminder.hoursBeforeReminder}HoursEmailed",
          "User" -> expiringTest.userId,
          "Email" -> emailAddress
        )
      ) ::
        DataStoreEvents.VideoInterviewExpiryReminder(expiringTest.applicationId) ::
        Nil
    }
  }

  def resetTests(application: OnlineTestApplication, actionTriggeredBy: String)
                (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    import ApplicationStatus._

    ApplicationStatus.withName(application.applicationStatus) match {
      case PHASE3_TESTS | PHASE3_TESTS_PASSED | PHASE3_TESTS_FAILED | PHASE3_TESTS_PASSED_WITH_AMBER =>
        phase3TestRepo.getTestGroup(application.applicationId).flatMap { phase3TestGroup =>
          registerAndInviteForTestGroup(application, phase3TestGroup).map { _ =>
            AuditEvents.VideoInterviewRescheduled(Map("userId" -> application.userId, "tests" -> "video-interview")) ::
              DataStoreEvents.VideoInterviewRescheduled(application.applicationId, actionTriggeredBy) :: Nil
          }
        }
      case _ => throw CannotResetPhase3Tests()
    }
  }

  private[onlinetesting] def registerAndInviteForTestGroup(application: OnlineTestApplication,
                                                           interviewId: Int,
                                                           phase3TestGroup: Option[Phase3TestGroup])
                                                          (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val daysUntilExpiry = gatewayConfig.phase3Tests.timeToExpireInDays

    val expirationDate = phase3TestGroup.map { phase3TG =>
      if (phase3TG.expirationDate.isAfter(dateTimeFactory.nowLocalTimeZone)) {
        phase3TG.expirationDate
      } else {
        dateTimeFactory.nowLocalTimeZone.plusDays(daysUntilExpiry)
      }
    }.getOrElse(dateTimeFactory.nowLocalTimeZone.plusDays(daysUntilExpiry))

    val invitationDate = dateTimeFactory.nowLocalTimeZone

    def emailProcess(emailAddress: String): Future[Unit] = if (application.isInvigilatedVideo) {
      Future.successful(())
    } else {
      emailInviteToApplicant(application, emailAddress, invitationDate, expirationDate)
    }

    def extendIfInvigilated(application: OnlineTestApplication): Future[Unit] = if (application.isInvigilatedVideo) {
      extendTestGroupExpiryTime(
        application.applicationId,
        gatewayConfig.phase3Tests.invigilatedTimeToExpireInDays - daysUntilExpiry,
        "InvigilatedInviteSystem"
      )
    } else {
      Future.successful(())
    }

    for {
      emailAddress <- candidateEmailAddress(application)
      launchPadTest <- registerAndInviteOrInviteOrResetOrRetake(
        phase3TestGroup, application, emailAddress, interviewId, invitationDate, expirationDate)
      _ <- emailProcess(emailAddress)
      _ <- markAsInvited(application)(Phase3TestGroup(expirationDate = expirationDate, tests = List(launchPadTest)))
      _ <- extendIfInvigilated(application)
      _ <- eventSink {
        AuditEvents.VideoInterviewRegistrationAndInviteComplete("userId" -> application.userId) ::
          DataStoreEvents.VideoInterviewRegistrationAndInviteComplete(application.applicationId) :: Nil
      }
    } yield {}
  }

  //scalastyle:off method.length
  private[onlinetesting] def registerAndInviteOrInviteOrResetOrRetake(phase3TestGroup: Option[Phase3TestGroup],
                                                                      application: OnlineTestApplication, emailAddress: String,
                                                                      interviewId: Int, invitationDate: DateTime,
                                                                      expirationDate: DateTime
                                                                     )(implicit hc: HeaderCarrier, rh: RequestHeader): Future[LaunchpadTest] = {


    case class InviteResetOrTakeResponse(candidateId: String, testUrl: String, customInviteId: String, customCandidateId: Option[String])

    def inviteOrResetOrRetake: Future[InviteResetOrTakeResponse] = {
      def hasNotCompletedSameInterviewBefore(phase3TestGroup: Phase3TestGroup) = {
        phase3TestGroup.tests.filter(launchPadTest =>
          !launchPadTest.completedDateTime.isDefined && launchPadTest.interviewId == interviewId).size > 0
      }

      def hasCompletedSameInterviewBefore(phase3TestGroup: Phase3TestGroup) = {
        phase3TestGroup.tests.filter(launchPadTest =>
          launchPadTest.completedDateTime.isDefined && launchPadTest.interviewId == interviewId).size > 0
      }

      val customCandidateId = "FSCND-" + tokenFactory.generateUUID()
      val getInitialCustomCandidateId = phase3TestGroup.map(_.activeTests.head.customCandidateId)
      phase3TestGroup.map { phase3TestGroupContent => // If invited before
        val candidateId = phase3TestGroupContent.tests.head.candidateId
        if (hasCompletedSameInterviewBefore(phase3TestGroupContent)) {
          retakeApplicant(application, interviewId, candidateId, phase3TestGroupContent.expirationDate.toLocalDate).map { retakeResponse =>
            InviteResetOrTakeResponse(candidateId, retakeResponse.testUrl, retakeResponse.customInviteId, getInitialCustomCandidateId)
          }
        } else if (hasNotCompletedSameInterviewBefore(phase3TestGroupContent)) {
          resetApplicant(application, interviewId, candidateId, phase3TestGroupContent.expirationDate.toLocalDate).map { resetResponse =>
            InviteResetOrTakeResponse(candidateId, resetResponse.testUrl, resetResponse.customInviteId, getInitialCustomCandidateId)
          }
        } else {
          // invite second time or later with different interview
          inviteApplicant(application, interviewId, phase3TestGroupContent.tests.head.candidateId).map { inviteResponse =>
            InviteResetOrTakeResponse(candidateId, inviteResponse.testUrl, inviteResponse.customInviteId, Some(inviteResponse.customCandidateId))
          }
        }
      }.getOrElse(// If invite first time
        registerApplicant(application, emailAddress, customCandidateId).flatMap { candidateId =>
          inviteApplicant(application, interviewId, candidateId).map { inviteResponse =>
            InviteResetOrTakeResponse(candidateId, inviteResponse.testUrl, inviteResponse.customInviteId, Some(inviteResponse.customCandidateId))
          }
        }
      )
    }

    inviteOrResetOrRetake.map(response =>
      LaunchpadTest(interviewId = interviewId,
        usedForResults = true,
        testUrl = response.testUrl,
        token = response.customInviteId,
        candidateId = response.candidateId,
        customCandidateId = response.customCandidateId.getOrElse(""),
        invitationDate = invitationDate,
        startedDateTime = None,
        completedDateTime = None,
        callbacks = LaunchpadTestCallbacks()
      )
    )
  }

  //scalastyle:on method.length
  @deprecated
  private def registerAndInviteApplicant(application: OnlineTestApplication, emailAddress: String, interviewId: Int, invitationDate: DateTime,
                                         expirationDate: DateTime
                                        )(implicit hc: HeaderCarrier, rh: RequestHeader): Future[LaunchpadTest] = {
    val customCandidateId = "FSCND-" + tokenFactory.generateUUID()

    for {
      candidateId <- registerApplicant(application, emailAddress, customCandidateId)
      invitation <- inviteApplicant(application, interviewId, candidateId)
    } yield {
      LaunchpadTest(interviewId = interviewId,
        usedForResults = true,
        testUrl = invitation.testUrl,
        token = invitation.customInviteId,
        candidateId = candidateId,
        customCandidateId = invitation.customCandidateId,
        invitationDate = invitationDate,
        startedDateTime = None,
        completedDateTime = None,
        callbacks = LaunchpadTestCallbacks()
      )
    }
  }

  def markAsStarted(launchpadInviteId: String, startedTime: DateTime = dateTimeFactory.nowLocalTimeZone)
                   (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    for {
      _ <- phase3TestRepo.updateTestStartTime(launchpadInviteId, startedTime)
      updated <- phase3TestRepo.getTestGroupByToken(launchpadInviteId)
      _ <- phase3TestRepo.updateProgressStatus(updated.applicationId, ProgressStatuses.PHASE3_TESTS_STARTED)
    } yield {
      AuditEvents.VideoInterviewStarted(updated.applicationId) ::
        DataStoreEvents.VideoInterviewStarted(updated.applicationId) ::
        Nil
    }
  }

  // We ignore mark as completed requests on records without start date. That will cover the scenario, where we reschudule a
  // video interview before recieving the complete callbacks from launchpad.
  def markAsCompleted(launchpadInviteId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    eventSink {
      phase3TestRepo.getTestGroupByToken(launchpadInviteId).flatMap { test =>
        val launchpadTest = test.testGroup.tests.find(_.token == launchpadInviteId).get
        if (launchpadTest.completedDateTime.isEmpty && !launchpadTest.startedDateTime.isEmpty) {
          for {
            _ <- phase3TestRepo.updateTestCompletionTime(launchpadInviteId, dateTimeFactory.nowLocalTimeZone)
            updated <- phase3TestRepo.getTestGroupByToken(launchpadInviteId)
            // Launchpad only: If a user has completed unexpire them
            _ <- removeExpiryStatus(updated.applicationId)
            _ <- phase3TestRepo.updateProgressStatus(updated.applicationId, ProgressStatuses.PHASE3_TESTS_COMPLETED)
          } yield {
            AuditEvents.VideoInterviewCompleted(updated.applicationId) ::
              DataStoreEvents.VideoInterviewCompleted(updated.applicationId) ::
              Nil
          }
        } else {
          Future.successful(List[EventType]())
        }
      }
    }
  }

  private def removeExpiryStatus(applicationId: String): Future[Unit] = {
    for {
      progress <- appRepository.findProgress(applicationId)
      actionFut <- if (progress.phase3ProgressResponse.phase3TestsExpired) {
        appRepository.removeProgressStatuses(applicationId, ProgressStatuses.PHASE3_TESTS_EXPIRED :: Nil)
      } else {
        Future.successful(())
      }
    } yield actionFut
  }

  def addResetEventMayBe(launchpadInviteId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    for {
      testGroup <- phase3TestRepo.getTestGroupByToken(launchpadInviteId)
      progressResponse <- appRepository.findProgress(testGroup.applicationId)
    } yield {
      val phase3Progress = progressResponse.phase3ProgressResponse
      val phase3Completed = phase3Progress.phase3TestsResultsReceived || phase3Progress.phase3TestsCompleted
      if (phase3Completed) {
        AuditEvents.VideoInterviewReset(testGroup.applicationId) ::
          DataStoreEvents.VideoInterviewReset(testGroup.applicationId) ::
          Nil
      } else {
        Nil
      }
    }
  }

  def markAsResultsReceived(launchpadInviteId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    phase3TestRepo.getTestGroupByToken(launchpadInviteId).flatMap { _ =>
      for {
        testGroup <- phase3TestRepo.getTestGroupByToken(launchpadInviteId)
        // Launchpad only: If results have been sent for a user, unexpire them
        _ <- removeExpiryStatus(testGroup.applicationId)
        _ <- phase3TestRepo.updateProgressStatus(testGroup.applicationId, ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED)
      } yield {
        AuditEvents.VideoInterviewResultsReceived(testGroup.applicationId) ::
          DataStoreEvents.VideoInterviewResultsReceived(testGroup.applicationId) ::
          Nil
      }
    }
  }

  def extendTestGroupExpiryTime(applicationId: String, extraDays: Int, actionTriggeredBy: String)
                               (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val progressFut = appRepository.findProgress(applicationId)
    val phase3TestGroup = phase3TestRepo.getTestGroup(applicationId)
      .map(tg => tg.getOrElse(throw new IllegalStateException(s"Phase 3 tests Expiration date for app id: '$applicationId' " +
        "cannot be extended. Test group not found.")))

    for {
      progress <- progressFut
      phase3 <- phase3TestGroup
      isAlreadyExpired = progress.phase3ProgressResponse.phase3TestsExpired
      extendDays = extendTime(isAlreadyExpired, phase3.expirationDate)
      newExpiryDate = extendDays(extraDays)
      activeTest = phase3.activeTest
      launchpadExtendRequest = ExtendDeadlineRequest(activeTest.interviewId, activeTest.candidateId, newExpiryDate.toLocalDate)
      _ <- launchpadGatewayClient.extendDeadline(launchpadExtendRequest)
      _ <- phase3TestRepo.updateGroupExpiryTime(applicationId, newExpiryDate, phase3TestRepo.phaseName)
      _ <- progressStatusesToRemoveWhenExtendTime(newExpiryDate, phase3, progress)
        .fold(Future.successful(()))(p => appRepository.removeProgressStatuses(applicationId, p))
      _ <- eventSink {
        AuditEvents.VideoInterviewExtended(
          "isAlreadyExpired" -> isAlreadyExpired.toString,
          "applicationId" -> applicationId) ::
          DataStoreEvents.VideoInterviewExtended(applicationId, actionTriggeredBy) :: Nil
      }
    } yield {}
  }

  private def registerApplicant(application: OnlineTestApplication, emailAddress: String,
                                customCandidateId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[String] = {
    val registerApplicant = RegisterApplicantRequest(emailAddress, customCandidateId, application.preferredName, application.lastName)
    launchpadGatewayClient.registerApplicant(registerApplicant).flatMap { registration =>
      eventSink {
        AuditEvents.VideoInterviewCandidateRegistered("userId" -> application.userId) ::
          DataStoreEvents.VideoInterviewCandidateRegistered(application.applicationId) :: Nil
      }.map(_ => registration.candidateId)
    }
  }

  private def inviteApplicant(application: OnlineTestApplication, interviewId: Int, candidateId: String)
                             (implicit hc: HeaderCarrier): Future[InviteApplicantResponse] = {

    val customInviteId = "FSINV-" + tokenFactory.generateUUID()

    val completionRedirectUrl = s"${gatewayConfig.phase3Tests.candidateCompletionRedirectUrl}/fset-fast-stream" +
      s"/online-tests/phase3/complete/$customInviteId"

    val inviteApplicant = InviteApplicantRequest(interviewId, candidateId, customInviteId, completionRedirectUrl)

    launchpadGatewayClient.inviteApplicant(inviteApplicant)
  }

  private def resetApplicant(application: OnlineTestApplication, interviewId: Int, candidateId: String, newDeadLine: LocalDate)
                            (implicit hc: HeaderCarrier): Future[ResetApplicantResponse] = {
    val resetApplicant = ResetApplicantRequest(interviewId, candidateId, newDeadLine)
    launchpadGatewayClient.resetApplicant(resetApplicant)
  }

  private def retakeApplicant(application: OnlineTestApplication, interviewId: Int, candidateId: String, newDeadLine: LocalDate)
                             (implicit hc: HeaderCarrier): Future[RetakeApplicantResponse] = {
    val retakeApplicant = RetakeApplicantRequest(interviewId, candidateId, newDeadLine)
    launchpadGatewayClient.retakeApplicant(retakeApplicant)
  }

  override def emailInviteToApplicant(application: OnlineTestApplication, emailAddress: String,
                                      invitationDate: DateTime, expirationDate: DateTime
                                     )(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val preferredName = application.preferredName
    emailClient.sendOnlineTestInvitation(emailAddress, preferredName, expirationDate).flatMap { _ =>
      eventSink {
        AuditEvents.VideoInterviewInvitationEmailSent(
          "userId" -> application.userId,
          "emailAddress" -> emailAddress
        ) ::
          DataStoreEvents.VideoInterviewInvitationEmailSent(
            application.applicationId
          ) :: Nil
      }
    }
  }

  private def mergePhase3TestGroups(oldTestGroupOpt: Option[Phase3TestGroup], newTestGroup: Phase3TestGroup): Phase3TestGroup = {
    oldTestGroupOpt match {
      case None =>
        newTestGroup
      case Some(oldTestGroup) =>
        newTestGroup.tests.headOption.map { newLaunchpadTest =>
          val mergedLaunchpadTests = oldTestGroup.tests.map { oldLaunchpadTest =>
            if (oldLaunchpadTest.interviewId == newLaunchpadTest.interviewId) {
              oldLaunchpadTest.copy(startedDateTime = None, completedDateTime = None, usedForResults = true)
            } else {
              oldLaunchpadTest.copy(usedForResults = false)
            }
          }
          if (mergedLaunchpadTests.filter(_.interviewId == newLaunchpadTest.interviewId).size > 0) {
            Phase3TestGroup(newTestGroup.expirationDate, mergedLaunchpadTests)
          } else {
            Phase3TestGroup(newTestGroup.expirationDate, mergedLaunchpadTests :+ newLaunchpadTest.copy(usedForResults = true))
          }
        }.getOrElse(oldTestGroup)
    }
  }

  // TODO: All resets are launchpad side, contemplate whether we should be able to call this invite method twice
  // or what we do if we want to reinvite
  private def markAsInvited(application: OnlineTestApplication)
                           (newPhase3TestGroup: Phase3TestGroup)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    for {
      currentPhase3TestGroup <- phase3TestRepo.getTestGroup(application.applicationId)
      updatedPhase3TestGroup = mergePhase3TestGroups(currentPhase3TestGroup, newPhase3TestGroup)
      _ <- phase3TestRepo.insertOrUpdateTestGroup(application.applicationId, updatedPhase3TestGroup)
      _ <- phase3TestRepo.resetTestProfileProgresses(application.applicationId, ResetPhase3Test.determineProgressStatusesToRemove)
      _ <- eventSink {
        AuditEvents.VideoInterviewInvited("userId" -> application.userId) ::
          DataStoreEvents.VideoInterviewInvited(application.applicationId) :: Nil
      }
    } yield {}
  }

  private def progressStatusesToRemoveWhenExtendTime(extendedExpiryDate: DateTime,
                                                     profile: Phase3TestGroup,
                                                     progress: ProgressResponse): Option[List[ProgressStatus]] = {
    val shouldRemoveExpired = progress.phase3ProgressResponse.phase3TestsExpired
    val today = dateTimeFactory.nowLocalTimeZone
    val shouldRemoveSecondReminder = extendedExpiryDate.minusHours(Phase3SecondReminder.hoursBeforeReminder).isAfter(today)
    val shouldRemoveFirstReminder = extendedExpiryDate.minusHours(Phase3FirstReminder.hoursBeforeReminder).isAfter(today)

    val progressStatusesToRemove = (Set.empty[ProgressStatus]
      ++ (if (shouldRemoveExpired) Set(PHASE3_TESTS_EXPIRED) else Set.empty)
      ++ (if (shouldRemoveSecondReminder) Set(PHASE3_TESTS_SECOND_REMINDER) else Set.empty)
      ++ (if (shouldRemoveFirstReminder) Set(PHASE3_TESTS_FIRST_REMINDER) else Set.empty)).toList

    if (progressStatusesToRemove.isEmpty) {
      None
    } else {
      Some(progressStatusesToRemove)
    }
  }

  private def candidateEmailAddress(application: OnlineTestApplication): Future[String] =
    cdRepository.find(application.userId).map(_.email)

  private def getInterviewIdForApplication(application: OnlineTestApplication): Int = {
    val time = (for {
      videoAdjustments <- application.videoInterviewAdjustments
      timeNeeded <- videoAdjustments.timeNeeded
    } yield timeNeeded).getOrElse(0)

    gatewayConfig.phase3Tests.interviewsByAdjustmentPercentage(s"${time}pc")
  }
}

object ResetPhase3Test {

  import ProgressStatuses._

  case class CannotResetPhase3Tests() extends NotFoundException

  def determineProgressStatusesToRemove: List[ProgressStatus] = {
    List(PHASE3_TESTS_EXPIRED, PHASE3_TESTS_STARTED, PHASE3_TESTS_FIRST_REMINDER, PHASE3_TESTS_SECOND_REMINDER,
      PHASE3_TESTS_COMPLETED, PHASE3_TESTS_RESULTS_RECEIVED, PHASE3_TESTS_FAILED, PHASE3_TESTS_FAILED_NOTIFIED, PHASE3_TESTS_PASSED,
      PHASE3_TESTS_PASSED_WITH_AMBER)
  }
}
