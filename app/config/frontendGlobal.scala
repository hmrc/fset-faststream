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

package config

import com.mohiva.play.silhouette.api.{ Environment, SecuredSettings, Silhouette }
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator
import com.typesafe.config.Config
import controllers.routes
import filters.CookiePolicyFilter
import forms.{ SignInForm, SignUpForm }
import helpers.NotificationType._
import models.{ CachedData, SecurityUser }
import net.ceedubs.ficus.Ficus._
import play.api.i18n.Lang
import play.api.mvc.Results._
import play.api.mvc.{ RequestHeader, Result, _ }
import play.api.{ Application, Configuration, Play }
import play.twirl.api.Html
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.play.audit.filters.FrontendAuditFilter
import uk.gov.hmrc.play.config.{ AppName, ControllerConfig, RunMode }
import uk.gov.hmrc.play.frontend.bootstrap.DefaultFrontendGlobal
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.logging.filters.FrontendLoggingFilter

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object FrontendGlobal
  extends DefaultFrontendGlobal with SecuredSettings {

  import FrontendAppConfig.feedbackUrl

  override val auditConnector = FrontendAuditConnector
  override val loggingFilter = LoggingFilter
  override val frontendAuditFilter = AuditFilter

  override def frontendFilters: Seq[EssentialFilter] = CookiePolicyFilter +: defaultFrontendFilters

  override def onStart(app: Application) {
    super.onStart(app)
    ApplicationCrypto.verifyConfiguration()
  }

  override def standardErrorTemplate(pageTitle: String, heading: String, message: String)(implicit rh: Request[_]): Html =
    views.html.error_template(pageTitle, heading, message)(rh, feedbackUrl)

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig("microservice.metrics")

  override def onNotAuthenticated(request: RequestHeader, lang: Lang): Option[Future[Result]] =
    Some(Future.successful(Redirect(routes.SignInController.present)))

  override def onNotAuthorized(request: RequestHeader, lang: Lang): Option[Future[Result]] = {
    import models.SecurityUser._
    object Internal extends Silhouette[SecurityUser, SessionAuthenticator] with FrontendController {
      override protected def env: Environment[SecurityUser, SessionAuthenticator] = SecurityEnvironmentImpl

      def whereTo: Some[Future[Result]] = {
        val sec = request.asInstanceOf[SecuredRequest[AnyContent]]
        Some(
          sec.identity.toUserFuture(hc(sec)).map {
            case Some(user: CachedData) if user.user.isActive => Redirect(routes.HomeController.present).flashing(danger("access.denied"))
            case _ => Redirect(routes.ActivationController.present).flashing(danger("access.denied"))
          }
        )
      }
    }
    Internal.whereTo
  }

}

object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object LoggingFilter extends FrontendLoggingFilter {
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

object AuditFilter extends FrontendAuditFilter with RunMode with AppName {

  override lazy val maskedFormFields = Seq(
    SignInForm.passwordField,
    SignUpForm.passwordField,
    SignUpForm.confirmPasswordField,
    SignUpForm.fakePasswordField
  )

  override lazy val applicationPort = None

  override lazy val auditConnector = FrontendAuditConnector

  override def controllerNeedsAuditing(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsAuditing
}
