/*
 * Copyright 2021 HM Revenue & Customs
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
import model.Exceptions.CannotAddMedia
import model.persisted.Media
import play.api.libs.json.JsValue
import play.api.mvc.{ Action, ControllerComponents }
import repositories.MediaRepository
import services.AuditService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class MediaController @Inject() (cc: ControllerComponents,
                                 mRepository: MediaRepository,
                                 auditService: AuditService
                                ) extends BackendController(cc) {

  def addMedia(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[Media] { media =>
      (for {
        _ <- mRepository.create(media)
      } yield {
        auditService.logEvent("CampaignReferrerSaved")
        Created
      }).recover {
        case e: CannotAddMedia => BadRequest(s"cannot add media details for user: ${e.userId}")
      }
    }
  }
}
