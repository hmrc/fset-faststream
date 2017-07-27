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
import model.Commands.Questionnaire
import model.SchemeId
import play.api.mvc.Action
import repositories.application.GeneralApplicationRepository
import repositories.sift.SiftAnswersRepository
import repositories.{ QuestionnaireRepository, _ }
import services.AuditService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object SchemeSiftAnswersController extends SchemeSiftAnswersController {
  val siftAnswersRepo = siftAnswersRepository
  val auditService = AuditService
}

trait SchemeSiftAnswersController extends BaseController {

  val siftAnswersRepo: SiftAnswersRepository
  val auditService: AuditService

  import model.Commands.Implicits._

  def addOrUpdateAnswer(applicationId: String, schemeId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[SchemeSpecificAnswer] { answer =>
      for {
        _ <- siftAnswersRepo.addSchemeSpecificAnswer(applicationId, SchemeId(schemeId), model.persisted.SchemeSpecificAnswer(answer.rawText))
      } yield {
        auditService.logEvent("SchemeSiftAnswerSaved", Map("schemeId" -> schemeId))
        Ok
      }
    }
  }
}
