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

import akka.actor.{ Actor, ActorRef, Props }
import model.PersistedObjects._
import org.joda.time.DateTime
import play.api.Logger
import repositories.ReportingRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AggregatorActor(locationAndRegion: (String, String), timeStamp: DateTime,
  reportRepository: ReportingRepository, locationProcessor: ActorRef) extends Actor {

  var genderCalculation: Option[DiversityGender] = None
  var sexualityCalculation: Option[DiversitySexuality] = None
  var socioEconomicCalculation: Option[DiversitySocioEconomic] = None
  var ethnicityCalculation: Option[DiversityEthnicity] = None
  var total: Option[Totals] = None

  override def receive: Receive = {
    case msg: DiversityGender =>
      Logger.info(s"## Aggregator: - ${locationAndRegion._1} - got message gender! ")
      genderCalculation = Some(msg)
      mergeAndStop()
    case msg: DiversitySexuality =>
      Logger.info(s"## Aggregator: - ${locationAndRegion._1} - got message sexuality!")
      sexualityCalculation = Some(msg)
      mergeAndStop()
    case msg: DiversitySocioEconomic =>
      Logger.info(s"## Aggregator: - ${locationAndRegion._1} - got message socio!")
      socioEconomicCalculation = Some(msg)
      mergeAndStop()
    case msg: DiversityEthnicity =>
      Logger.info(s"## Aggregator: - ${locationAndRegion._1} - got message ethnicity!")
      ethnicityCalculation = Some(msg)
      mergeAndStop()
    case msg: Totals =>
      Logger.info(s"## Aggregator: - ${locationAndRegion._1} - got message total!")
      total = Some(msg)
      mergeAndStop()
  }

  def mergeAndStop() = {
    merge.map { m =>
      if (m) {
        locationProcessor ! AggregationFinished
        context.stop(self)
      }
    }

  }

  private def merge: Future[Boolean] = {
    (total, sexualityCalculation, genderCalculation, socioEconomicCalculation, ethnicityCalculation) match {
      case (Some(t), Some(sexuality), Some(genderAndDis), Some(socioEcon), Some(ethnicity)) =>

        val collector = ethnicity.collector ++ socioEcon.collector ++ sexuality.collector ++ genderAndDis.collector
        val report = new DiversityReportRow(locationAndRegion._1, locationAndRegion._2,
          t.totalApplications, t.haveDisability, collector)

        Logger.info(s"## Aggregator-${self.path.name}: got all 5 messages for location: ${locationAndRegion._1}, saving to db!")
        reportRepository.update(locationAndRegion._1, timeStamp, report).map { _ =>
          true
        }

      case _ => Future.successful(false)
    }
  }
}

object AggregatorActor {
  def props(locationAndRegion: (String, String), timeStamp: DateTime, reportRepository: ReportingRepository, locationProcessor: ActorRef) =
    Props(new AggregatorActor(locationAndRegion, timeStamp, reportRepository, locationProcessor))
}
