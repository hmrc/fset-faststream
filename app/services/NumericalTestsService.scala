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
import factories.UUIDFactory
import model.NumericalTestCommands.NumericalTestApplication
import play.api.mvc.RequestHeader
import repositories.application.{ GeneralApplicationMongoRepository, GeneralApplicationRepository }
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object NumericalTestsService extends NumericalTestsService {
  val applicationRepo: GeneralApplicationMongoRepository = repositories.applicationRepository
  val cubiksGatewayClient = CubiksGatewayClient
  val gatewayConfig = cubiksGatewayConfig
  val tokenFactory = UUIDFactory
}

trait NumericalTestsService {
  def applicationRepo: GeneralApplicationRepository
  val tokenFactory: UUIDFactory
  val gatewayConfig: CubiksGatewayConfig
  def testConfig: NumericalTestsConfig = gatewayConfig.numericalTests

  def registerAndInviteForTests(applications: List[NumericalTestApplication])
                               (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val schedule = testConfig.schedules("sample") //TODO: Update this schedule
    registerAndInvite(applications, schedule).map(_ => ())
  }

  private def registerAndInvite(applications: List[NumericalTestApplication], schedule: NumericalTestSchedule)
                               (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    applications match {
      case Nil => Future.successful(Nil)
      case candidates =>
        val tokens = (1 to candidates.size).map(_ => tokenFactory.generateUUID())
        for {
          registeredApplicants <- Future.successful(())
          invitedApplicants <- Future.successful(())
          //TODO: Perhaps save something to DB and notify candidates here
        } yield ()
    }
  }
}
