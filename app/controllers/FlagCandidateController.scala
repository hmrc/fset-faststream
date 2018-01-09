/*
 * Copyright 2018 HM Revenue & Customs
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

import model.Exceptions.NotFoundException
import model.FlagCandidateCommands.{ FlagCandidate => RqFlagCandidate }
import model.FlagCandidatePersistedObject.{ FlagCandidate => DbFlagCandidate }
import play.api.libs.json.Json
import play.api.mvc.Action
import repositories.application.FlagCandidateRepository
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object FlagCandidateController extends FlagCandidateController {

  import repositories._

  val fcRepository = flagCandidateRepository
}

trait FlagCandidateController extends BaseController {

  val fcRepository: FlagCandidateRepository

  def find(appId: String) = Action.async { implicit request =>
    fcRepository.tryGetCandidateIssue(appId).map {
      case Some(DbFlagCandidate(_, Some(issue))) =>
        Ok(Json.toJson(RqFlagCandidate(issue)))
      case _ =>
        NotFound
    }
  }

  def save(appId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[RqFlagCandidate] { flag =>
      fcRepository.save(DbFlagCandidate(appId, Some(flag.issue))).map { _ =>
        Ok
      } recover {
        case _: NotFoundException => NotFound
      }
    }
  }

  def remove(appId: String) = Action.async { implicit request =>
    fcRepository.remove(appId).map { _ =>
      NoContent
    } recover {
      case _: NotFoundException => NotFound
    }
  }
}
