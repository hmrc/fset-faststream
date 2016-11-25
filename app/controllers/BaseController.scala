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

import java.time.LocalDateTime

import config.{ CSRCache, ApplicationRouteFrontendConfig }
import connectors.ApplicationClient
import helpers.NotificationType._
import models.ApplicationRoute.{ apply => _, _ }
import models.{ CachedData, CachedDataWithApp }
import play.api.mvc.Request
import security.SecureActions
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

case class ApplicationRouteConfig(newAccountsStarted: Boolean, newAccountsEnabled: Boolean, applicationsSubmitEnabled: Boolean)

object ApplicationRouteConfig {
  def apply(config: ApplicationRouteFrontendConfig) = {
    require(
      config.startNewAccountsDate.forall(startDate => config.blockNewAccountsDate.forall(_.isAfter(startDate))),
      "start new accounts date must be before block new accounts date"
    )
    val now = LocalDateTime.now()

    def isAfterNow(date: Option[LocalDateTime]) = date forall (_.isAfter(now))

    def isBeforeNow(date: Option[LocalDateTime]) = date forall (_.isBefore(now))

    new ApplicationRouteConfig(isBeforeNow(config.startNewAccountsDate), isAfterNow(config.blockNewAccountsDate),
      isAfterNow(config.blockApplicationsDate))
  }
}

/**
 * should be extended by all controllers
 */
abstract class BaseController(applicationClient: ApplicationClient, val cacheClient: CSRCache)
  extends SecureActions with FrontendController {

  implicit val feedbackUrl = config.FrontendAppConfig.feedbackUrl

  implicit val appRouteConfigMap = Map(
    Faststream -> ApplicationRouteConfig(config.FrontendAppConfig.faststreamFrontendConfig),
    Edip -> ApplicationRouteConfig(config.FrontendAppConfig.edipFrontendConfig),
    Sdip -> ApplicationRouteConfig(config.FrontendAppConfig.sdipFrontendConfig)
  )

  val redirectNoApplication = Future.successful {
    Redirect(routes.HomeController.present()).flashing(warning("info.create.application"))
  }

  val redirectReadOnlyApplication = Future.successful {
    Redirect(routes.PreviewApplicationController.present()).flashing(warning("info.application.readonly"))
  }

  def updateProgress[A](additionalChanges: CachedData => CachedData = { d => d })(onUpdate: CachedData => A)
                       (implicit user: CachedDataWithApp, hc: HeaderCarrier, request: Request[_]): Future[A] =
    applicationClient.getApplicationProgress(user.application.applicationId).flatMap { prog =>
      val cd = CachedData(user.user, Some(user.application)).copy(application = Some(user.application.copy(progress = prog)))
      env.userService.save(
        additionalChanges(cd)
      ).map { _ =>
          onUpdate(cd)
        }
    }

  def isNewAccountsStarted(implicit applicationRoute: ApplicationRoute = Faststream) =
    appRouteConfigMap.get(applicationRoute).forall(_.newAccountsStarted)

  def isNewAccountsEnabled(implicit applicationRoute: ApplicationRoute = Faststream) =
    appRouteConfigMap.get(applicationRoute).forall(_.newAccountsEnabled)

  def isSubmitApplicationsEnabled(implicit applicationRoute: ApplicationRoute = Faststream) =
    appRouteConfigMap.get(applicationRoute).forall(_.applicationsSubmitEnabled)
}
