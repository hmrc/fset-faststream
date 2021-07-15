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

package config

import javax.inject.Inject
import play.api.i18n.{Messages, MessagesApi}
import play.api.mvc.{Request, RequestHeader}
import play.twirl.api.Html
import uk.gov.hmrc.play.bootstrap.frontend.http.FrontendErrorHandler

import scala.language.implicitConversions

class FaststreamErrorHandler @Inject() (
  val messagesApi: MessagesApi,
  val config: FrontendAppConfig) extends FrontendErrorHandler {

  private implicit def rhToRequest(rh: RequestHeader): Request[_] = Request(rh, "")

  override def standardErrorTemplate(pageTitle: String, heading: String, message: String)(implicit rh: Request[_]): Html = {
    val messages = implicitly[Messages]
    views.html.error_template(pageTitle, heading, message)(
      rh, config.feedbackUrl, config.trackingConsentConfig, messages)
  }
}
