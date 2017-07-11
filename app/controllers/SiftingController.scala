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

import model.Commands._
import model.{ EvaluationResults, SchemeId }
import model.exchange.ApplicationSifting
import model.persisted.SchemeEvaluationResult
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent }
import repositories._
import repositories.sifting.SiftingRepository
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object SiftingController extends SiftingController {
  val siftAppRepository = siftingRepository
}

trait SiftingController extends BaseController {

  import Implicits._

  val siftAppRepository: SiftingRepository

  def findSiftingEligible(schemeId: String): Action[AnyContent] = Action.async { implicit request =>
    siftAppRepository.findApplicationsReadyForSifting(SchemeId(schemeId)).map { candidates =>
      Ok(Json.toJson(candidates))
    }
  }

  def fromPassMark(s: String): EvaluationResults.Result = s match {
    case "Pass" => EvaluationResults.Green
    case "Fail" => EvaluationResults.Red
    case _ => sys.error(s"Unsupported evaluation result $s for sifting")
  }


  def submitSifting: Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[ApplicationSifting] { sift =>
      siftAppRepository.siftCandidate(sift.applicationId,
        SchemeEvaluationResult(sift.schemeId, fromPassMark(sift.result).toString)).map(_ => Ok)
    }
  }
}
