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

package controllers

import akka.stream.scaladsl.Source
import model.UniqueIdentifier
import play.api.libs.json.{ JsObject, Json }
import play.api.libs.streams.Streams
import play.api.mvc.Action
import repositories._
import repositories.application.DiagnosticReportingRepository
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object DiagnosticReportController extends DiagnosticReportController {
  val drRepository: DiagnosticReportingRepository = diagnosticReportRepository
  val assessorAssessmentCentreScoresRepo = repositories.assessorAssessmentScoresRepository
  val reviewerAssessmentCentreScoresRepo = repositories.reviewerAssessmentScoresRepository
}

trait DiagnosticReportController extends BaseController {

  def drRepository: DiagnosticReportingRepository
  def assessorAssessmentCentreScoresRepo: AssessmentScoresMongoRepository
  def reviewerAssessmentCentreScoresRepo: AssessmentScoresMongoRepository

  def getApplicationByUserId(applicationId: String) = Action.async { implicit request =>

    (for {
      application <- drRepository.findByApplicationId(applicationId)
      assessorScores <- assessorAssessmentCentreScoresRepo.find(UniqueIdentifier(applicationId))
      reviewerScores <- reviewerAssessmentCentreScoresRepo.find(UniqueIdentifier(applicationId))
    } yield {
      val assessorScoresJson = assessorScores.map(s => JsObject(Map("assessorScores" -> Json.toJson(s).as[JsObject])))
      val reviewerScoresJson = reviewerScores.map(s => JsObject(Map("reviewerScores" -> Json.toJson(s).as[JsObject])))

      val allJson = Seq(assessorScoresJson, reviewerScoresJson).flatten.foldLeft(application) { (a, v) =>
        a :+ v
      }

      Ok(Json.toJson(allJson))
    }).recover {
      case _ => NotFound
    }
  }

  def getAllApplications = Action { implicit request =>
    val response = Source.fromPublisher(Streams.enumeratorToPublisher(drRepository.findAll()))
    Ok.chunked(response)
  }
}
