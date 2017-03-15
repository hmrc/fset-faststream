/*
 * Copyright 2017 HM Revenue & Customs
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

import com.mohiva.play.silhouette.api.{ Environment, Silhouette, SilhouetteProvider }
import com.mohiva.play.silhouette.api.actions._
import config.SecurityEnvironmentImpl
import play.api.Play
import play.api.i18n.MessagesApi

object SilhouetteComponent extends SilhouetteComponent {
  lazy val silhouette: Silhouette[SecurityEnvironment] = {
    new SilhouetteProvider[SecurityEnvironment](
      environment,
      securedAction,
      unsecuredAction,
      userAwareAction
    )
  }
}

trait SilhouetteComponent
  extends SecuredActionComponents
    with SecuredErrorHandlerComponents
    with UnsecuredActionComponents
    with UnsecuredErrorHandlerComponents
    with UserAwareActionComponents {

  def messagesApi: MessagesApi = Play.current.injector.instanceOf(classOf[MessagesApi])

  override lazy val securedErrorHandler = new CustomSecuredErrorHandler(messagesApi)
  val environment: Environment[SecurityEnvironment] = SecurityEnvironmentImpl
}
