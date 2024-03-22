/*
 * Copyright 2023 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import model.EvaluationResults
import model.Exceptions.{AlreadyEvaluatedForSchemeException, SchemeNotFoundException}
import model.exchange.{FsbEvaluationResults, FsbScoresAndFeedback}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.application.FsbService
import services.events.EventsService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext

@Singleton
class FsbTestGroupController @Inject() (cc: ControllerComponents,
                                        fsbService: FsbService,
                                        eventsService: EventsService) extends BackendController(cc) {

  implicit val ec: ExecutionContext = cc.executionContext

  def savePerScheme(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[FsbEvaluationResults] { fsbEvaluationResults =>
      val recordedResults = fsbEvaluationResults.copy(
        applicationResults = fsbEvaluationResults.applicationResults.filterNot(_.result == "DidNotAttend")
      )
      val greenRedResults = recordedResults.applicationResults.map { applicationResult =>
        applicationResult.copy(result = EvaluationResults.Result.fromPassFail(applicationResult.result).toString)
      }
      fsbService.saveResults(recordedResults.schemeId, greenRedResults).map { _ => Ok }.recover {
        case ex: AlreadyEvaluatedForSchemeException => BadRequest(ex.message)
        case ex: SchemeNotFoundException => UnprocessableEntity(ex.message)
      }
    }
  }

  def find(applicationIds: List[String], fsbType: Option[String]): Action[AnyContent] = Action.async {
    fsbService.findByApplicationIdsAndFsbType(applicationIds, fsbType).map { results =>
      Ok(Json.toJson(results))
    }
  }

  def findScoresAndFeedback(applicationId: String): Action[AnyContent] = Action.async {
    fsbService.findScoresAndFeedback(applicationId).map {
      case Some(scoresAndFeedback) => Ok(Json.toJson(scoresAndFeedback))
      case None => NotFound(s"Cannot find scores and feedback for applicationId: $applicationId")
    }
  }

  def saveScoresAndFeedback(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[FsbScoresAndFeedback] { fsbScoresAndFeedback: FsbScoresAndFeedback =>
      fsbService.saveScoresAndFeedback(applicationId, fsbScoresAndFeedback)
        .map { _ => Ok }
    }
  }
}
