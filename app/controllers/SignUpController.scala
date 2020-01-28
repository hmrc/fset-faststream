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

package controllers

import _root_.forms.SignUpForm
import _root_.forms.SignUpForm._
import com.mohiva.play.silhouette.api.SignUpEvent
import config.CSRHttp
import connectors.{ ApplicationClient, UserManagementClient }
import connectors.UserManagementClient.EmailTakenException
import connectors.exchange._
import connectors.exchange.campaignmanagement.AfterDeadlineSignupCodeUnused
import helpers.NotificationType._
import models.{ ApplicationRoute, SecurityUser, UniqueIdentifier }
import play.api.Logger
import play.api.i18n.Messages
import play.api.mvc.{ Action, AnyContent, Result }
import security.{ SignInService, SilhouetteComponent }

import scala.concurrent.Future
import play.api.i18n.Messages.Implicits._
import play.api.Play.current
import uk.gov.hmrc.http.HeaderCarrier

object SignUpController extends SignUpController(ApplicationClient, UserManagementClient) {
  val http = CSRHttp
  val appRouteConfigMap = config.FrontendAppConfig.applicationRoutesFrontend
  lazy val silhouette = SilhouetteComponent.silhouette
}

abstract class SignUpController(val applicationClient: ApplicationClient, userManagementClient: UserManagementClient)
  extends BaseController with SignInService with CampaignAwareController {

  private def signupCodeUnusedAndValid(signupCode: Option[String])
                               (implicit hc: HeaderCarrier): Future[AfterDeadlineSignupCodeUnused] = signupCode.map(sCode =>
    applicationClient.afterDeadlineSignupCodeUnusedAndValid(sCode)
  ).getOrElse(Future.successful(AfterDeadlineSignupCodeUnused(unused = false)))

  def present(signupCode: Option[String] = None): Action[AnyContent] = CSRUserAwareAction { implicit request => implicit user =>

    val signupCodeValid: Future[Boolean] = signupCodeUnusedAndValid(signupCode).map(_.unused)

    signupCodeValid.map { sCodeValid =>
      request.identity match {
        case Some(_) => Redirect(routes.HomeController.present()).flashing(warning("activation.already"))
        case None => Ok(views.html.registration.signup(SignUpForm.form, appRouteConfigMap, None, signupCode, sCodeValid))
      }
    }
  }

  // scalastyle:off method.length
  def signUp(signupCode: Option[String]): Action[AnyContent] = CSRUserAwareAction { implicit request =>
    implicit user =>

      val signupCodeUnusedValue: Future[AfterDeadlineSignupCodeUnused] = signupCodeUnusedAndValid(signupCode)
      val signupCodeValid: Future[Boolean] = signupCodeUnusedValue.map(_.unused)

      def checkAppWindowBeforeProceeding (data: Map[String, String], fn: => Future[Result]): Future[Result] =
        signupCodeValid.map { sCodeValid =>
          if (sCodeValid) {
            fn
          } else {
            data.get("applicationRoute").map(ApplicationRoute.withName).map {
              case appRoute if !isNewAccountsStarted(appRoute) =>
                Future.successful(Redirect(routes.SignUpController.present(None)).flashing(warning(
                  Messages(s"applicationRoute.$appRoute.notOpen", getApplicationStartDate(appRoute)))))
              case appRoute if !isNewAccountsEnabled(appRoute) =>
                Future.successful(Redirect(routes.SignUpController.present(None)).flashing(
                  warning(Messages(s"applicationRoute.$appRoute.closed"))
                ))
              case _ => fn
            }.getOrElse(fn)
          }
        }.flatMap(identity)

      def overrideSubmissionDeadlineAndMarkUsedIfSignupCodeValid(applicationId: UniqueIdentifier,
                                                      sCode: AfterDeadlineSignupCodeUnused) = {
        if (sCode.unused) {
          for {
            _ <- applicationClient.overrideSubmissionDeadline(
              applicationId, OverrideSubmissionDeadlineRequest(sCode.expires.get)
            )
            _ <- applicationClient.markSignupCodeAsUsed(signupCode.get, applicationId)
          } yield ()
        } else {
          Future.successful(())
        }
      }

      SignUpForm.form.bindFromRequest.fold(
        invalidForm => {
          checkAppWindowBeforeProceeding(invalidForm.data, Future.successful(
            Ok(views.html.registration.signup(SignUpForm.form.bind(invalidForm.data.sanitize), appRouteConfigMap)))
          )
        },
        data => {
          val selectedAppRoute = ApplicationRoute.withName(data.applicationRoute)
          val appRoute = (selectedAppRoute, data.sdipFastStreamConsider) match {
            case (ApplicationRoute.Faststream, Some(true)) => ApplicationRoute.SdipFaststream
            case (_, _) => selectedAppRoute
          }
          checkAppWindowBeforeProceeding(SignUpForm.form.fill(data).data, {
            (for {
              u <- userManagementClient.register(data.email.toLowerCase, data.password, data.firstName, data.lastName)
              _ <- applicationClient.addReferral(u.userId, extractMediaReferrer(data))
              appResponse <- applicationClient.createApplication(u.userId, FrameworkId, appRoute)
              sCode <- signupCodeUnusedValue
              _ <- overrideSubmissionDeadlineAndMarkUsedIfSignupCodeValid(appResponse.applicationId, sCode)
            } yield {
              signInUser(
                u.toCached,
                redirect = Redirect(routes.ActivationController.present()).flashing(success("account.successful")),
                env = env
              ).map { r =>
                env.eventBus.publish(SignUpEvent(SecurityUser(u.userId.toString()), request))
                r
              }
            }).flatMap(identity)
            }).recover {
                case e: EmailTakenException =>
                  Ok(views.html.registration.signup(SignUpForm.form.fill(data), appRouteConfigMap, Some(danger("user.exists"))))
              }
          })
        }
  // scalastyle:on

  private def extractMediaReferrer(data: SignUpForm.Data): String = {
    if (data.campaignReferrer.contains("Other")) {
      data.campaignOther.getOrElse("")
    } else {
      data.campaignReferrer.getOrElse("")
    }
  }
}
