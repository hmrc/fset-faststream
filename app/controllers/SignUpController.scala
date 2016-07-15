/*
 * Copyright 2016 HM Revenue & Customs
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

import _root_.forms.SignUpForm
import com.mohiva.play.silhouette.api.SignUpEvent
import config.CSRHttp
import connectors.ApplicationClient
import connectors.ExchangeObjects.Implicits._
import connectors.UserManagementClient.EmailTakenException
import helpers.NotificationType._
import models.SecurityUser
import security.SignInService

import scala.concurrent.Future

object SignUpController extends SignUpController(ApplicationClient) {
  val http = CSRHttp
}

abstract class SignUpController(val applicationClient: ApplicationClient) extends BaseController(applicationClient) with SignInService {

  def present = CSRUserAwareAction { implicit request =>
    implicit user =>

      Future.successful(request.identity match {
        case Some(_) => Redirect(routes.HomeController.present()).flashing(warning("activation.already"))
        case None => Ok(views.html.registration.signup(SignUpForm.form))
      })
  }

  def signUp = CSRUserAwareAction { implicit request =>
    implicit user =>

      SignUpForm.form.bindFromRequest.fold(
        invalidForm => Future.successful(Ok(views.html.registration.signup(invalidForm))),
        data => {
          env.register(data.email.toLowerCase, data.password, data.firstName, data.lastName).flatMap { u =>
            signInUser(
              u.toCached,
              redirect = Redirect(routes.ActivationController.present()).flashing(success("account.successful")),
              env = env
            ).map { r =>
              env.eventBus.publish(SignUpEvent(SecurityUser(u.userId.toString), request, request2lang))
              r
            }
          }.recover {
            case e: EmailTakenException =>
              Ok(views.html.registration.signup(SignUpForm.form.fill(data), Some(danger("user.exists"))))
          }
        }
      )
  }
}
