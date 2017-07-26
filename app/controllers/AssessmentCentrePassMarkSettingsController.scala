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

import model.persisted.assessmentcentre.AssessmentCentrePassMarkSettings
import model.Commands.Implicits._
import play.api.libs.json.Json
import play.api.mvc.Action
import repositories._
import services.passmarksettings.AssessmentCentrePassMarkSettingsService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object AssessmentCentrePassMarkSettingsController extends AssessmentCentrePassMarkSettingsController {
  val service = AssessmentCentrePassMarkSettingsService
}

trait AssessmentCentrePassMarkSettingsController extends BaseController {
  val service: AssessmentCentrePassMarkSettingsService

  def getLatestVersion = Action.async { implicit request =>
    service.getLatestVersion.map { passmark =>
      Ok(Json.toJson(passmark))
    }
  }

  def create = Action.async(parse.json) { implicit request =>
    withJsonBody[AssessmentCentrePassMarkSettings] { settings =>
      service.create(settings).map { _ =>
        Created
      }
    }
  }
}
