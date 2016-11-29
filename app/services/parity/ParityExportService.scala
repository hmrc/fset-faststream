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

import config.MicroserviceAppConfig
import play.api.Logger
import services.events.{ EventService, EventSink }
import repositories._
import repositories.parity.ParityExportRepository

import scala.concurrent.Future

object ParityExportService extends ParityExportService {
  val eventService = EventService
  val parityExRepository = parityExportRepository

}

trait ParityExportService extends EventSink {

  val parityExRepository: ParityExportRepository

  // Random apps in PHASE3_TESTS_PASSED_NOTIFIED
  def nextApplicationsForExport(batchSize: Int): Future[List[String]] = parityExRepository.nextApplicationsForExport(batchSize)

  def exportApplication(applicationId: String): Future[Unit] = {
    val applicationDoc = parityExRepository.getApplicationForExport(applicationId)

    Logger.debug("============ App = " + applicationDoc)

    Future.successful(())
  }
}
