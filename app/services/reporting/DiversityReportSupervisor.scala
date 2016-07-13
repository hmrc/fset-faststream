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

import akka.actor.SupervisorStrategy.Stop
import akka.actor._
import org.joda.time.DateTime
import play.api.Logger
import repositories.application.AssistanceDetailsRepository
import repositories.{ QuestionnaireRepository, ReportingRepository }

import scala.concurrent.ExecutionContext.Implicits.global

case class ProcessDiversityReport(location: String, applicationIds: List[String])
case class LocationFinished(location: String)

class DiversityReportSupervisor(val timestamp: DateTime, val locationAndRegions: List[(String, String)],
  val questionnaireRepository: QuestionnaireRepository, val asRepository: AssistanceDetailsRepository,
  val reportingRepository: ReportingRepository) extends Actor with DiversityReportSupervisorTrait {

  var startTime: Long = 0

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    super.preStart()

    startTime = System.nanoTime()

    initLocationWatcher()
  }

  override val supervisorStrategy = AllForOneStrategy() {
    case _: Exception =>
      Logger.error(s"## DiversityReportSupervisor-${self.path.name}: received exception, stop all actors")
      self ! PoisonPill
      Stop
  }

  override def receive: Receive = {
    case ProcessDiversityReport(location, appIds) =>

      val locationAndRegion = locationAndRegions.find(p => p._1 == location).getOrElse(location -> "")
      val locationProcessor =
        context.actorOf(
          LocationProcessor.props(locationAndRegion, timestamp, questionnaireRepository, asRepository, reportingRepository, self)
        )

      val message = Process(appIds)

      Logger.info(s"## DiversityReportSupervisor-${self.path.name}: sending message for: ${locationAndRegion._1}")
      locationProcessor ! message

    case LocationFinished(location) =>
      locationWatcher(location) = true
      if (!locationWatcher.values.toList.contains(false)) {
        //all the locations reported, so finish the report (update report state and stop actor)
        val stopTime = System.nanoTime()
        val duration = (stopTime - startTime) / 1000000
        finalizeReport().map { _ =>
          Logger.info(s"## DiversityReportSupervisor-${self.path.name}: all locations reported! execution time: $duration ms")
          context.stop(self)
        }
      }
  }
}

trait DiversityReportSupervisorTrait {
  val timestamp: DateTime
  val locationAndRegions: List[(String, String)]
  val questionnaireRepository: QuestionnaireRepository
  val asRepository: AssistanceDetailsRepository
  val reportingRepository: ReportingRepository

  val locationWatcher = collection.mutable.Map.empty[String, Boolean]

  def initLocationWatcher(): Unit = {
    for {
      locationAndRegion <- locationAndRegions
    } yield {
      locationWatcher += locationAndRegion._1 -> false
    }
  }

  def finalizeReport() = reportingRepository.finalizeReportStatus(timestamp)

}

object DiversityReportSupervisor {
  def props(timestamp: DateTime, locations: List[(String, String)], questionnaireRepository: QuestionnaireRepository,
    asRepository: AssistanceDetailsRepository, reportingRepository: ReportingRepository) =
    Props(new DiversityReportSupervisor(timestamp, locations, questionnaireRepository, asRepository, reportingRepository))
}
