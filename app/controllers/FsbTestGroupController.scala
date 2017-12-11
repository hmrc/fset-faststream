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

import model.{ EvaluationResults, SchemeId }
import model.Exceptions.{ AlreadyEvaluatedForSchemeException, SchemeNotFoundException }
import model.exchange.FsbEvaluationResults
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent }
import services.application.FsbService
import services.events.EventsService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object FsbTestGroupController extends FsbTestGroupController {
  val eventsService = EventsService
  val fsbService = FsbService
}

trait FsbTestGroupController extends BaseController {
  val fsbService: FsbService
  val eventsService: EventsService

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

  def find(applicationIds: List[String], fsbType: Option[String]): Action[AnyContent] = Action.async { implicit request =>
    fsbService.findByApplicationIdsAndFsbType(applicationIds, fsbType).map { results =>
      Ok(Json.toJson(results))
    }
  }

}
