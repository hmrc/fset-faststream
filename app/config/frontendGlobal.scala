/*
 * Copyright 2019 HM Revenue & Customs
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

import com.typesafe.config.Config
import controllers.{ SignInController, routes }
import filters.CookiePolicyFilter
import forms.{ SignInForm, SignUpForm }
import net.ceedubs.ficus.Ficus._
import play.api._
import play.api.i18n.Lang
import play.api.mvc.Results._
import play.api.mvc.{ RequestHeader, Result, _ }
import play.twirl.api.Html
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.play.config.{ AppName, ControllerConfig }
import uk.gov.hmrc.play.frontend.bootstrap.DefaultFrontendGlobal

import scala.concurrent.Future
import uk.gov.hmrc.play.frontend.filters.{ FrontendAuditFilter, FrontendLoggingFilter, MicroserviceFilterSupport }

abstract class DevelopmentFrontendGlobal
  extends DefaultFrontendGlobal {

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
}

object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object LoggingFilter extends FrontendLoggingFilter with MicroserviceFilterSupport {
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

object AuditFilter extends FrontendAuditFilter with AppName with MicroserviceFilterSupport {

  override def appNameConfiguration: Configuration = Play.current.configuration

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

object DevelopmentFrontendGlobal extends DevelopmentFrontendGlobal {
  override def onStart(app: Application) = {
    if (app.mode == Mode.Prod) Logger.warn("WHITE-LISTING DISABLED: Loading Development Frontend Global")
    super.onStart(app)
  }
}

object ProductionFrontendGlobal extends DevelopmentFrontendGlobal {
  override def filters = WhitelistFilter +: super.filters
}
