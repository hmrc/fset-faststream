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

import config.{ FrontendAppConfig, SecurityEnvironment }
import helpers.NotificationTypeHelper
import javax.inject.{ Inject, Singleton }
import play.api.mvc.{ Action, AnyContent, MessagesControllerComponents }
import security.SilhouetteComponent

import scala.concurrent.{ ExecutionContext, Future }

@Singleton class SessionController @Inject() (config: FrontendAppConfig,
  mcc: MessagesControllerComponents,
  val secEnv: SecurityEnvironment,
  val silhouetteComponent: SilhouetteComponent,
  val notificationTypeHelper: NotificationTypeHelper)(
  implicit val ec: ExecutionContext) extends BaseController(config, mcc) {

  def extendIdleTimeout: Action[AnyContent] = CSRUserAwareAction {
    implicit request =>
      implicit user =>
        Future.successful(Ok)
  }
}
