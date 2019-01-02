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

package services.onlinetesting.phase3

import _root_.services.AuditService
import common.Phase3TestConcern
import config.LaunchpadGatewayConfig
import connectors._
import connectors.launchpadgateway.LaunchpadGatewayClient
import connectors.launchpadgateway.exchangeobjects.out._
import factories.{ DateTimeFactory, UUIDFactory }
import model.Exceptions.NotFoundException
import model.OnlineTestCommands._
import model.ProgressStatuses._
import model._
import model.command.ProgressResponse
import model.stc.StcEventTypes.StcEventType
import model.stc.{ AuditEvents, DataStoreEvents }
import model.exchange.Phase3TestGroupWithActiveTest
import model.persisted.phase3tests.{ LaunchpadTest, LaunchpadTestCallbacks, Phase3TestGroup }
import model.persisted.{ NotificationExpiringOnlineTest, Phase3TestGroupWithAppId }
import org.joda.time.{ DateTime, LocalDate }
import play.api.mvc.RequestHeader
import repositories._
import repositories.onlinetesting.Phase3TestRepository
import services.stc.StcEventService
import services.onlinetesting.Exceptions.NoActiveTestException
import services.onlinetesting.OnlineTestService
import services.onlinetesting.phase3.ResetPhase3Test.CannotResetPhase3Tests
import services.sift.ApplicationSiftService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps
import uk.gov.hmrc.http.HeaderCarrier

object Phase3TestService extends Phase3TestService {

  import config.MicroserviceAppConfig._

  val appRepository = applicationRepository
  val testRepository = phase3TestRepository
  val cdRepository = faststreamContactDetailsRepository
  val launchpadGatewayClient = LaunchpadGatewayClient
  val tokenFactory = UUIDFactory
  val dateTimeFactory = DateTimeFactory
  val emailClient = Phase3OnlineTestEmailClient
  val auditService = AuditService
  val gatewayConfig = launchpadGatewayConfig
  val eventService = StcEventService
  val siftService = ApplicationSiftService
}

//scalastyle:off number.of.methods
trait Phase3TestService extends OnlineTestService with Phase3TestConcern {
  type TestRepository = Phase3TestRepository

  val launchpadGatewayClient: LaunchpadGatewayClient
  val gatewayConfig: LaunchpadGatewayConfig

  override def nextApplicationsReadyForOnlineTesting(maxBatchSize: Int): Future[List[OnlineTestApplication]] = {
    testRepository.nextApplicationsReadyForOnlineTesting(maxBatchSize: Int)
  }

  def getTestGroup(applicationId: String): Future[Option[Phase3TestGroup]] = testRepository.getTestGroup(applicationId)

