/*
 * Copyright 2017 HM Revenue & Customs
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
import model.ApplicationStatus.ApplicationStatus
import model.ProgressStatuses.{ EXPORTED, ProgressStatus, UPDATE_EXPORTED }
import model.events.{ AuditEvents, DataStoreEvents }
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.csv.FSACIndicatorCSVRepository
import repositories.parity.{ ApplicationReadyForExport, ParityExportRepository }
import services.application.ApplicationService
import services.events.{ AuditEventService, EventSink }
import services.parity.ParityExportService.ParityExportException
import services.reporting.{ SocioEconomicCalculator, SocioEconomicScoreCalculator }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ParityExportService extends ParityExportService {
  val eventService = AuditEventService
  val parityExRepository = parityExportRepository
  val parityGatewayConfig = MicroserviceAppConfig.parityGatewayConfig
  val parityGatewayClient = ParityGatewayClient
  val mRepository = mediaRepository
  val cdRepository = faststreamContactDetailsRepository
  val qRepository = questionnaireRepository
  val fsacIndicatorCSVRepository = repositories.fsacIndicatorCSVRepository
  val socioEconomicCalculator = SocioEconomicCalculator
  val appRepository = applicationRepository
  val applicationService = ApplicationService

  case class ParityExportException(msg: String, throwable: Throwable) extends Exception(msg, throwable)
}

trait ParityExportService extends EventSink {

  val parityExRepository: ParityExportRepository
  val parityGatewayConfig: ParityGatewayConfig
  val parityGatewayClient: ParityGatewayClient
  val mRepository: MediaRepository
  val cdRepository: contactdetails.ContactDetailsRepository
  val qRepository: QuestionnaireRepository
  val fsacIndicatorCSVRepository: FSACIndicatorCSVRepository
  val applicationService: ApplicationService
  val socioEconomicCalculator: SocioEconomicScoreCalculator
  val appRepository: GeneralApplicationRepository

  def nextApplicationsForExport(batchSize: Int, statusToExport: ApplicationStatus): Future[List[ApplicationReadyForExport]] =
  parityExRepository.nextApplicationsForExport(batchSize, statusToExport)

  def exportApplication(applicationId: String)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    (for {
      exportJson <- generateExportJson(applicationId)
      _ <- parityGatewayClient.createExport(exportJson)
      _ <- appRepository.addProgressStatusAndUpdateAppStatus(applicationId, EXPORTED)
    } yield {
      AuditEvents.ApplicationExported("applicationId" -> applicationId) ::
      DataStoreEvents.ApplicationExported(applicationId) :: Nil
    }).recover {
      case ex => throw ParityExportException(s"Failed during candidate export for application id $applicationId", ex)
    }
  }

  def updateExportApplication(applicationId: String)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    (for {
      exportJson <- generateExportJson(applicationId)
      _ <- parityGatewayClient.updateExport(exportJson)
      _ <- appRepository.addProgressStatusAndUpdateAppStatus(applicationId, UPDATE_EXPORTED)
    } yield {
      AuditEvents.ApplicationExportUpdated("applicationId" -> applicationId) ::
      DataStoreEvents.ApplicationExportUpdated(applicationId) :: Nil
    }).recover {
      case ex => throw ParityExportException(s"Failed during candidate update export for application id $applicationId", ex)
    }
  }

  // scalastyle:off method.length
  private def generateExportJson(applicationId: String): Future[JsObject] = {

    for {
      applicationDoc <- parityExRepository.getApplicationForExport(applicationId)
      userId = (applicationDoc \ "userId").as[String]
      fsacIndicator = (applicationDoc \ "fsac-indicator" \ "assessmentCentre").as[String]
      contactDetails <- cdRepository.find(userId)
      mediaOpt <- mRepository.find(userId)
      diversityQuestions <- qRepository.findQuestions(applicationId)
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
              Json.obj("assessment-location" -> fsacIndicator) ++
              Json.obj("results" -> Json.obj("passed-schemes" -> passedSchemes))
        }
      ) andThen (__ \ "testGroups").json.prune andThen (__ \ "fsac-indicator").json.prune

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
