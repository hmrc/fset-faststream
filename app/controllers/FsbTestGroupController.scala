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

import model.EvaluationResults
import model.Exceptions.{ AlreadyEvaluatedForSchemeException, SchemeNotFoundException }
import model.exchange.FsbEvaluationResults
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent }
import services.application.FsbTestGroupService
import services.events.EventsService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object FsbTestGroupController extends FsbTestGroupController {
  val eventsService = EventsService
  val fsbService = FsbTestGroupService
}

trait FsbTestGroupController extends BaseController {
  val fsbService: FsbTestGroupService
  val eventsService: EventsService

  def save(eventId: String, sessionId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[FsbEvaluationResults] { fsbEvaluationResults =>
      val greenRedResults = fsbEvaluationResults.applicationResults.map { applicationResult =>
        applicationResult.copy(result = EvaluationResults.Result.fromPassFail(applicationResult.result).toString)
      }

      eventsService.findSchemeByEvent(eventId).flatMap { scheme =>
        fsbService.saveResults(scheme.id, greenRedResults)
      }.map { result =>
        Ok
      }.recover {
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
