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

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import play.api.Play.{ configuration, current }
import uk.gov.hmrc.play.config.{ RunMode, ServicesConfig }

case class EmailConfig(url: EmailUrl, templates: EmailTemplates)

case class EmailUrl(host: String, sendEmail: String)
case class EmailTemplates(registration: String)

case class UserManagementConfig(url: UserManagementUrl)
case class UserManagementUrl(host: String)

/** configuration for the fast tract backend */
case class FasttrackConfig(url: FasttrackUrl)
case class FasttrackUrl(host: String, base: String)

case class FasttrackFrontendConfig(blockNewAccountsDate: Option[LocalDateTime], blockApplicationsDate: Option[LocalDateTime])

object FasttrackFrontendConfig {
  def read(blockNewAccountsDate: Option[String], blockApplicationsDate: Option[String]): FasttrackFrontendConfig = {
    val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    def parseDate(dateStr: String): LocalDateTime = LocalDateTime.parse(dateStr, format)

    new FasttrackFrontendConfig(blockNewAccountsDate map parseDate, blockApplicationsDate map parseDate)
  }
}

trait AppConfig {
  val analyticsToken: String
  val analyticsHost: String
  val reportAProblemPartialUrl: String
  val reportAProblemNonJSUrl: String
  val emailConfig: EmailConfig
  val userManagementConfig: UserManagementConfig
  val fasttrackConfig: FasttrackConfig
  val fasttrackFrontendConfig: FasttrackFrontendConfig
}

object FrontendAppConfig extends AppConfig with ServicesConfig with RunMode {

  private def loadConfig(key: String) = configuration.getString(key).getOrElse(throw new Exception(s"Missing configuration key: $key"))

  val feedbackUrl = configuration.getString("feedback.url").getOrElse("")

  private val contactHost = configuration.getString(s"$env.microservice.services.contact-frontend.host").getOrElse("")
  private val contactFormServiceIdentifier = "CSRFastTrack"

  override lazy val analyticsToken = loadConfig(s"$env.microservice.services.google-analytics.token")
  override lazy val analyticsHost = loadConfig(s"$env.microservice.services.google-analytics.host")
  override lazy val reportAProblemPartialUrl = s"$contactHost/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  override lazy val reportAProblemNonJSUrl = s"$contactHost/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"

  override lazy val emailConfig = configuration.underlying.as[EmailConfig](s"$env.microservice.services.email")

  override lazy val userManagementConfig = configuration.underlying.as[UserManagementConfig](s"$env.microservice.services.user-management")
  override lazy val fasttrackConfig = configuration.underlying.as[FasttrackConfig](s"$env.microservice.services.fasttrack")
  override val fasttrackFrontendConfig = FasttrackFrontendConfig.read(
    blockNewAccountsDate = configuration.getString("application.blockNewAccountsDate"),
    blockApplicationsDate = configuration.getString("application.blockApplicationsDate")
  )
}