  def getTestGroupWithActiveTest(applicationId: String): Future[Option[Phase3TestGroupWithActiveTest]] = {
    for {
      phase3Opt <- testRepository.getTestGroup(applicationId)
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
    testRepository.removeTestGroup(applicationId).map { _ =>
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


  override def processNextTestForReminder(reminder: model.ReminderNotice)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] =
    testRepository.nextTestForReminder(reminder).flatMap {
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
                (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    import ApplicationStatus._

    ApplicationStatus.withName(application.applicationStatus) match {
      case PHASE3_TESTS | PHASE3_TESTS_PASSED | PHASE3_TESTS_FAILED | PHASE3_TESTS_PASSED_WITH_AMBER =>
        testRepository.getTestGroup(application.applicationId).flatMap { phase3TestGroup =>
          registerAndInviteForTestGroup(application, phase3TestGroup).map { _ =>
            AuditEvents.VideoInterviewRescheduled(Map("userId" -> application.userId, "tests" -> "video-interview")) ::
              DataStoreEvents.VideoInterviewRescheduled(application.applicationId, actionTriggeredBy) :: Nil
          }
        }
      case _ => throw CannotResetPhase3Tests()
    }
  }

  // scalastyle:off method.length
  private[onlinetesting] def registerAndInviteForTestGroup(application: OnlineTestApplication,
                                                           interviewId: Int,
                                                           phase3TestGroup: Option[Phase3TestGroup])
                                                          (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val daysUntilExpiry = gatewayConfig.phase3Tests.timeToExpireInDays

    val expirationDate = phase3TestGroup.map { phase3TG =>
      if (phase3TG.expirationDate.isAfterNow) {
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

    def extendIfInvigilatedOrIfAdjustmentsWereChanged(application: OnlineTestApplication): Future[Unit] = {
      val couldHaveBeenInvigilatedBefore = phase3TestGroup
        .exists(_.expirationDate.isAfter(dateTimeFactory.nowLocalTimeZone.plusDays(daysUntilExpiry * 2)))

      if (couldHaveBeenInvigilatedBefore && !application.isInvigilatedVideo) {
        extendTestGroupExpiryTime(
          application.applicationId,
          -gatewayConfig.phase3Tests.invigilatedTimeToExpireInDays + daysUntilExpiry,
          "NonInvigilatedInviteSystem"
        )
      } else if (!couldHaveBeenInvigilatedBefore && application.isInvigilatedVideo) {
        extendTestGroupExpiryTime(
          application.applicationId,
          gatewayConfig.phase3Tests.invigilatedTimeToExpireInDays - daysUntilExpiry,
          "InvigilatedInviteSystem")
      } else {
        Future.successful(())
      }
    }

    for {
      emailAddress <- candidateEmailAddress(application)
      launchPadTest <- registerAndInviteOrInviteOrResetOrRetakeOrNothing(
        phase3TestGroup, application, emailAddress, interviewId, invitationDate, expirationDate)
      _ <- emailProcess(emailAddress)
      _ <- markAsInvited(application)(Phase3TestGroup(expirationDate = expirationDate, tests = List(launchPadTest)))
      _ <- extendIfInvigilatedOrIfAdjustmentsWereChanged(application)
      _ <- eventSink {
        AuditEvents.VideoInterviewRegistrationAndInviteComplete("userId" -> application.userId) ::
          DataStoreEvents.VideoInterviewRegistrationAndInviteComplete(application.applicationId) :: Nil
      }
    } yield {}
  }

  // scalastyle:on method.length

  //scalastyle:off method.length
  private[onlinetesting] def registerAndInviteOrInviteOrResetOrRetakeOrNothing(phase3TestGroup: Option[Phase3TestGroup],
    application: OnlineTestApplication, emailAddress: String,
    interviewId: Int, invitationDate: DateTime,
    expirationDate: DateTime
  )(implicit hc: HeaderCarrier, rh: RequestHeader): Future[LaunchpadTest] = {

    case class InviteResetOrTakeResponse(candidateId: String, testUrl: String, customInviteId: String, customCandidateId: Option[String])

    def inviteOrResetOrRetake: Future[InviteResetOrTakeResponse] = {
      val customCandidateId = "FSCND-" + tokenFactory.generateUUID()
      val getInitialCustomCandidateId = phase3TestGroup.map(_.activeTests.head.customCandidateId)
      phase3TestGroup.map {
        phase3TestGroupContent =>
          val candidateId = phase3TestGroupContent.tests.head.candidateId
          phase3TestGroupContent.tests.find(_.interviewId == interviewId).map {
            launchpadTest =>
              if (launchpadTest.startedDateTime.isDefined && launchpadTest.completedDateTime.isDefined) {
                retakeApplicant(application, interviewId, candidateId, expirationDate.toLocalDate).map {
                  retakeResponse =>
                    InviteResetOrTakeResponse(candidateId, retakeResponse.testUrl, retakeResponse.customInviteId, getInitialCustomCandidateId)
                }
              } else if (launchpadTest.startedDateTime.isDefined && launchpadTest.completedDateTime.isEmpty) {
                resetApplicant(application, interviewId, candidateId, phase3TestGroupContent.expirationDate.toLocalDate).map {
                  resetResponse =>
                    InviteResetOrTakeResponse(candidateId, resetResponse.testUrl, resetResponse.customInviteId, getInitialCustomCandidateId)
                }
              } else {
                Future.successful(
                  InviteResetOrTakeResponse(candidateId, launchpadTest.testUrl, launchpadTest.token, getInitialCustomCandidateId))
              }
          }.getOrElse(inviteApplicant(application, interviewId, phase3TestGroupContent.tests.head.candidateId).map {
            inviteResponse =>
              InviteResetOrTakeResponse(candidateId, inviteResponse.testUrl,
                inviteResponse.customInviteId, Some(inviteResponse.customCandidateId))
          })
      }.getOrElse(
        registerApplicant(application, emailAddress, customCandidateId).flatMap {
          candidateId =>
            inviteApplicant(application, interviewId, candidateId).map {
              inviteResponse =>
                InviteResetOrTakeResponse(candidateId, inviteResponse.testUrl, inviteResponse.customInviteId,
                  Some(inviteResponse.customCandidateId)
                )
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

  def markAsStarted(launchpadInviteId: String, startedTime: DateTime = dateTimeFactory.nowLocalTimeZone)
                   (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    for {
      _ <- testRepository.updateTestStartTime(launchpadInviteId, startedTime)
      updated <- testRepository.getTestGroupByToken(launchpadInviteId)
      _ <- testRepository.updateProgressStatus(updated.applicationId, ProgressStatuses.PHASE3_TESTS_STARTED)
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
      testRepository.getTestGroupByToken(launchpadInviteId).flatMap {
        test =>
          val launchpadTest = test.testGroup.tests.find(_.token == launchpadInviteId).get
          if (launchpadTest.completedDateTime.isEmpty && launchpadTest.startedDateTime.isDefined) {
            for {
              _ <- testRepository.updateTestCompletionTime(launchpadInviteId, dateTimeFactory.nowLocalTimeZone)
              updated <- testRepository.getTestGroupByToken(launchpadInviteId)
              _ <- testRepository.updateProgressStatus(updated.applicationId, ProgressStatuses.PHASE3_TESTS_COMPLETED)
            } yield {
              AuditEvents.VideoInterviewCompleted(updated.applicationId) ::
                DataStoreEvents.VideoInterviewCompleted(updated.applicationId) ::
                Nil
            }
          } else {
            Future.successful(List[StcEventType]())
          }
      }
    }
  }

  def addResetEventMayBe(launchpadInviteId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    for {
      testGroup <- testRepository.getTestGroupByToken(launchpadInviteId)
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
    testRepository.getTestGroupByToken(launchpadInviteId).flatMap {
      _ =>
        for {
          testGroup <- testRepository.getTestGroupByToken(launchpadInviteId)
          _ <- testRepository.updateProgressStatus(testGroup.applicationId, ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED)
        } yield {
          AuditEvents.VideoInterviewResultsReceived(testGroup.applicationId) ::
            DataStoreEvents.VideoInterviewResultsReceived(testGroup.applicationId) ::
            Nil
        }
    }
  }

  def removeExpiredStatus(applicationId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    for {
      _ <- appRepository.removeProgressStatuses(applicationId, List(ProgressStatuses.PHASE3_TESTS_EXPIRED))
    } yield {
      AuditEvents.VideoInterviewUnexpired(
        "applicationId" -> applicationId
      ) :: Nil
    }
  }

  def extendTestGroupExpiryTime(applicationId: String, extraDays: Int, actionTriggeredBy: String)
                               (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val progressFut = appRepository.findProgress(applicationId)
    val phase3TestGroup = testRepository.getTestGroup(applicationId)
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
      _ <- testRepository.updateGroupExpiryTime(applicationId, newExpiryDate, testRepository.phaseName)
      _ <- progressStatusesToRemoveWhenExtendTime(newExpiryDate, phase3, progress)
        .fold(Future.successful(()))(p => appRepository.removeProgressStatuses(applicationId, p))
      _ <- eventSink {
        AuditEvents.VideoInterviewExtended(
          "isAlreadyExpired" -> isAlreadyExpired.toString,
          "applicationId" -> applicationId) ::
          DataStoreEvents.VideoInterviewExtended(applicationId, actionTriggeredBy) :: Nil
      }
    } yield {
    }
  }

  private def registerApplicant(application: OnlineTestApplication, emailAddress: String,
                                customCandidateId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[String] = {
    val registerApplicant = RegisterApplicantRequest(emailAddress, customCandidateId, application.preferredName, application.lastName)
    launchpadGatewayClient.registerApplicant(registerApplicant).flatMap {
      registration =>
        eventSink {
          AuditEvents.VideoInterviewCandidateRegistered("userId" -> application.userId) ::
            DataStoreEvents.VideoInterviewCandidateRegistered(application.applicationId) :: Nil
        }.map(_ => registration.candidateId)
    }
  }

  private def inviteApplicant(application: OnlineTestApplication, interviewId: Int, candidateId: String)
                             (implicit hc: HeaderCarrier): Future[InviteApplicantResponse] = {

    val customInviteId = "FSINV-" + tokenFactory.generateUUID()

    val completionRedirectUrl = s"${
      gatewayConfig.phase3Tests.candidateCompletionRedirectUrl
    }/fset-fast-stream" +
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
    emailClient.sendOnlineTestInvitation(emailAddress, preferredName, expirationDate).flatMap {
      _ =>
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
        newTestGroup.tests.headOption.map {
          newLaunchpadTest =>
            val mergedLaunchpadTests = oldTestGroup.tests.map {
              oldLaunchpadTest =>
                if (oldLaunchpadTest.interviewId == newLaunchpadTest.interviewId) {
                  oldLaunchpadTest.copy(startedDateTime = None, completedDateTime = None, usedForResults = true)
                } else {
                  oldLaunchpadTest.copy(usedForResults = false)
                }
            }
            if (mergedLaunchpadTests.exists(_.interviewId == newLaunchpadTest.interviewId)) {
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
      currentPhase3TestGroup <- testRepository.getTestGroup(application.applicationId)
      updatedPhase3TestGroup = mergePhase3TestGroups(currentPhase3TestGroup, newPhase3TestGroup)
      _ <- testRepository.insertOrUpdateTestGroup(application.applicationId, updatedPhase3TestGroup)
      _ <- testRepository.resetTestProfileProgresses(application.applicationId, ResetPhase3Test.determineProgressStatusesToRemove)
      _ <- eventSink {
        AuditEvents.VideoInterviewInvited("userId" -> application.userId) ::
          DataStoreEvents.VideoInterviewInvited(application.applicationId) :: Nil
      }
    } yield {
    }
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

    gatewayConfig.phase3Tests.interviewsByAdjustmentPercentage(s"${
      time
    }pc")
  }
}

object ResetPhase3Test {

  import ProgressStatuses._

  case class CannotResetPhase3Tests() extends NotFoundException

  def determineProgressStatusesToRemove: List[ProgressStatus] = {
    List(PHASE3_TESTS_EXPIRED, PHASE3_TESTS_STARTED, PHASE3_TESTS_FIRST_REMINDER, PHASE3_TESTS_SECOND_REMINDER,
      PHASE3_TESTS_COMPLETED, PHASE3_TESTS_RESULTS_RECEIVED, PHASE3_TESTS_FAILED, PHASE3_TESTS_FAILED_NOTIFIED, PHASE3_TESTS_PASSED,
      PHASE3_TESTS_PASSED_WITH_AMBER, PHASE3_TESTS_FAILED_SDIP_AMBER)
  }
}
