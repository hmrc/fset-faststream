/*
 * Copyright 2018 HM Revenue & Customs
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

package services

import config.MicroserviceAppConfig.cubiksGatewayConfig
import config.{ CubiksGatewayConfig, NumericalTestSchedule, NumericalTestsConfig }
import connectors.CubiksGatewayClient
import connectors.ExchangeObjects.{ Invitation, InviteApplicant, Registration }
import factories.{ DateTimeFactory, UUIDFactory }
import model.Exceptions.UnexpectedException
import model.NumericalTestCommands.NumericalTestApplication
import model.ProgressStatuses.{ ProgressStatus, SIFT_TEST_COMPLETED, SIFT_TEST_INVITED }
import model.persisted.CubiksTest
import model.persisted.sift.SiftTestGroup
import play.api.Logger
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.sift.ApplicationSiftRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object NumericalTestsService extends NumericalTestsService {
  val applicationRepo: GeneralApplicationRepository = repositories.applicationRepository
  val applicationSiftRepo: ApplicationSiftRepository = repositories.applicationSiftRepository
  val cubiksGatewayClient = CubiksGatewayClient
  val gatewayConfig = cubiksGatewayConfig
  val tokenFactory = UUIDFactory
  val dateTimeFactory: DateTimeFactory = DateTimeFactory
}

trait NumericalTestsService {
  def applicationRepo: GeneralApplicationRepository
  def applicationSiftRepo: ApplicationSiftRepository
  val tokenFactory: UUIDFactory
  val gatewayConfig: CubiksGatewayConfig
  def testConfig: NumericalTestsConfig = gatewayConfig.numericalTests
  val cubiksGatewayClient: CubiksGatewayClient
  val dateTimeFactory: DateTimeFactory

  case class NumericalTestInviteData(application: NumericalTestApplication,
                                     scheduleId: Int,
                                     token: String,
                                     registration: Registration,
                                     invitation: Invitation)

  def registerAndInviteForTests(applications: List[NumericalTestApplication])
                               (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val schedule = testConfig.schedules("sample") //TODO: Update this schedule
      registerAndInvite(applications, schedule).map(_ => ())
  }

  def registerApplicants(candidates: Seq[NumericalTestApplication], tokens: Seq[String])
                        (implicit hc: HeaderCarrier): Future[Map[Int, (NumericalTestApplication, String, Registration)]] = {
    cubiksGatewayClient.registerApplicants(candidates.size).map(_.zipWithIndex.map{
      case (registration, idx) =>
        val candidate = candidates(idx)
        (registration.userId, (candidate, tokens(idx), registration))
    }.toMap)
  }

  def inviteApplicants(candidateData: Map[Int, (NumericalTestApplication, String, Registration)],
                       schedule: NumericalTestSchedule)(implicit hc: HeaderCarrier): Future[List[NumericalTestInviteData]] = {
    val scheduleCompletionBaseUrl = s"${gatewayConfig.candidateAppUrl}/fset-fast-stream/sift-test"
    val invites = candidateData.values.map {
      case (_, token, registration) =>
        val completionUrl = s"$scheduleCompletionBaseUrl/complete/$token"
        InviteApplicant(schedule.scheduleId, registration.userId, completionUrl) // TODO: handle time adjustments
    }.toList

    cubiksGatewayClient.inviteApplicants(invites).map(_.map { invitation =>
      val (application, token, registration) = candidateData(invitation.userId)
      NumericalTestInviteData(application, schedule.scheduleId, token, registration, invitation)
    })
  }

  def insertNumericalTest(invitedApplicants: List[NumericalTestInviteData]): Future[Unit] = {
    val invitedApplicantsOps = invitedApplicants.map { invite =>
      val tests = List(
        CubiksTest(
          scheduleId = invite.scheduleId,
          usedForResults = true,
          cubiksUserId = invite.registration.userId,
          token = invite.token,
          testUrl = invite.invitation.authenticateUrl,
          invitationDate = dateTimeFactory.nowLocalTimeZone,
          participantScheduleId = invite.invitation.participantScheduleId
        )
      )
      upsertTests(invite.application, tests)
    }
    Future.sequence(invitedApplicantsOps).map(_ => ())
  }

  private def upsertTests(application: NumericalTestApplication, newTests: List[CubiksTest]): Future[Unit] = {

    def upsert(applicationId: String, currentTestGroup: Option[SiftTestGroup], newTests: List[CubiksTest]) = {
      currentTestGroup match {
        case Some(testGroup) if testGroup.tests.isEmpty =>
          applicationSiftRepo.insertNumericalTests(applicationId, newTests)
        case Some(testGroup) if testGroup.tests.isDefined =>
          // TODO: Test may have been reset, change the active test here
          throw new NotImplementedError("Test may have been reset, change the active test here")
        case None =>
          throw UnexpectedException(s"Application ${application.applicationId} should have a SIFT_PHASE testGroup at this point")
      }
    }

    for {
      currentTestGroupOpt <- applicationSiftRepo.getTestGroup(application.applicationId)
      updatedTestGroup <- upsert(application.applicationId, currentTestGroupOpt, newTests)
      //TODO: Reset "test profile progresses" while resetting tests?
    } yield ()
  }


  def updateProgressStatuses(applicationIds: List[String], progressStatus: ProgressStatus): Future[Unit] = {
    Future.sequence(
      applicationIds.map(id => applicationRepo.addProgressStatusAndUpdateAppStatus(id, progressStatus))
    ).map(_ => ())
  }

  private def registerAndInvite(applications: List[NumericalTestApplication], schedule: NumericalTestSchedule)
                               (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    applications match {
      case Nil => Future.successful(Nil)
      case candidates =>
        val tokens = (1 to candidates.size).map(_ => tokenFactory.generateUUID())
        for {
          registeredApplicants <- registerApplicants(candidates, tokens)
          invitedApplicants <- inviteApplicants(registeredApplicants, schedule)
          _ <- insertNumericalTest(invitedApplicants)
          _ <- updateProgressStatuses(invitedApplicants.map(_.application.applicationId), SIFT_TEST_INVITED)
        } yield ()
    }
  }

  def markAsCompleted(cubiksUserId: Int)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    applicationSiftRepo.updateTestCompletionTime(cubiksUserId, dateTimeFactory.nowLocalTimeZone).flatMap { _ =>
      applicationSiftRepo.getTestGroupByCubiksId(cubiksUserId).map { updatedTestGroup =>
        val appId = updatedTestGroup.applicationId
        require(updatedTestGroup.tests.isDefined, s"No numerical tests exists for application: $appId")
        val tests = updatedTestGroup.tests.get
        require(tests.exists(_.usedForResults), "Active tests cannot be found")

        val activeCompletedTests = tests.forall(_.completedDateTime.isDefined)
        if(activeCompletedTests) {
          applicationRepo.addProgressStatusAndUpdateAppStatus(appId, SIFT_TEST_COMPLETED)
        } else {
          Logger.info(s"No tests to mark as completed for cubiksId: $cubiksUserId and applicationId: $appId")
          Future.successful(())
        }
      }
    }
  }

  def markAsCompleted(token: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    applicationSiftRepo.getTestGroupByToken(token).flatMap { testGroup =>
      val tests = testGroup.tests
        .getOrElse(throw UnexpectedException(s"Numerical test with token($token) not found for appId: ${testGroup.applicationId}"))
      tests.find(_.token == token)
        .map(test => markAsCompleted(test.cubiksUserId))
        .getOrElse(Future.successful(()))
    }
  }
}
