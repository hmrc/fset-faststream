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

import connectors.exchange.SchemeSpecificAnswer
import model.SchemeId
import play.api.libs.json.Json
import play.api.mvc.Action
import repositories.sift.SiftAnswersRepository
import repositories._
import services.AuditService
import services.sift.SiftAnswersService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object SchemeSiftAnswersController extends SchemeSiftAnswersController {
  val siftAnswersService = SiftAnswersService
  val auditService = AuditService
}

trait SchemeSiftAnswersController extends BaseController {

  val siftAnswersService: SiftAnswersService
  val auditService: AuditService

  import model.Commands.Implicits._

  def addOrUpdateAnswer(applicationId: String, schemeId: SchemeId) = Action.async(parse.json) { implicit request =>
    withJsonBody[SchemeSpecificAnswer] { answer =>
      for {
        _ <- siftAnswersService.addSchemeSpecificAnswer(applicationId, schemeId, model.persisted.SchemeSpecificAnswer(answer.rawText))
      } yield {
        auditService.logEvent("SchemeSiftAnswerSaved", Map("schemeId" -> schemeId.value))
        Ok
      }
    }
  }

  def getSchemeSpecificAnswer(applicationId: String, schemeId: model.SchemeId)  = Action.async { implicit request =>
    siftAnswersService.findSchemeSpecificAnswer(applicationId, schemeId).map { result =>
      result match {
        case Some(answer) => Ok(Json.toJson(answer))
        case _ => NotFound(s"Cannot find scheme specific answer for applicationId: $applicationId, scheme: $schemeId")
      }
    }
  }

  def getSiftAnswers(applicationId: String)  = Action.async { implicit request =>
    siftAnswersService.findSiftAnswers(applicationId).map { result =>
      result match {
        case Some(answers) => Ok(Json.toJson(answers))
        case _ => NotFound(s"Cannot find answers to additional questions for applicationId: $applicationId")
      }
    }
  }
}
