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

import java.util.UUID

import config.{ MicroserviceAppConfig, ParityGatewayConfig }
import play.api.Logger
import play.api.libs.json._
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
  val mRepository = mediaRepository
  val cdRepository = faststreamContactDetailsRepository
  val qRepository = questionnaireRepository
  val northSouthRepository = northSouthIndicatorRepository

}

trait ParityExportService extends EventSink {

  val parityExRepository: ParityExportRepository
  val parityGatewayConfig: ParityGatewayConfig
  val mRepository: MediaRepository
  val cdRepository: contactdetails.ContactDetailsRepository
  val qRepository: QuestionnaireRepository
  val northSouthRepository: NorthSouthIndicatorCSVRepository

  // Random apps in READY_FOR_EXPORT
  def nextApplicationsForExport(batchSize: Int): Future[List[String]] = parityExRepository.nextApplicationsForExport(batchSize)

  // scalastyle:off method.length
  def exportApplication(applicationId: String): Future[Unit] = {

    try {
    (for {
      applicationDoc <- parityExRepository.getApplicationForExport(applicationId)
      userId = (applicationDoc \ "userId").as[String]
      _ = print("User ID = " + userId + "\n")
      contactDetails <- cdRepository.find(userId)
      mediaOpt <- mRepository.find(userId)
      diversityQuestions <- qRepository.findQuestions(applicationId)
      northSouthIndicator = northSouthRepository.calculateFsacIndicator(contactDetails.postCode, contactDetails.outsideUk).get
    } yield {
      Logger.debug("============ App = " + applicationDoc)

      val mediaObj = mediaOpt.map(media => Json.obj("media" -> media.media)).getOrElse(Json.obj())
      val diversityQuestionsObj = diversityQuestions.foldLeft(Json.obj()){ (builder, qAndA) =>
        val (question, answer) = (qAndA._1, qAndA._2)
        builder ++ Json.obj(question -> Json.obj("answer" -> JsString(answer.answer.getOrElse("")),
          "otherDetails" -> JsString(answer.otherDetails.getOrElse("")),
          "unknown" -> JsBoolean(answer.unknown.getOrElse(false))))
      }

      val applicationTransformer = __.json.update(
        __.read[JsObject].map {
          o =>
            o ++
              mediaObj ++
              Json.obj("contact-details" -> contactDetails) ++
              Json.obj("diversity-questionnaire" -> Json.obj("questions" -> diversityQuestionsObj, "scoring" -> Json.obj("ses" -> 5))) ++
              Json.obj("assessment-location" -> northSouthIndicator) ++
              Json.obj("results" -> Json.obj("passed-schemes" -> Json.arr("Generalist"))) // <--- PLACEHOLDER, integrate with BS
        }
      )

      val appDoc = applicationDoc.transform(applicationTransformer).get

      val rootTransformer = (__ \ "application").json.put(appDoc) andThen
        __.json.update(__.read[JsObject].map { o => o ++ Json.obj("token" -> UUID.randomUUID().toString) })

      val finalDoc = Json.toJson("{}").transform(rootTransformer)

      // finalDoc.get.validate()

      Logger.debug("=========== Exp = " + finalDoc.get)
    }).recover {
      case ex => print(s"Error!!!! => $ex\n")
    }
    } catch {
      case _: Throwable => print("Exception!!!!\n"); Future.successful(())
    }

    // scalastyle:on method.length
  }
}

