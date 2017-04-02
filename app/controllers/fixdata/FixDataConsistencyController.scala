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

package controllers.fixdata

import model.Exceptions.NotFoundException
import model.command.{ FastPassEvaluation, FastPassPromotion, ProcessedFastPassCandidate }
import play.api.libs.json.Json
import play.api.mvc.Action
import services.application.ApplicationService
import services.fastpass.FastPassService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object FixDataConsistencyController extends FixDataConsistencyController {
  override val applicationService = ApplicationService
  override val fastPassService = FastPassService
}

trait FixDataConsistencyController extends BaseController {
  val applicationService: ApplicationService
  val fastPassService: FastPassService

  def removeETray(appId: String) = Action.async { implicit request =>
    applicationService.fixDataByRemovingETray(appId).map { _ =>
      NoContent
    } recover {
      case _: NotFoundException => NotFound
    }
  }

  def removeProgressStatus(appId: String, progressStatus: String) = Action.async { implicit request =>
    applicationService.fixDataByRemovingProgressStatus(appId, progressStatus).map { _ =>
      NoContent
    } recover {
      case _: NotFoundException => NotFound
    }
  }

  def promoteToFastPassAccepted(applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[FastPassPromotion] { req =>
      fastPassService.promoteToFastPassCandidate(applicationId, req.triggeredBy).map { _ =>
        NoContent
      } recover {
        case _: NotFoundException => NotFound
      }
    }
  }

  def removeVideoInterviewFailed(appId: String) = Action.async { implicit request =>
    applicationService.fixDataByRemovingVideoInterviewFailed(appId).map { _ =>
      NoContent
    } recover {
      case _: NotFoundException => NotFound
    }
  }

}
