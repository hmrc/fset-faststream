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

package controllers

import _root_.forms.SignUpForm
import _root_.forms.SignUpForm._
import com.mohiva.play.silhouette.api.SignUpEvent
import config.{ FrontendAppConfig, SecurityEnvironment }
import connectors.UserManagementClient.EmailTakenException
import connectors.exchange._
import connectors.exchange.campaignmanagement.AfterDeadlineSignupCodeUnused
import connectors.{ ApplicationClient, UserManagementClient }
import helpers.NotificationType._
import helpers.NotificationTypeHelper
import javax.inject.{ Inject, Singleton }
import models.{ ApplicationRoute, SecurityUser, UniqueIdentifier }
import play.api.i18n.Messages
import play.api.mvc.{ Action, AnyContent, MessagesControllerComponents, Result }
import security.{ SignInService, SilhouetteComponent }
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class SignUpController @Inject() (
  config: FrontendAppConfig,
  mcc: MessagesControllerComponents,
  val secEnv: SecurityEnvironment,
  val silhouetteComponent: SilhouetteComponent,
  val notificationTypeHelper: NotificationTypeHelper,
  signInService: SignInService,
  applicationClient: ApplicationClient,
  userManagementClient: UserManagementClient,
  formWrapper: SignUpForm)(implicit val ec: ExecutionContext)
  extends BaseController(config, mcc) with CampaignAwareController {
  val appRouteConfigMap = config.applicationRoutesFrontend
  import notificationTypeHelper._

  private def signupCodeUnusedAndValid(signupCode: Option[String])
                               (implicit hc: HeaderCarrier): Future[AfterDeadlineSignupCodeUnused] = signupCode.map(sCode =>
    applicationClient.afterDeadlineSignupCodeUnusedAndValid(sCode)
  ).getOrElse(Future.successful(AfterDeadlineSignupCodeUnused(unused = false)))

  def present(signupCode: Option[String] = None): Action[AnyContent] = CSRUserAwareAction { implicit request => implicit user =>

    val signupCodeValid: Future[Boolean] = signupCodeUnusedAndValid(signupCode).map(_.unused)

    signupCodeValid.map { sCodeValid =>
      request.identity match {
        case Some(_) => Redirect(routes.HomeController.present()).flashing(warning("activation.already"))
        case None => Ok(views.html.registration.signup(formWrapper.form, appRouteConfigMap, None, signupCode, sCodeValid))
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

      formWrapper.form.bindFromRequest.fold(
        invalidForm => {
          checkAppWindowBeforeProceeding(invalidForm.data, Future.successful(
            Ok(views.html.registration.signup(formWrapper.form.bind(invalidForm.data.sanitize), appRouteConfigMap)))
          )
        },
        data => {
          val selectedAppRoute = ApplicationRoute.withName(data.applicationRoute)
          val appRoute = (selectedAppRoute, data.sdipFastStreamConsider) match {
            case (ApplicationRoute.Faststream, Some(true)) => ApplicationRoute.SdipFaststream
            case (_, _) => selectedAppRoute
          }
          checkAppWindowBeforeProceeding(formWrapper.form.fill(data).data, {
            (for {
              u <- userManagementClient.register(data.email.toLowerCase, data.password, data.firstName, data.lastName)
              _ <- applicationClient.addReferral(u.userId, extractMediaReferrer(data))
              appResponse <- applicationClient.createApplication(u.userId, FrameworkId, appRoute)
              sCode <- signupCodeUnusedValue
              _ <- overrideSubmissionDeadlineAndMarkUsedIfSignupCodeValid(appResponse.applicationId, sCode)
            } yield {
              signInService.signInUser(
                u.toCached,
                redirect = Redirect(routes.ActivationController.present).flashing(success("account.successful"))
              ).map { r =>
                secEnv.eventBus.publish(SignUpEvent(SecurityUser(u.userId.toString()), request))
                r
              }
            }).flatMap(identity)
            }).recover {
                case e: EmailTakenException =>
                  Ok(views.html.registration.signup(
                    formWrapper.form.fill(data),
                    appRouteConfigMap,
                    Some(danger("user.exists"))))
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
