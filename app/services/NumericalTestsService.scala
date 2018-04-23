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
import model.NumericalTestCommands.NumericalTestApplication
import model.persisted.{ CubiksTest, NumericalTestGroup }
import play.api.mvc.RequestHeader
import repositories.sift.ApplicationSiftRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object NumericalTestsService extends NumericalTestsService {
  val applicationSiftRepo: ApplicationSiftRepository = repositories.applicationSiftRepository
  val cubiksGatewayClient = CubiksGatewayClient
  val gatewayConfig = cubiksGatewayConfig
  val tokenFactory = UUIDFactory
  val dateTimeFactory: DateTimeFactory = DateTimeFactory
}

trait NumericalTestsService {
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
    val scheduleCompletionBaseUrl = s"${gatewayConfig.candidateAppUrl}/fset-fast-stream/numerical-tests"
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

  def insertNumericalTestGroup(invitedApplicants: List[NumericalTestInviteData]): Future[Unit] = {
    val invitedApplicantsOps = invitedApplicants.map { invite =>
      val testGroup = NumericalTestGroup(
        tests = List(
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
      )
      insertOrUpdateTestGroup(invite.application)
    }
    Future.sequence(invitedApplicantsOps).map(_ => ())
  }

  private def insertOrUpdateTestGroup(application: NumericalTestApplication): Future[Unit] = {
    for {
      currentTestProfileOpt <- applicationSiftRepo.getNumericalTestsGroup(application.applicationId)
      updatedTestGroup <- Future.successful(currentTestProfileOpt) // TODO: Do an insertOrAppend() operation here?
      //TODO: Do we need to reset "test profile progresses" ??
    } yield ()
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
          _ <- insertNumericalTestGroup(invitedApplicants)
        } yield ()
    }
  }
}
