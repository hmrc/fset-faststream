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
import model.Exceptions.{ SiftAnswersIncomplete, SiftAnswersSubmitted }
import model.SchemeId
import model.exchange.sift.{ GeneralQuestionsAnswers, SchemeSpecificAnswer }
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent, ControllerComponents }
import services.AuditService
import services.sift.{ ApplicationSiftService, SiftAnswersService }
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

@Singleton
class SchemeSiftAnswersController @Inject() (cc: ControllerComponents,
                                             siftAnswersService: SiftAnswersService,
                                             applicationSiftService: ApplicationSiftService,
                                             auditService: AuditService
                                            ) extends BackendController(cc) {

  implicit val ec = cc.executionContext

  def addOrUpdateSchemeSpecificAnswer(applicationId: String, schemeId: SchemeId): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
    withJsonBody[SchemeSpecificAnswer] { answer =>
      (for {
        _ <- siftAnswersService.addSchemeSpecificAnswer(applicationId, schemeId, answer)
      } yield {
        auditService.logEvent("Scheme specific answer saved", Map("applicationId" -> applicationId, "schemeId" -> schemeId.value))
        Ok
      }) recover {
        case e: SiftAnswersSubmitted => Conflict(e.m)
      }
    }
  }

  def addOrUpdateGeneralAnswers(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[GeneralQuestionsAnswers] { answers =>
      (for {
        _ <- siftAnswersService.addGeneralAnswers(applicationId, answers)
      } yield {
        auditService.logEvent("General answers saved", Map("applicationId" -> applicationId))
        Ok
      }) recover {
        case e: SiftAnswersSubmitted => Conflict(e.m)
      }
    }
  }

  def getSchemeSpecificAnswer(applicationId: String, schemeId: model.SchemeId): Action[AnyContent] = Action.async { implicit request =>
    siftAnswersService.findSchemeSpecificAnswer(applicationId, schemeId).map {
      case Some(answer) => Ok(Json.toJson(answer))
      case _ => NotFound(s"Cannot find scheme specific answer for applicationId: $applicationId, scheme: $schemeId")
    }
  }

  def getGeneralAnswers(applicationId: String): Action[AnyContent] = Action.async {
    siftAnswersService.findGeneralAnswers(applicationId).map {
      case Some(answer) => Ok(Json.toJson(answer))
      case _ => NotFound(s"Cannot find additional general answers for applicationId: $applicationId")
    }
  }

  def getSiftAnswers(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    siftAnswersService.findSiftAnswers(applicationId).map {
      case Some(answers) => Ok(Json.toJson(answers))
      case _ => NotFound(s"Cannot find answers to additional questions for applicationId: $applicationId")
    }
  }

  def submitAnswers(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    (for {
      isSiftExpired <- applicationSiftService.isSiftExpired(applicationId)
      if !isSiftExpired
      _ <- siftAnswersService.submitAnswers(applicationId)
    } yield {
      auditService.logEvent("Additional answers saved", Map("applicationId" -> applicationId))
      Ok
    })recover {
      case e: SiftAnswersIncomplete => UnprocessableEntity(e.m)
      case e: SiftAnswersSubmitted => Conflict(e.m)
      case e: NoSuchElementException => Forbidden(e.getMessage)
    }
  }

  def getSiftAnswersStatus(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    siftAnswersService.findSiftAnswersStatus(applicationId).map {
      case Some(status) => Ok(Json.toJson(status))
      case _ => NotFound(s"No existing additional answers for applicationId: $applicationId")
    }
  }
}
