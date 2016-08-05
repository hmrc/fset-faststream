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

package services.reporting

import akka.util.Timeout
import common.FutureEx
import org.joda.time.DateTime
import play.api.libs.concurrent.Akka
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.assistancedetails.AssistanceDetailsRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

object DiversityMonitoringReport extends DiversityMonitoringReport {

  override val qRepository = questionnaireRepository
  override val reportRepository = diversityReportRepository
  override val appRepository = applicationRepository
  override val asRepository = faststreamAssistanceDetailsRepository

  val yamlRepository = frameworkRepository

  def locationAndRegions: Future[List[(String, String)]] = yamlRepository.getFrameworksByRegion.map { reqions =>
    reqions.flatMap { region =>
      region.locations.map(location => (location.name, region.name))
    }
  }

}

trait DiversityMonitoringReport {
  val qRepository: QuestionnaireRepository
  val reportRepository: ReportingRepository
  val appRepository: GeneralApplicationRepository
  val asRepository: AssistanceDetailsRepository

  import play.api.Play.current

  implicit val timeout = Timeout(2.second)

  def locationAndRegions: Future[List[(String, String)]]

  /**
   * Returns all the applications that have this location as the first of the second one.
   * return the list of application ids.
   */
  def getAllApplicationsByLocation(location: String): Future[List[String]] =
    appRepository.findApplicationIdsByLocation(location)

  /**
   * for each location calls the getAllApplicationsByLocation, and sends the message to the supervisor
   */
  def execute(): Future[Unit] = {
    locationAndRegions.flatMap { locationAndRegionsList =>

      val supervisor = Akka.system.actorOf(
        DiversityReportSupervisor.props(DateTime.now, locationAndRegionsList, qRepository, asRepository, reportRepository),
        "diversity_report_supervisor"
      )

      FutureEx.traverseSerial(locationAndRegionsList) {
        case (location, region) =>
          val applicationIds = getAllApplicationsByLocation(location)
          for {
            lstAppsIds <- applicationIds
          } yield {
            supervisor ! ProcessDiversityReport(location, lstAppsIds)
          }
      }.map(_ => ())
    }
  }
}
