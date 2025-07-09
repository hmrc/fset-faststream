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

import model.Exceptions.{ApplicationNotFound, LastSchemeWithdrawException, SiftExpiredException}
import model.command.{WithdrawApplication, WithdrawScheme}
import play.api.libs.json.JsValue
import play.api.mvc.{Action, ControllerComponents}
import services.application.WithdrawService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class WithdrawController @Inject() (cc: ControllerComponents,
                                    withdrawService: WithdrawService) extends BackendController(cc) {

  implicit val ec: ExecutionContext = cc.executionContext

  def withdrawApplication(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[WithdrawApplication] { withdrawRequest =>
      withdrawService.withdraw(applicationId, withdrawRequest).map { _ =>
        Ok
      }.recover {
        case e: ApplicationNotFound => NotFound(s"Cannot find application with id: ${e.id}")
      }
    }
  }

  def withdrawScheme(applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[WithdrawScheme] { withdrawRequest =>
      withdrawService.withdraw(applicationId, withdrawRequest).map { _ => Ok }
        .recover {
          case e: ApplicationNotFound => NotFound(s"cannot find application with id: ${e.id}")
          case e: LastSchemeWithdrawException => BadRequest(e.m)
          case e: SiftExpiredException=> Forbidden(e.m)
        }
    }
  }
}
