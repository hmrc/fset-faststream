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

import java.util.Base64

import akka.actor.ActorSystem
import com.mohiva.play.silhouette.api.crypto.{Base64AuthenticatorEncoder, Hash}
import com.mohiva.play.silhouette.api.util.{Clock, FingerprintGenerator}
import com.mohiva.play.silhouette.api.{Environment, EventBus}
import com.mohiva.play.silhouette.impl.authenticators.{SessionAuthenticatorService, SessionAuthenticatorSettings}
import com.typesafe.config.Config
import connectors.{ApplicationClient, UserManagementClient}
import helpers.WSBinaryPost
import play.api.Play
import play.api.Play.current
import uk.gov.hmrc.http.hooks.HttpHooks
import uk.gov.hmrc.http.{HttpDelete, HttpGet, HttpPost, HttpPut}
//import play.api.libs.ws.WS
import play.api.mvc.Results.{ Forbidden, NotImplemented, Redirect }
import play.api.mvc.{ Call, RequestHeader, Result }
import security.{ CsrCredentialsProvider, UserCacheService }
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.AppName
import uk.gov.hmrc.play.frontend.config.LoadAuditingConfig
import uk.gov.hmrc.play.frontend.filters.MicroserviceFilterSupport
import uk.gov.hmrc.play.http.ws._
import uk.gov.hmrc.whitelist.AkamaiWhitelistFilter

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

object FrontendAuditConnector extends AuditConnector {
  override lazy val auditingConfig = LoadAuditingConfig("auditing")
}

trait WSHttp extends HttpGet with WSGet
  with HttpPut with WSPut with HttpPost with WSPost with HttpDelete with WSDelete with HttpHooks with AppName

trait CSRHttp extends WSHttp with WSBinaryPost {
  override val hooks = NoneRequired
  //val wS = WS
  override lazy val appNameConfiguration = Play.current.configuration
  override lazy val configuration: Option[Config] = Option(Play.current.configuration.underlying)
  override lazy val actorSystem: ActorSystem = Play.current.actorSystem
}

object CSRHttp extends CSRHttp

object CaseInSensitiveFingerPrintGenerator extends  FingerprintGenerator {
  import play.api.http.HeaderNames._
  def generate(implicit request: RequestHeader) = {
    Hash.sha1(new StringBuilder()
      .append(request.headers.get(USER_AGENT).map(x => x.toLowerCase).getOrElse("")).append(":")
      .append(request.headers.get(ACCEPT_LANGUAGE).map(_.toLowerCase).getOrElse("")).append(":")
      .append(request.headers.get(ACCEPT_CHARSET).map(_.toLowerCase).getOrElse("")).append(":")
      .toString()
    )
  }
}

trait SecurityEnvironmentImpl extends Environment[security.SecurityEnvironment] {
  override lazy val eventBus: EventBus = EventBus()

  val userService = new UserCacheService(ApplicationClient, UserManagementClient)
  override val identityService = userService

  override lazy val authenticatorService = new SessionAuthenticatorService(SessionAuthenticatorSettings(
    sessionKey = Play.configuration.getString("silhouette.authenticator.sessionKey").get,
    useFingerprinting = Play.configuration.getBoolean("silhouette.authenticator.useFingerprinting").get,
    authenticatorIdleTimeout = Play.configuration.getInt("silhouette.authenticator.authenticatorIdleTimeout").map(x => x seconds),
    authenticatorExpiry = Play.configuration.getInt("silhouette.authenticator.authenticatorExpiry").get seconds
  ), CaseInSensitiveFingerPrintGenerator, new Base64AuthenticatorEncoder, Clock())

  lazy val credentialsProvider = new CsrCredentialsProvider {
    val http: CSRHttp = CSRHttp
  }

  def providers = Map(credentialsProvider.id -> credentialsProvider)

  override def requestProviders = Nil

  override val executionContext = global

  val http: CSRHttp = CSRHttp
}

object SecurityEnvironmentImpl extends SecurityEnvironmentImpl

object WhitelistFilter extends AkamaiWhitelistFilter with MicroserviceFilterSupport {

  // Whitelist Configuration
  private def whitelistConfig(key: String): Seq[String] =
    Some(new String(Base64.getDecoder.decode(Play.configuration.getString(key).getOrElse("")), "UTF-8"))
      .map(_.split(",")).getOrElse(Array.empty).toSeq

  // List of IP addresses
  override val whitelist: Seq[String] = whitelistConfig("whitelist")

  // List of allowed file upload addresses
  val whitelistFileUpload: Seq[String] = whitelistConfig("whitelistFileUpload")

  // List of prefixes that file uploads happen under
  val fileUploadPathPrefixes = List("/fset-fast-stream/file-submission/")

  // Es. /ping/ping,/admin/details
  override def excludedPaths: Seq[Call] = whitelistConfig("whitelistExcludedCalls").map {
    path => Call("GET", path)
  }

  def destination: Call = Call(
    "GET",
    "https://www.apply-civil-service-fast-stream.service.gov.uk/shutter/fset-faststream/index.html"
  )

  // Modified AkamaiWhitelistFilter (play-whitelist-filter)
  private def isCircularDestination(requestHeader: RequestHeader): Boolean =
    requestHeader.uri == destination.url

  private def toCall(rh: RequestHeader): Call =
    Call(rh.method, rh.uri)

  override def apply(f: (RequestHeader) => Future[Result])(rh: RequestHeader): Future[Result] =
    if (excludedPaths.contains(toCall(rh))) {
      f(rh)
    } else {
      rh.headers.get(trueClient) map {
        ip =>
          if (fileUploadPathPrefixes.exists(pathPrefix => rh.uri.startsWith(pathPrefix))) {
            if (whitelistFileUpload.contains(ip)) {
              f(rh)
            } else {
              Future.successful(Forbidden)
            }
          }
          else if (whitelist.head == "*" || whitelist.contains(ip)) {
            f(rh)
          } else if (isCircularDestination(rh)) {
            Future.successful(Forbidden)
          } else {
            Future.successful(Redirect(destination))
          }
      } getOrElse Future.successful(NotImplemented)
    }
}
