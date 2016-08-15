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

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props}
import org.joda.time.DateTime
import play.api.Logger
import repositories.assistancedetails.AssistanceDetailsRepository
import repositories.{QuestionnaireRepository, ReportingRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

case object AggregationFinished
case class Process(applicationIds: List[String])
case class QuestionnaireProfile(questionnaire: List[Map[String, String]])
case class Totals(totalApplications: Int, haveDisability: Int)
case object TimeoutMessage

class LocationProcessor(val locationAndRegion: (String, String), val timeStamp: DateTime, val questionnaireRepository: QuestionnaireRepository,
  val asRepository: AssistanceDetailsRepository, val reportingRepository: ReportingRepository,
  reportSupervisor: ActorRef) extends Actor with LocationProcessorTrait {

  import config.MicroserviceAppConfig.diversityMonitoringJobConfig

  val aggregator = context.actorOf(
    AggregatorActor.props(locationAndRegion, timeStamp, reportingRepository, self)
  )

  val jobs = List(
    context.actorOf(EthnicityCalculator.props(aggregator)),
    context.actorOf(SocioEconomicCalculator.props(aggregator)),
    context.actorOf(SexualityCalculator.props(aggregator)),
    context.actorOf(GenderCalculator.props(aggregator))
  )

  val timeout = Duration(diversityMonitoringJobConfig.forceStopActorsSecs.get, TimeUnit.SECONDS)
  val timeoutScheduler = context.system.scheduler.scheduleOnce(timeout, self, TimeoutMessage)

  override def receive: Receive = {

    case Process(appIds) =>

      val totalMessage = createTotals(appIds, countDisabilities(appIds))

      totalMessage.map { v =>
        //        Logger.info(s"## LocationProcessor:  Sending total Message - $v")
        aggregator ! v

      }

      collectQuestionnaires(appIds).map { ans =>
        val msg = QuestionnaireProfile(ans)
        jobs.foreach(_ ! msg)
      }

    case TimeoutMessage =>
      Logger.error(s"## LocationProcessor-${self.path.name}: location ${locationAndRegion._1} received the timeout message!")
      throw new Exception(s"timeout received from location: ${locationAndRegion._1} , escalate")

    case AggregationFinished =>
      Logger.info(s"## LocationProcessor-${self.path.name}: aggregation for ${locationAndRegion._1} completed!")
      val locationFinished = LocationFinished(locationAndRegion._1)
      reportSupervisor ! locationFinished
      timeoutScheduler.cancel()
      context.stop(self)
  }

}

trait LocationProcessorTrait {

  val locationAndRegion: (String, String)
  val timeStamp: DateTime
  val questionnaireRepository: QuestionnaireRepository
  val asRepository: AssistanceDetailsRepository
  val reportingRepository: ReportingRepository

  def findQuestionnaire(applicationId: String): Future[Map[String, String]] =
    questionnaireRepository.findQuestions(applicationId)

  def haveDisabilities(applicationId: String): Future[Boolean] = {
    asRepository.find(applicationId).map(ans => if (ans.hasDisability == "Yes") true else false)
  }

  def createTotals(appIds: List[String], countDisabilities: Future[List[Boolean]]): Future[Totals] = {
    val disabilitiesTotal: Future[Int] = countDisabilities.map { ans =>
      ans.foldLeft(0)((z, a) => if (a) z + 1 else z)
    }

    val applicationTotal = Future.successful(appIds.size)

    disabilitiesTotal.zip(applicationTotal).map(p => Totals(p._2, p._1))
  }

  def collectQuestionnaires(appIds: List[String]): Future[List[Map[String, String]]] = Future.sequence {
    for {
      appId <- appIds
    } yield {
      findQuestionnaire(appId)
    }
  }

  def countDisabilities(appIds: List[String]): Future[List[Boolean]] = Future.sequence {
    for {
      appId <- appIds
    } yield {
      haveDisabilities(appId)
    }
  }

}

object LocationProcessor {
  def props(locationAndRegion: (String, String), timeStamp: DateTime, questionnaireRepository: QuestionnaireRepository,
    asRepository: AssistanceDetailsRepository, reportingRepository: ReportingRepository, reportSupervisor: ActorRef) =
    Props(new LocationProcessor(locationAndRegion, timeStamp, questionnaireRepository, asRepository, reportingRepository, reportSupervisor))
}
