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

package controllers

import _root_.forms.SignUpForm
import _root_.forms.SignUpForm._
import com.mohiva.play.silhouette.api.SignUpEvent
import config.{ CSRCache, CSRHttp }
import connectors.ApplicationClient
import connectors.UserManagementClient.EmailTakenException
import connectors.exchange._
import helpers.NotificationType._
import models.{ ApplicationRoute, SecurityUser }
import play.api.i18n.Messages
import play.api.mvc.Result
import security.SignInService

import scala.concurrent.Future

object SignUpController extends SignUpController(ApplicationClient, CSRCache) {
  val http = CSRHttp
  val appRouteConfigMap = config.FrontendAppConfig.applicationRoutesFrontend
}

abstract class SignUpController(val applicationClient: ApplicationClient, cacheClient: CSRCache)
  extends BaseController(applicationClient, cacheClient) with SignInService with CampaignAwareController {

  def present = CSRUserAwareAction { implicit request =>
    implicit user =>
      Future.successful(request.identity match {
        case Some(_) => Redirect(routes.HomeController.present()).flashing(warning("activation.already"))
        case None => Ok(views.html.registration.signup(SignUpForm.form, appRouteConfigMap))
      })
  }

  def signUp = CSRUserAwareAction { implicit request =>
    implicit user =>

      def checkAppWindowBeforeProceeding (data: Map[String, String], fn: => Future[Result]) =
        data.get("applicationRoute").map(ApplicationRoute.withName).map {
          case appRoute if !isNewAccountsStarted(appRoute) =>
            Future.successful(Redirect(routes.SignUpController.present()).flashing(warning(
              Messages(s"applicationRoute.$appRoute.notOpen", getApplicationStartDate(appRoute)))))
          case appRoute if !isNewAccountsEnabled(appRoute) =>
            Future.successful(Redirect(routes.SignUpController.present()).flashing(warning(Messages(s"applicationRoute.$appRoute.closed"))))
          case _ => fn
        } getOrElse fn

      SignUpForm.form.bindFromRequest.fold(
        invalidForm => {
          checkAppWindowBeforeProceeding(invalidForm.data, Future.successful(
            Ok(views.html.registration.signup(SignUpForm.form.bind(invalidForm.data.sanitize), appRouteConfigMap)))
          )
        },
        data => {
          val appRoute = ApplicationRoute.withName(data.applicationRoute)
          checkAppWindowBeforeProceeding(SignUpForm.form.fill(data).data, {
              env.register(data.email.toLowerCase, data.password, data.firstName, data.lastName).flatMap { u =>
                applicationClient.addReferral(u.userId, extractMediaReferrer(data)).flatMap { _ =>
                  applicationClient.createApplication(u.userId, FrameworkId, appRoute).flatMap { appResponse =>
                    signInUser(
                      u.toCached,
                      redirect = Redirect(routes.ActivationController.present()).flashing(success("account.successful")),
                      env = env
                    ).map { r =>
                      env.eventBus.publish(SignUpEvent(SecurityUser(u.userId.toString()), request, request2lang))
                      r
                    }
                  }
                }
              }.recover {
                case e: EmailTakenException =>
                  Ok(views.html.registration.signup(SignUpForm.form.fill(data), appRouteConfigMap, Some(danger("user.exists"))))
              }
          })
        }
      )
  }

  private def extractMediaReferrer(data: SignUpForm.Data): String = {
    if (data.campaignReferrer.contains("Other")) {
      data.campaignOther.getOrElse("")
    } else {
      data.campaignReferrer.getOrElse("")
    }
  }
}
