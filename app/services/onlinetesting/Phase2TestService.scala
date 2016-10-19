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
import config.{ CubiksGatewayConfig, Phase2Schedule, Phase2TestsConfig }
import connectors.ExchangeObjects._
import connectors.{ CubiksGatewayClient, Phase2OnlineTestEmailClient }
import factories.{ DateTimeFactory, UUIDFactory }
import model.Exceptions.NotFoundException
import model.OnlineTestCommands._
import model.{ ProgressStatuses, ReminderNotice }
import model.events.DataStoreEvents
import model.ProgressStatuses
import model.events.EventTypes.EventType
import model.events.{ AuditEvents, DataStoreEvents }
import model.exchange.{ CubiksTestResultReady, Phase2TestGroupWithNames }
import model.persisted.{ CubiksTest, NotificationExpiringOnlineTest, Phase2TestGroup, Phase2TestGroupWithAppId }
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

}

trait Phase2TestService extends OnlineTestService with ScheduleSelector {

  val phase2TestRepo: Phase2TestRepository
  val cubiksGatewayClient: CubiksGatewayClient
  val gatewayConfig: CubiksGatewayConfig


  def testConfig: Phase2TestsConfig = gatewayConfig.phase2Tests

  case class Phase2TestInviteData(application: OnlineTestApplication,
                                  scheduleId: Int,
                                  token: String,
                                  registration: Registration,
                                  invitation: Invitation)

  def getTestProfile(applicationId: String): Future[Option[Phase2TestGroupWithNames]] = {
    for {
      phase2Opt <- phase2TestRepo.getTestGroup(applicationId)
    } yield phase2Opt.map { phase2 =>
        val tests = phase2.activeTests
        Phase2TestGroupWithNames(
          phase2.expirationDate,
          tests
        )
    }
  }

  override def nextApplicationReadyForOnlineTesting: Future[List[OnlineTestApplication]] = {
    phase2TestRepo.nextApplicationsReadyForOnlineTesting
  }

  override def processNextTestForReminder(reminder: ReminderNotice)(implicit hc: HeaderCarrier): Future[Unit] = {
    phase2TestRepo.nextTestForReminder(reminder).flatMap {
      case Some(expiringTest) => processReminder(expiringTest, reminder)
      case None => Future.successful(())
    }
  }

  override def emailCandidateForExpiringTestReminder(expiringTest: NotificationExpiringOnlineTest,
                                                     emailAddress: String,
                                                     reminder: ReminderNotice)(implicit hc: HeaderCarrier): Future[Unit] = {
    emailClient.sendTestExpiringReminder(emailAddress, expiringTest.preferredName,
      reminder.hoursBeforeReminder, reminder.timeUnit, expiringTest.expiryDate).map { _ =>
      audit(s"ReminderPhase2ExpiringOnlineTestNotificationBefore${reminder.hoursBeforeReminder}HoursEmailed",
        expiringTest.userId, Some(emailAddress))
    }
  }

