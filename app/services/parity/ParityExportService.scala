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
import connectors.ExchangeObjects
import connectors.paritygateway.ParityGatewayClient
import model.ProgressStatuses.EXPORTED
import model.events.{ AuditEvents, DataStoreEvents }
import play.api.libs.json._
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.parity.{ ApplicationReadyForExport, ParityExportRepository }
import services.application.ApplicationService
import services.events.{ EventService, EventSink }
import services.reporting.{ SocioEconomicCalculator, SocioEconomicScoreCalculator }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ParityExportService extends ParityExportService {
  val eventService = EventService
  val parityExRepository = parityExportRepository
  val parityGatewayConfig = MicroserviceAppConfig.parityGatewayConfig
  val parityGatewayClient = ParityGatewayClient
  val mRepository = mediaRepository
  val cdRepository = faststreamContactDetailsRepository
  val qRepository = questionnaireRepository
  val northSouthRepository = northSouthIndicatorRepository
  val socioEconomicCalculator = SocioEconomicCalculator
  val appRepository = applicationRepository
  val applicationService = ApplicationService
}

trait ParityExportService extends EventSink {

  val parityExRepository: ParityExportRepository
  val parityGatewayConfig: ParityGatewayConfig
  val parityGatewayClient: ParityGatewayClient
  val mRepository: MediaRepository
  val cdRepository: contactdetails.ContactDetailsRepository
  val qRepository: QuestionnaireRepository
  val northSouthRepository: NorthSouthIndicatorCSVRepository
  val applicationService: ApplicationService
  val socioEconomicCalculator: SocioEconomicScoreCalculator
  val appRepository: GeneralApplicationRepository

  // Random apps in READY_FOR_EXPORT
  def nextApplicationsForExport(batchSize: Int): Future[List[ApplicationReadyForExport]] =
  parityExRepository.nextApplicationsForExport(batchSize)

  def exportApplication(applicationId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    for {
      exportJson <- generateExportJson(applicationId)
      _ <- parityGatewayClient.createExport(exportJson)
      _ <- appRepository.addProgressStatusAndUpdateAppStatus(applicationId, EXPORTED)
    } yield {
      AuditEvents.ApplicationExported("applicationId" -> applicationId) ::
      DataStoreEvents.ApplicationExported(applicationId) :: Nil
    }
  }

  // scalastyle:off method.length
  private def generateExportJson(applicationId: String): Future[JsObject] = {

    for {
      applicationDoc <- parityExRepository.getApplicationForExport(applicationId)
      userId = (applicationDoc \ "userId").as[String]
      contactDetails <- cdRepository.find(userId)
      mediaOpt <- mRepository.find(userId)
      diversityQuestions <- qRepository.findQuestions(applicationId)
      northSouthIndicator = northSouthRepository.calculateFsacIndicator(contactDetails.postCode, contactDetails.outsideUk).get
      sesScore = socioEconomicCalculator.calculateAsInt(diversityQuestions)
      passedSchemes <- applicationService.getPassedSchemes(userId, ExchangeObjects.frameworkId)
    } yield {

      val mediaObj = mediaOpt match {
        case Some(media) if media.media.nonEmpty => Json.obj("media" -> media.media)
        case _ => Json.obj()
      }

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
              Json.obj("diversity-questionnaire" -> Json.obj("questions" -> diversityQuestionsObj, "scoring" -> Json.obj("ses" -> sesScore))) ++
              Json.obj("assessment-location" -> northSouthIndicator) ++
              Json.obj("results" -> Json.obj("passed-schemes" -> passedSchemes))
        }
      ) andThen (__ \ "testGroups").json.prune

      val appDoc = applicationDoc.transform(applicationTransformer).get

      val rootTransformer = (__ \ "application").json.put(appDoc) andThen
        __.json.update(__.read[JsObject].map { o => o ++ Json.obj("token" -> parityGatewayConfig.upstreamAuthToken) })

      val finalDoc = Json.toJson("{}").transform(rootTransformer)

      // TODO: Validate against json schema
      // finalDoc.get.validate()

      finalDoc.get
    }
    // scalastyle:on method.length
  }
}

