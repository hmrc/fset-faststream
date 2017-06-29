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
import model.Exceptions.{ ApplicationNotFound, ContactDetailsNotFound, PersonalDetailsNotFound }
import model.{ ApplicationRoute, SchemeType }
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent }
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.personaldetails.PersonalDetailsRepository
import repositories.sifting.SiftingRepository
import services.search.SearchForApplicantService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object SiftingController extends SiftingController {
  val siftAppRepository = siftingRepository
}

trait SiftingController extends BaseController {

  import Implicits._

  val siftAppRepository: SiftingRepository

  def findSiftingEligible(schema: String): Action[AnyContent] = Action.async { implicit request =>
    siftAppRepository.findSiftingEligible(SchemeType.withName(schema)).map { candidates =>
      Ok(Json.toJson(candidates))
    }
  }

  def submitSifting(applicationId: String, siftingPass: Boolean) = Action.async { implicit request =>
    siftAppRepository.siftCandidate(applicationId, siftingPass).map(_ => Ok(""))
  }

}