  def resetTests(application: OnlineTestApplication, actionTriggeredBy: String)
                (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    phase2TestRepo.getTestGroup(application.applicationId).flatMap {
       case Some(phase2TestGroup) if schedulesAvailable(phase2TestGroup.tests.map(_.scheduleId)) =>
         val schedule = getRandomSchedule(phase2TestGroup.tests.map(_.scheduleId))
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

  def registerApplicants(candidates: List[OnlineTestApplication], tokens: Seq[String])
    (implicit hc: HeaderCarrier): Future[Map[Int, (OnlineTestApplication, String, Registration)]] = {
    cubiksGatewayClient.registerApplicants(candidates.size).map( _.zipWithIndex.map { case (registration, idx) =>
      val candidate = candidates(idx)
      audit("Phase2TestRegistered", candidate.userId)
      (registration.userId, (candidate, tokens(idx), registration))
    }.toMap)
  }

  def inviteApplicants(candidateData: Map[Int, (OnlineTestApplication, String, Registration)],
                       schedule: Phase2Schedule = getRandomSchedule())
    (implicit hc: HeaderCarrier): Future[List[Phase2TestInviteData]] = {
    val invites = candidateData.values.map { case (application, token, registration) =>
      buildInviteApplication(application, token, registration.userId, schedule)
    }.toList

    cubiksGatewayClient.inviteApplicants(invites).map(_.map { invitation =>
      val (application, token, registration) = candidateData(invitation.userId)
      audit("Phase2TestInvited", application.userId)
      Phase2TestInviteData(application, schedule.scheduleId, token, registration, invitation)
    })
  }

  override def registerAndInviteForTestGroup(applications: List[OnlineTestApplication])
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] =
    registerAndInviteForTestGroup(applications, getRandomSchedule()).map { candidatesToProgress =>
      eventSink {
        Future.successful(
          candidatesToProgress.map(candidate => {
            audit("Phase2TestInvitationProcessComplete", candidate.userId)
            DataStoreEvents.OnlineExerciseResultSent(candidate.applicationId)
          })
        )
      }
    }

  private def registerAndInviteForTestGroup(applications: List[OnlineTestApplication], schedule: Phase2Schedule)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[List[OnlineTestApplication]] = filterCandidates(applications) match {
    case Nil => Future.successful(Nil)
    case candidatesToProcess =>
      val tokens = for (i <- 1 to candidatesToProcess.size) yield tokenFactory.generateUUID()
      implicit val (invitationDate, expirationDate) = calcOnlineTestDates(gatewayConfig.phase2Tests.expiryTimeInDays)

      for {
        registeredApplicants <- registerApplicants(candidatesToProcess, tokens)
        invitedApplicants <- inviteApplicants(registeredApplicants, schedule)
        _ <- insertPhase2TestGroups(invitedApplicants)(invitationDate, expirationDate)
        _ <- emailInviteToApplicants(candidatesToProcess)(hc, invitationDate, expirationDate)
      } yield {
        candidatesToProcess
      }
  }

  def buildInviteApplication(application: OnlineTestApplication, token: String, userId: Int, schedule: Phase2Schedule) = {
    val scheduleCompletionBaseUrl = s"${gatewayConfig.candidateAppUrl}/fset-fast-stream/online-tests/phase2"

    InviteApplicant(schedule.scheduleId,
      userId,
      s"$scheduleCompletionBaseUrl/complete/$token",
      resultsURL = None,
      timeAdjustments = buildTimeAdjustments(application.needsAdjustments, schedule.assessmentId)
    )
  }

  private def insertPhase2TestGroups(o: List[Phase2TestInviteData])
    (implicit invitationDate: DateTime, expirationDate: DateTime): Future[Unit] = Future.sequence(o.map { completedInvite =>
    val newTestGroup = Phase2TestGroup(expirationDate = expirationDate,
      List(CubiksTest(scheduleId = completedInvite.scheduleId,
        usedForResults = true,
        cubiksUserId = completedInvite.registration.userId,
        token = completedInvite.token,
        testUrl = completedInvite.invitation.authenticateUrl,
        invitationDate = invitationDate,
        participantScheduleId = completedInvite.invitation.participantScheduleId
      ))
    )
    insertOrUpdateTestGroup(completedInvite.application)(newTestGroup)
  }).map( _ => () )

  private def insertOrUpdateTestGroup(application: OnlineTestApplication)
                           (newPhase2TestGroup: Phase2TestGroup): Future[Unit] = for {
    currentPhase2TestGroup <- phase2TestRepo.getTestGroup(application.applicationId)
    updatedPhase2TestGroup = merge(currentPhase2TestGroup, newPhase2TestGroup)
    _ <- phase2TestRepo.insertOrUpdateTestGroup(application.applicationId, updatedPhase2TestGroup)
    _ <- phase2TestRepo.removeTestProfileProgresses(application.applicationId, determineStatusesToRemove(updatedPhase2TestGroup))
  } yield ()

  private def merge(currentProfile: Option[Phase2TestGroup], newProfile: Phase2TestGroup): Phase2TestGroup = currentProfile match {
    case None => newProfile
    case Some(profile) =>
      val existingTestsAfterUpdate = profile.tests.map(t =>
          t.copy(usedForResults = false)
      )
      Phase2TestGroup(newProfile.expirationDate, existingTestsAfterUpdate ++ newProfile.tests)
  }

  def markAsStarted(cubiksUserId: Int, startedTime: DateTime = dateTimeFactory.nowLocalTimeZone)
                   (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit]= eventSink {
    val updatedPhase2Test = updatePhase2Test(cubiksUserId, t => t.copy(startedDateTime = Some(startedTime)))
    updatedPhase2Test flatMap { u =>
      phase2TestRepo.updateProgressStatus(u.applicationId, ProgressStatuses.PHASE2_TESTS_STARTED) map { _ =>
        DataStoreEvents.ETrayStarted(u.applicationId) :: Nil
      }
    }
  }

  def markAsCompleted(cubiksUserId: Int)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    val updatedPhase2Test = updatePhase2Test(cubiksUserId, t => t.copy(completedDateTime = Some(dateTimeFactory.nowLocalTimeZone)))
    updatedPhase2Test flatMap { u =>
      require(u.phase2TestGroup.activeTests.nonEmpty, "Active tests cannot be found")
      val activeTestsCompleted = u.phase2TestGroup.activeTests forall (_.completedDateTime.isDefined)
      activeTestsCompleted match {
        case true =>
          phase2TestRepo.updateProgressStatus(u.applicationId, ProgressStatuses.PHASE2_TESTS_COMPLETED) map { _ =>
            DataStoreEvents.ETrayCompleted(u.applicationId) :: Nil
          }
        case false =>
          Future.successful(List.empty[EventType])
      }
    }
  }

