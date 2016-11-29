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

package services.parity

import config.{ MicroserviceAppConfig, ParityGatewayConfig }
import play.api.Logger
import play.api.libs.json.JsObject
import services.events.{ EventService, EventSink }
import repositories._
import repositories.parity.ParityExportRepository
import repositories.parity.ParityExportRepository.ApplicationIdNotFoundException

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object ParityExportService extends ParityExportService {
  val eventService = EventService
  val parityExRepository = parityExportRepository
  val parityGatewayConfig = MicroserviceAppConfig.parityGatewayConfig
}

trait ParityExportService extends EventSink {

  val parityExRepository: ParityExportRepository
  val parityGatewayConfig: ParityGatewayConfig

  // Random apps in PHASE3_TESTS_PASSED_NOTIFIED
  def nextApplicationsForExport(batchSize: Int): Future[List[String]] = parityExRepository.nextApplicationsForExport(batchSize)

  def exportApplication(applicationId: String): Future[Unit] = {
    parityExRepository.getApplicationForExport(applicationId).map { applicationDoc =>

      Logger.debug("============ App = " + applicationDoc)

      /*
      Alternative style, not going to be used
      val export = JsObject(
        "application" -> JsObject(

        )
      )*/

      val export2 =
        s"""
          | "application": {
          |   "token": ${parityGatewayConfig.upstreamAuthToken},
          |   "userId": ${applicationDoc \ "userId"}
          | }
        """.stripMargin

    }
  }
}
