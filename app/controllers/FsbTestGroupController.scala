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

import model.Exceptions.SchemeNotFoundException
import model.exchange.FsbEvaluationResults
import model.{ EvaluationResults, FsbType, SchemeId }
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent }
import services.application.FsbTestGroupService
import services.events.EventsService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object FsbTestGroupController extends FsbTestGroupController {
  val eventsService = EventsService
  val service = FsbTestGroupService
}

trait FsbTestGroupController extends BaseController {
  val service: FsbTestGroupService
  val eventsService: EventsService

  def save(eventId: String, sessionId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[FsbEvaluationResults] { fsbEvaluationResults =>
      val greenRedResults = fsbEvaluationResults.applicationResults.map { applicationResult =>
        applicationResult.copy(result = EvaluationResults.Result.fromPassFail(applicationResult.result).toString)
      }

      eventsService.findSchemeByEvent(eventId).flatMap {
        case Some(scheme) => service.saveResults(scheme.id, greenRedResults)
        case None => throw new SchemeNotFoundException(s"Event $eventId has no associated Scheme. FsbType mismatch")
      }

      Future.successful(Ok)
    }
  }

  def find(applicationIds: List[String], fsbType: Option[String]): Action[AnyContent] = Action.async { implicit request =>
    service.findByApplicationIdsAndFsbType(applicationIds, fsbType).map { results =>
      Ok(Json.toJson(results))
    }
  }

}