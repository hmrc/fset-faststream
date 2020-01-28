/*
 * Copyright 2020 HM Revenue & Customs
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

import controllers.{ ApplicationRouteState, ApplicationRouteStateImpl }
import models.ApplicationRoute._
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import play.api.Mode.Mode
import play.api.{ Configuration, Logger, Play }
import play.api.Play.{ configuration, current }
import uk.gov.hmrc.play.config.ServicesConfig

case class AuthConfig(serviceName: String)

case class EmailConfig(url: EmailUrl, templates: EmailTemplates)

case class EmailUrl(host: String, sendEmail: String)
case class EmailTemplates(registration: String)

case class UserManagementConfig(url: UserManagementUrl)
case class UserManagementUrl(host: String)

case class FaststreamConfig(url: FaststreamUrl)
case class FaststreamUrl(host: String, base: String)

case class ApplicationRouteFrontendConfig(timeZone: Option[String], startNewAccountsDate: Option[LocalDateTime],
                                          blockNewAccountsDate: Option[LocalDateTime],
                                          blockApplicationsDate: Option[LocalDateTime])

case class AddressLookupConfig(url: String)

object ApplicationRouteFrontendConfig {
  def read(timeZone: Option[String], startNewAccountsDate: Option[String], blockNewAccountsDate: Option[String],
           blockApplicationsDate: Option[String]): ApplicationRouteFrontendConfig = {

    def parseDate(dateStr: String): LocalDateTime = LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    new ApplicationRouteFrontendConfig(timeZone, startNewAccountsDate map parseDate, blockNewAccountsDate map parseDate,
      blockApplicationsDate map parseDate)
  }
}

trait AppConfig {
  val analyticsToken: String
  val analyticsHost: String
  val emailConfig: EmailConfig
  val authConfig: AuthConfig
  val userManagementConfig: UserManagementConfig
  val faststreamConfig: FaststreamConfig
  val applicationRoutesFrontend: Map[ApplicationRoute, ApplicationRouteState]
  val addressLookupConfig: AddressLookupConfig
  val fsacGuideUrl: String
}

object FrontendAppConfig extends AppConfig with ServicesConfig {

  override def mode: Mode = Play.current.mode
  override def runModeConfiguration: Configuration = Play.current.configuration

  private def loadConfig(key: String) = configuration.getString(key).getOrElse(throw new Exception(s"Missing configuration key: $key"))

  val feedbackUrl = configuration.getString("feedback.url").getOrElse("")

  val marketingTrackingEnabled = configuration.getBoolean("marketing.trackingEnabled").getOrElse(false)

  override lazy val analyticsToken = loadConfig("microservice.services.google-analytics.token")
  override lazy val analyticsHost = loadConfig("microservice.services.google-analytics.host")

  override lazy val emailConfig = configuration.underlying.as[EmailConfig]("microservice.services.email")
  override lazy val authConfig = configuration.underlying.as[AuthConfig](s"microservice.services.auth")

  override lazy val userManagementConfig = configuration.underlying.as[UserManagementConfig]("microservice.services.user-management")
  override lazy val faststreamConfig = configuration.underlying.as[FaststreamConfig]("microservice.services.faststream")

  override lazy val addressLookupConfig = configuration.underlying.as[AddressLookupConfig]("microservice.services.address-lookup")

  override lazy val fsacGuideUrl = configuration.underlying.as[String]("microservice.fsacGuideUrl")

  override lazy val applicationRoutesFrontend = Map(
    Faststream -> loadAppRouteConfig("faststream"),
    Edip -> loadAppRouteConfig("edip"),
    Sdip -> loadAppRouteConfig("sdip"),
    SdipFaststream -> loadAppRouteConfig("faststream")
  )

  def loadAppRouteConfig(routeKey: String) = {
    val timeZone = configuration.getString("applicationRoute.timeZone")
    val startNewAccountsDate = configuration.getString(s"applicationRoute.$routeKey.startNewAccountsDate")
    val blockNewAccountsDate = configuration.getString(s"applicationRoute.$routeKey.blockNewAccountsDate")
    val blockApplicationsDate = configuration.getString(s"applicationRoute.$routeKey.blockApplicationsDate")
    Logger.warn(s"Reading campaign closing times timeZone=$timeZone")
    Logger.warn(s"Reading campaign closing times for routeKey=$routeKey...")
    Logger.warn(s"routeKey=$routeKey - startNewAccountsDate=$startNewAccountsDate")
    Logger.warn(s"routeKey=$routeKey - blockNewAccountsDate=$blockNewAccountsDate")
    Logger.warn(s"routeKey=$routeKey - blockApplicationsDate=$blockApplicationsDate")

    ApplicationRouteStateImpl(
      ApplicationRouteFrontendConfig.read(
        timeZone = configuration.getString("applicationRoute.timeZone"),
        startNewAccountsDate = configuration.getString(s"applicationRoute.$routeKey.startNewAccountsDate"),
        blockNewAccountsDate = configuration.getString(s"applicationRoute.$routeKey.blockNewAccountsDate"),
        blockApplicationsDate = configuration.getString(s"applicationRoute.$routeKey.blockApplicationsDate")
      )
    )
  }

  // Whitelist Configuration
  private def whitelistConfig(key: String): Seq[String] = Some(
    new String(Base64.getDecoder.decode(Play.configuration.getString(key).getOrElse("")), "UTF-8")
  ).map(_.split(",")).getOrElse(Array.empty).toSeq

  lazy val whitelist = whitelistConfig("whitelist")
  lazy val whitelistFileUpload = whitelistConfig("whitelistFileUpload")
  lazy val whitelistExcluded = whitelistConfig("whitelistExcludedCalls")
}
