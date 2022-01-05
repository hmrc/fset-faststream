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

package security

import com.mohiva.play.silhouette.api.actions.{ SecuredErrorHandler, SecuredRequest }
import controllers.routes
import javax.inject.Inject
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc.Results.Redirect
import play.api.mvc.{ AnyContent, RequestHeader, Result }
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.Future

class CustomSecuredErrorHandler @Inject() (signInService: SignInService,
  val messagesApi: MessagesApi) extends SecuredErrorHandler with I18nSupport {

  override def onNotAuthenticated(implicit request: RequestHeader): Future[Result] =
    Future.successful(Redirect(routes.SignInController.present()))

  override def onNotAuthorized(implicit request: RequestHeader): Future[Result] = {
    val sec = request.asInstanceOf[SecuredRequest[SecurityEnvironment, AnyContent]]
    val headerCarrier = HeaderCarrierConverter.fromHeadersAndSession(sec.headers, Some(sec.session))
    signInService.notAuthorised(request, headerCarrier)
  }
}
