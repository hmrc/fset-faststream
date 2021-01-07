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
import model.Exceptions.{ ApplicationNotFound, LastSchemeWithdrawException, SiftExpiredException }
import model.command.{ WithdrawApplication, WithdrawScheme }
import play.api.libs.json.JsValue
import play.api.mvc.{ Action, ControllerComponents }
import services.application.ApplicationService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class WithdrawController @Inject() (cc: ControllerComponents,
                                    applicationService: ApplicationService) extends BackendController(cc) {

  def withdrawApplication(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[WithdrawApplication] { withdrawRequest =>
      applicationService.withdraw(applicationId, withdrawRequest).map { _ =>
        Ok
      }.recover {
        case e: ApplicationNotFound => NotFound(s"Cannot find application with id: ${e.id}")
        case e: SiftExpiredException => Forbidden(e.m)
      }
    }
  }

  def withdrawScheme(applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[WithdrawScheme] { withdrawRequest =>
      applicationService.withdraw(applicationId, withdrawRequest).map { _ => Ok }
        .recover {
          case e: ApplicationNotFound => NotFound(s"cannot find application with id: ${e.id}")
          case e: LastSchemeWithdrawException => BadRequest(e.m)
          case e: SiftExpiredException=> Forbidden(e.m)
        }
    }
  }
}