  def markAsCompleted(token: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    phase2TestRepo.getTestProfileByToken(token).flatMap { p =>
      p.tests.find(_.token == token).map {test => markAsCompleted(test.cubiksUserId)}
        .getOrElse(Future.successful(()))
    }
  }

  def markAsReportReadyToDownload(cubiksUserId: Int, reportReady: CubiksTestResultReady): Future[Unit] = {
    updatePhase2Test(cubiksUserId, updateTestReportReady(_: CubiksTest, reportReady)).flatMap { updated =>
      if (updated.phase2TestGroup.activeTests forall (_.resultsReadyToDownload)) {
        phase2TestRepo.updateProgressStatus(updated.applicationId, ProgressStatuses.PHASE2_TESTS_RESULTS_READY)
      } else {
        Future.successful(())
      }
    }
  }

  private def updatePhase2Test(cubiksUserId: Int, update: CubiksTest => CubiksTest): Future[Phase2TestGroupWithAppId] = {
    def createUpdateTestGroup(p: Phase2TestGroupWithAppId): Phase2TestGroupWithAppId = {
      val testGroup = p.phase2TestGroup
      assertUniqueTestByCubiksUserId(testGroup.tests, cubiksUserId)
      val updatedTestGroup = testGroup.copy(tests = updateCubiksTestsById(cubiksUserId, testGroup.tests, update))
      Phase2TestGroupWithAppId(p.applicationId, updatedTestGroup)
    }
    for {
      p1TestProfile <- phase2TestRepo.getTestProfileByCubiksId(cubiksUserId)
      updated = createUpdateTestGroup(p1TestProfile)
      _ <- phase2TestRepo.insertOrUpdateTestGroup(updated.applicationId, updated.phase2TestGroup)
    } yield {
      updated
    }
  }

  //TODO Once the time adjustments ticket has been done then this should be updated to apply the etray adjustment settings.
  def buildTimeAdjustments(needsAdjustment: Boolean, assessmentId: Int) = if (needsAdjustment) {
    List(TimeAdjustments(assessmentId, sectionId = 1, absoluteTime = 100))
  } else {
    Nil
  }

  private def filterCandidates(candidates: List[OnlineTestApplication]): List[OnlineTestApplication] =
    candidates.find(_.needsAdjustments) match {
      case Some(candidate) => Nil // TODO build time adjustments here
      case None => candidates
  }

  def emailInviteToApplicants(candidates: List[OnlineTestApplication])
    (implicit hc: HeaderCarrier, invitationDate: DateTime, expirationDate: DateTime): Future[Unit] =
  Future.sequence(candidates.map { candidate =>
    candidateEmailAddress(candidate.userId).flatMap(emailInviteToApplicant(candidate, _ , invitationDate, expirationDate))
  }).map( _ => () )


  private def candidateEmailAddress(userId: String): Future[String] = cdRepository.find(userId).map(_.email)

}

object ResetPhase2Test {

  import ProgressStatuses._

  case class CannotResetPhase2Tests() extends NotFoundException

  case class ResetLimitExceededException() extends Exception

  def determineStatusesToRemove(testGroup: Phase2TestGroup): List[ProgressStatus] = {
      (if (testGroup.hasNotStartedYet) List(PHASE2_TESTS_STARTED) else List()) ++
      (if (testGroup.hasNotCompletedYet) List(PHASE2_TESTS_COMPLETED) else List()) ++
      (if (testGroup.hasNotResultReadyToDownloadForAllTestsYet) List(PHASE2_TESTS_RESULTS_RECEIVED, PHASE2_TESTS_RESULTS_READY) else List())
  }
}

