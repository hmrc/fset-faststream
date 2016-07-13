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

import config.FasttrackFrontendConfig
import connectors.ApplicationClient.ApplicationNotFound
import connectors.{ ApplicationClient, ExchangeObjects }
import helpers.NotificationType._
import models.{ CachedData, CachedDataWithApp }
import play.api.mvc.Request
import security.SecureActions
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

case class FasttrackConfig(newAccountsEnabled: Boolean, applicationsSubmitEnabled: Boolean)

object FasttrackConfig {
  def apply(config: FasttrackFrontendConfig) = {
    val now = LocalDateTime.now()
    def isAfterNow(date: Option[LocalDateTime]) = date forall (_.isAfter(now))

    new FasttrackConfig(isAfterNow(config.blockNewAccountsDate), isAfterNow(config.blockApplicationsDate))
  }
}

/**
 * should be extended by all controllers
 */
trait BaseController extends FrontendController with SecureActions with ApplicationClient {

  implicit val feedbackUrl = config.FrontendAppConfig.feedbackUrl
  implicit def fasttrackConfig = FasttrackConfig(config.FrontendAppConfig.fasttrackFrontendConfig)

  val redirectNoApplication = Future.successful {
    Redirect(routes.HomeController.present()).flashing(warning("info.create.application"))
  }

  val redirectReadOnlyApplication = Future.successful {
    Redirect(routes.ReviewApplicationController.present()).flashing(warning("info.application.readonly"))
  }

  def updateProgress[A](additionalChanges: CachedData => CachedData = { d => d })(onUpdate: CachedData => A)(
    implicit
    user: CachedDataWithApp, hc: HeaderCarrier, request: Request[_]
  ): Future[A] =
    getApplicationProgress(user.application.applicationId).flatMap { prog =>
      val cd = CachedData(user.user, Some(user.application)).copy(application = Some(user.application.copy(progress = prog)))
      env.userService.save(
        additionalChanges(cd)
      ).map { _ =>
          onUpdate(cd)
        }
    }

  def refreshCachedUser()(implicit user: CachedDataWithApp, hc: HeaderCarrier, request: Request[_]): Future[CachedData] =
    findApplication(user.user.userID, ExchangeObjects.frameworkId).flatMap { appData =>
      val cd = CachedData(user.user, Some(appData))
      env.userService.save(cd)
    } recover {
      case e: ApplicationNotFound => CachedData(user.user, Some(user.application))
    }

}
