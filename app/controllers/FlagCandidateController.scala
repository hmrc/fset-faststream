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

import javax.inject.{ Inject, Singleton }
import model.Exceptions.NotFoundException
import model.FlagCandidateCommands.{ FlagCandidate => RqFlagCandidate }
import model.FlagCandidatePersistedObject.{ FlagCandidate => DbFlagCandidate }
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent, ControllerComponents }
import repositories.application.FlagCandidateRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

@Singleton
class FlagCandidateController @Inject() (cc: ControllerComponents,
                                         fcRepository: FlagCandidateRepository) extends BackendController(cc) {

  implicit val ec = cc.executionContext

  def find(appId: String): Action[AnyContent] = Action.async {
    fcRepository.tryGetCandidateIssue(appId).map {
      case Some(DbFlagCandidate(_, Some(issue))) =>
        Ok(Json.toJson(RqFlagCandidate(issue)))
      case _ =>
        NotFound
    }
  }

  def save(appId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[RqFlagCandidate] { flag =>
      fcRepository.save(DbFlagCandidate(appId, Some(flag.issue))).map { _ =>
        Ok
      } recover {
        case _: NotFoundException => NotFound
      }
    }
  }

  def remove(appId: String): Action[AnyContent] = Action.async {
    fcRepository.remove(appId).map { _ =>
      NoContent
    } recover {
      case _: NotFoundException => NotFound
    }
  }
}
