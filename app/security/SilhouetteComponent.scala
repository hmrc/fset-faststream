/*
 * Copyright 2021 HM Revenue & Customs
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

package security

import com.mohiva.play.silhouette.api.actions._
import com.mohiva.play.silhouette.api.{Environment, Silhouette, SilhouetteProvider}
import javax.inject.{Inject, Singleton}
import play.api.i18n.MessagesApi
import play.api.mvc.BodyParsers

import scala.concurrent.ExecutionContext

@Singleton
class SilhouetteComponent @Inject() (
  val messagesApi: MessagesApi,
  val securityEnvironment: config.SecurityEnvironment,
  signInService: SignInService,
  val bodyParser: BodyParsers.Default)(implicit val ec: ExecutionContext)
  extends SecuredActionComponents
    with SecuredErrorHandlerComponents
    with UnsecuredActionComponents
    with UnsecuredErrorHandlerComponents
    with UserAwareActionComponents {

  lazy val silhouette: Silhouette[SecurityEnvironment] = {
    new SilhouetteProvider[SecurityEnvironment](
      environment,
      securedAction,
      unsecuredAction,
      userAwareAction
    )
  }

  override lazy val securedErrorHandler = new CustomSecuredErrorHandler(
    signInService, messagesApi)
  override lazy val securedBodyParser = bodyParser
  override lazy val unsecuredBodyParser = bodyParser
  override lazy val userAwareBodyParser = bodyParser

  val environment: Environment[SecurityEnvironment] = securityEnvironment
}
