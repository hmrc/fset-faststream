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

package config

import akka.actor.ActorSystem
import com.mohiva.play.silhouette.api.crypto.{ Base64AuthenticatorEncoder, Hash }
import com.mohiva.play.silhouette.api.util.{ Clock, FingerprintGenerator }
import com.mohiva.play.silhouette.api.{ Environment, EventBus }
import com.mohiva.play.silhouette.impl.authenticators.{ SessionAuthenticatorService, SessionAuthenticatorSettings }
import com.typesafe.config.Config
import helpers.WSBinaryPost
import javax.inject.{ Inject, Singleton }
import play.api.Application
import play.api.libs.ws.WSClient
import play.api.mvc.{ LegacySessionCookieBaker, RequestHeader }
import security.{ CsrCredentialsProvider, UserCacheService }
import uk.gov.hmrc.http.hooks.HttpHooks
import uk.gov.hmrc.http.{ HttpDelete, HttpGet, HttpPost, HttpPut }
import uk.gov.hmrc.play.http.ws._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

trait WSHttp
  extends HttpGet with WSGet
  with HttpPut with WSPut
  with HttpPost with WSPost
  with HttpDelete with WSDelete
  with HttpHooks

@Singleton
class CSRHttp @Inject() (
  val wsClient: WSClient,
  application: Application)
  extends WSHttp with WSBinaryPost {
  override val hooks = NoneRequired
  override lazy val configuration: Config = application.configuration.underlying
  override lazy val actorSystem: ActorSystem = application.actorSystem
}

object CaseInSensitiveFingerPrintGenerator extends FingerprintGenerator {
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

@Singleton
class SecurityEnvironmentImpl @Inject() (
  val application: Application,
  val config: FrontendAppConfig,
  val http: CSRHttp,
  val userCacheService: UserCacheService,
  val sessionCookieBaker: LegacySessionCookieBaker)(implicit val ec: ExecutionContext) extends SecurityEnvironment {
  lazy val eventBus: EventBus = EventBus()

  val userService = userCacheService
  def identityService = userCacheService

  private val sessionAuthenticationSettings = SessionAuthenticatorSettings(
    sessionKey = application.configuration.getOptional[String]("silhouette.authenticator.sessionKey").get,
    useFingerprinting = application.configuration.getOptional[Boolean]("silhouette.authenticator.useFingerprinting").get,
    authenticatorIdleTimeout = application.configuration.getOptional[Int](
      "silhouette.authenticator.authenticatorIdleTimeout").map(x => x seconds),
    authenticatorExpiry = application.configuration.getOptional[Int](
      "silhouette.authenticator.authenticatorExpiry").get seconds
  )

  lazy val authenticatorService = new SessionAuthenticatorService(
    sessionAuthenticationSettings,
    CaseInSensitiveFingerPrintGenerator,
    new Base64AuthenticatorEncoder,
    sessionCookieBaker,
    Clock())

  lazy val credentialsProvider = new CsrCredentialsProvider(config, http)

  def providers = Map(credentialsProvider.id -> credentialsProvider)

  def requestProviders = Nil

  val executionContext = ec
}

trait SecurityEnvironment extends Environment[security.SecurityEnvironment] {
  val application: Application
  val config: FrontendAppConfig
  val http: CSRHttp
  def userCacheService: UserCacheService
  val sessionCookieBaker: LegacySessionCookieBaker
  implicit val ec: ExecutionContext

  val credentialsProvider: CsrCredentialsProvider
  val authenticatorService: SessionAuthenticatorService
  val userService: UserCacheService
  def identityService: UserCacheService
}
