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

import model.Exceptions.NotFoundException
import model.exchange.OnboardQuestions as ExOnboardQuestions
import model.persisted.OnboardQuestions as DbOnboardQuestions
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.onboardquestions.OnboardQuestionsRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class OnboardQuestionsController @Inject()(cc: ControllerComponents,
                                           onboardQuestionsRepository: OnboardQuestionsRepository,
                                          ) extends BackendController(cc) {

  implicit val ec: ExecutionContext = cc.executionContext

  def find(appId: String): Action[AnyContent] = Action.async {
    onboardQuestionsRepository.tryGetOnboardQuestions(appId).map {
      case Some(DbOnboardQuestions(_, Some(niNumber))) =>
        Ok(Json.toJson(ExOnboardQuestions(niNumber)))
      case _ =>
        NotFound(s"No record found for applicationId $appId")
    }
  }

  def save(appId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[ExOnboardQuestions] { onboardQuestions =>
      onboardQuestionsRepository.save(DbOnboardQuestions(appId, Some(onboardQuestions.niNumber))).map { _ =>
        Ok
      } recover {
        case _: NotFoundException => NotFound(s"No record found for applicationId $appId")
      }
    }
  }
}
