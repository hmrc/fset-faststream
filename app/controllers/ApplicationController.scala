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
import helpers.NotificationTypeHelper
import javax.inject.{ Inject, Singleton }
import play.api.mvc.MessagesControllerComponents
import security.SilhouetteComponent

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Provide all the peripheral links from this controller, like T&C link
 */
@Singleton
class ApplicationController @Inject() (
  config: FrontendAppConfig,
  mcc: MessagesControllerComponents,
  val secEnv: SecurityEnvironment,
  val silhouetteComponent: SilhouetteComponent,
  val notificationTypeHelper: NotificationTypeHelper)(implicit val ec: ExecutionContext)
  extends BaseController(config, mcc) {

  def index = Action {
    Redirect(routes.SignInController.signIn)
  }

  def terms = CSRUserAwareAction { implicit request =>
    implicit user =>
      Future.successful(Ok(views.html.index.terms()))
  }

  def cookies = CSRUserAwareAction { implicit request =>
    implicit user =>
      Future.successful(Ok(views.html.index.cookies()))
  }

  // For this to work on a dev machine you need to run the following service manager command:
  // sm --start TRACKING_CONSENT_FRONTEND
  def trackingConsentCookies = CSRUserAwareAction { implicit request =>
    implicit user =>
      require(config.trackingConsentConfig.trackingConsentHost.isDefined, "Tracking consent host must be defined")
      Future.successful(Redirect(s"${config.trackingConsentConfig.trackingConsentHost.get}/tracking-consent/cookie-settings"))
  }

  def privacy = CSRUserAwareAction { implicit request =>
    implicit user =>
      Future.successful(Ok(views.html.index.privacy()))
  }

  def helpdesk = CSRUserAwareAction { implicit request =>
    implicit user =>
      Future.successful(Ok(views.html.index.helpdesk()))
  }

  def accessibility = CSRUserAwareAction { implicit request =>
    implicit user =>
      Future.successful(Ok(views.html.index.accessibility()))
  }
}
