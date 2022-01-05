/*
 * Copyright 2022 HM Revenue & Customs
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

import config.{ FrontendAppConfig, SecurityEnvironment }
import connectors.ApplicationClient
import connectors.ApplicationClient.TestForTokenExpiredException
import connectors.UserManagementClient.TokenEmailPairInvalidException
import forms.VerifyCodeForm
import helpers.NotificationTypeHelper
import javax.inject.{ Inject, Singleton }
import models.CachedData
import play.api.mvc.{ MessagesControllerComponents, Request, Result }
import security.SilhouetteComponent

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class InvigilatedController @Inject() (
  config: FrontendAppConfig,
  mcc: MessagesControllerComponents,
  val secEnv: SecurityEnvironment,
  val silhouetteComponent: SilhouetteComponent,
  val notificationTypeHelper: NotificationTypeHelper,
  applicationClient: ApplicationClient,
  formWrapper: VerifyCodeForm)(implicit val ec: ExecutionContext) extends BaseController(config, mcc) {
  import notificationTypeHelper._

  def present = CSRUserAwareAction { implicit request =>
    implicit user =>
      Future.successful(Ok(views.html.index.invigilatedEtraySignin(formWrapper.form)))
  }

  def verifyToken = CSRUserAwareAction { implicit request =>
    implicit user =>
      formWrapper.form.bindFromRequest.fold(
        invalidForm =>
          Future.successful(Ok(views.html.index.invigilatedEtraySignin(invalidForm))),
        data =>
          applicationClient.verifyInvigilatedToken(data.email, data.token).flatMap {
          invigilatedTest => Future.successful(Redirect(invigilatedTest.url))
        }.recover {
          case e: TokenEmailPairInvalidException => showValidationError(data)
          case e: TestForTokenExpiredException => showValidationError(data, "error.token.expired")
        }
    )
  }

  def showValidationError(data: VerifyCodeForm.Data, errorMsg: String = "error.token.invalid")
                         (implicit user: Option[CachedData], request: Request[_]): Result = {
    Ok(views.html.index.invigilatedEtraySignin(formWrapper.form.fill(VerifyCodeForm.Data(email = "", token = "")),
      Some(danger(errorMsg))))
  }
}
