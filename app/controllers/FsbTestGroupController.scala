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

import model.exchange.FsbEvaluationResults
import model.{ EvaluationResults, FsbType, SchemeId }
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.Action
import services.application.FsbTestGroupService
import services.events.EventsService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object FsbTestGroupController extends FsbTestGroupController {
  val eventsService: EventsService = EventsService
  val service = FsbTestGroupService
}

trait FsbTestGroupController extends BaseController {
  val service: FsbTestGroupService
  val eventsService: EventsService

  def save(eventId: String, sessionId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[FsbEvaluationResults] { fsbEvaluationResults =>
      val greenRedResults = fsbEvaluationResults.applicationResults.map { applicationResult =>
        applicationResult.copy(result = fromPassMark(applicationResult.result).toString)
      }
      for {
        event <- eventsService.getEvent(eventId)
        fsbTypes <- eventsService.getFsbTypes
        maybeFsbType <- Future(fsbTypes.find(f => f.key == event.description))
        fsbType: FsbType = maybeFsbType.getOrElse(throw new Exception(s"FsbType with event description ${event.description} not found"))
        schemeId = SchemeId(fsbType.schemeId)
        result <- service.saveResults(schemeId, greenRedResults)
      } yield Ok
    }
  }

  def find(applicationIds: List[String]) = Action.async { implicit request =>
      service.find(applicationIds).map { results =>
        Ok(Json.toJson(results))
      }
  }

  private def fromPassMark(s: String): EvaluationResults.Result = s match {
    case "Pass" => EvaluationResults.Green
    case "Fail" => EvaluationResults.Red
    case _ => sys.error(s"Unsupported evaluation result $s for FSB")
  }
}
