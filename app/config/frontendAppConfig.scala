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
import java.util.Base64

import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import play.api.Play
import play.api.Play.{ configuration, current }
import uk.gov.hmrc.play.config.ServicesConfig

case class EmailConfig(url: EmailUrl, templates: EmailTemplates)

case class EmailUrl(host: String, sendEmail: String)
case class EmailTemplates(registration: String)

case class UserManagementConfig(url: UserManagementUrl)
case class UserManagementUrl(host: String)

case class FaststreamConfig(url: FaststreamUrl)
case class FaststreamUrl(host: String, base: String)

case class ApplicationRouteFrontendConfig(startNewAccountsDate: Option[LocalDateTime], blockNewAccountsDate: Option[LocalDateTime],
                                          blockApplicationsDate: Option[LocalDateTime])

object ApplicationRouteFrontendConfig {
  def read(startNewAccountsDate: Option[String], blockNewAccountsDate: Option[String],
           blockApplicationsDate: Option[String]): ApplicationRouteFrontendConfig = {
    val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    def parseDate(dateStr: String): LocalDateTime = LocalDateTime.parse(dateStr, format)

    new ApplicationRouteFrontendConfig(startNewAccountsDate map parseDate, blockNewAccountsDate map parseDate,
      blockApplicationsDate map parseDate)
  }
}

trait AppConfig {
  val analyticsToken: String
  val analyticsHost: String
  val reportAProblemPartialUrl: String
  val reportAProblemNonJSUrl: String
  val emailConfig: EmailConfig
  val userManagementConfig: UserManagementConfig
  val faststreamConfig: FaststreamConfig
  val faststreamFrontendConfig: ApplicationRouteFrontendConfig
  val edipFrontendConfig: ApplicationRouteFrontendConfig
  val sdipFrontendConfig: ApplicationRouteFrontendConfig
}

object FrontendAppConfig extends AppConfig with ServicesConfig {

  private def loadConfig(key: String) = configuration.getString(key).getOrElse(throw new Exception(s"Missing configuration key: $key"))

  val feedbackUrl = configuration.getString("feedback.url").getOrElse("")

  private val contactHost = configuration.getString(s"microservice.services.contact-frontend.host").getOrElse("")
  private val contactFormServiceIdentifier = "CSRFastStream"

  override lazy val analyticsToken = loadConfig("microservice.services.google-analytics.token")
  override lazy val analyticsHost = loadConfig("microservice.services.google-analytics.host")
  override lazy val reportAProblemPartialUrl = s"$contactHost/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  override lazy val reportAProblemNonJSUrl = s"$contactHost/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"

  override lazy val emailConfig = configuration.underlying.as[EmailConfig]("microservice.services.email")

  override lazy val userManagementConfig = configuration.underlying.as[UserManagementConfig]("microservice.services.user-management")
  override lazy val faststreamConfig = configuration.underlying.as[FaststreamConfig]("microservice.services.faststream")
  val faststreamFrontendConfig = ApplicationRouteFrontendConfig.read(
    startNewAccountsDate = configuration.getString("applicationRoute.faststream.startNewAccountsDate"),
    blockNewAccountsDate = configuration.getString("applicationRoute.faststream.blockNewAccountsDate"),
    blockApplicationsDate = configuration.getString("applicationRoute.faststream.blockApplicationsDate")
  )
  val edipFrontendConfig = ApplicationRouteFrontendConfig.read(
    startNewAccountsDate = configuration.getString("applicationRoute.edip.startNewAccountsDate"),
    blockNewAccountsDate = configuration.getString("applicationRoute.edip.blockNewAccountsDate"),
    blockApplicationsDate = configuration.getString("applicationRoute.edip.blockApplicationsDate")
  )
  val sdipFrontendConfig = ApplicationRouteFrontendConfig.read(
    startNewAccountsDate = configuration.getString("applicationRoute.sdip.startNewAccountsDate"),
    blockNewAccountsDate = configuration.getString("applicationRoute.sdip.blockNewAccountsDate"),
    blockApplicationsDate = configuration.getString("applicationRoute.sdip.blockApplicationsDate")
  )

  // Whitelist Configuration
  private def whitelistConfig(key: String): Seq[String] = Some(
    new String(Base64.getDecoder.decode(Play.configuration.getString(key).getOrElse("")), "UTF-8")
  ).map(_.split(",")).getOrElse(Array.empty).toSeq

  lazy val whitelist = whitelistConfig("whitelist")
  lazy val whitelistExcluded = whitelistConfig("whitelistExcludedCalls")
}
