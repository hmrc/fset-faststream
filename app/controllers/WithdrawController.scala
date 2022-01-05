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

import config.{ FrontendAppConfig, SecurityEnvironment }
import connectors.ApplicationClient.{ CannotWithdraw, SiftExpired }
import connectors.exchange._
import connectors.{ ApplicationClient, ReferenceDataClient }
import forms.{ SchemeWithdrawForm, WithdrawApplicationForm }
import helpers.NotificationType._
import helpers.NotificationTypeHelper
import javax.inject.{ Inject, Singleton }
import models._
import models.page._
import play.api.mvc.{ Action, AnyContent, MessagesControllerComponents }
import security.Roles._
import security.SilhouetteComponent
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class WithdrawController @Inject() (
  config: FrontendAppConfig,
  mcc: MessagesControllerComponents,
  val secEnv: SecurityEnvironment,
  val silhouetteComponent: SilhouetteComponent,
  val notificationTypeHelper: NotificationTypeHelper,
  applicationClient: ApplicationClient,
  refDataClient: ReferenceDataClient,
  formWrapper: WithdrawApplicationForm,
  schemeWithdrawFormWrapper: SchemeWithdrawForm
)(implicit val ec: ExecutionContext) extends BaseController(config, mcc) with CampaignAwareController {
  val appRouteConfigMap: Map[ApplicationRoute.Value, ApplicationRouteState] = config.applicationRoutesFrontend
  import notificationTypeHelper._

  val Withdrawer = "Candidate"

  def presentWithdrawApplication: Action[AnyContent] = CSRSecureAppAction(AbleToWithdrawApplicationRole) { implicit request =>
    implicit user =>
      Future.successful(Ok(views.html.application.withdraw(formWrapper.form)))
  }

  def presentWithdrawScheme: Action[AnyContent] = CSRSecureAppAction(SchemeWithdrawRole) { implicit request =>
    implicit user =>
      getWithdrawableSchemes(user.application.applicationId).map {
        case Nil => Redirect(routes.HomeController.present()).flashing(danger("access.denied"))
        case lastScheme :: Nil => Redirect(routes.WithdrawController.presentWithdrawApplication()).flashing(
          warning("withdraw.scheme.last"))
        case schemes =>
          val page = SchemeWithdrawPage(schemes.map(s => (s.name, s.id.value)),
            schemeWithdrawFormWrapper.form)
          Ok(views.html.home.schemeWithdraw(page))
      }
  }

  private def getWithdrawableSchemes(appId: UniqueIdentifier)(implicit hc: HeaderCarrier) =
    applicationClient.getCurrentSchemeStatus(appId).flatMap { schemesStatus =>
      schemesStatus.filter(_.result == "Green").map(_.schemeId) match {
        case Nil => Future(Nil)
        case schemes => refDataClient.allSchemes.map { refDataSchemes =>
          refDataSchemes.filter(s => schemes.contains(s.id))
        }
      }
    }

  def withdrawScheme = CSRSecureAppAction(SchemeWithdrawRole) { implicit request =>
    implicit user =>
      schemeWithdrawFormWrapper.form.bindFromRequest.fold(
        invalid => getWithdrawableSchemes(user.application.applicationId).map { schemes =>
          Ok(views.html.home.schemeWithdraw(SchemeWithdrawPage(
            schemes.map(s => (s.name, s.id.value)),
            invalid
          )))
        },
        data => refDataClient.allSchemes().flatMap (_.find(_.id.value == data.scheme).map { schemeToWithdraw =>
          applicationClient.withdrawScheme(user.application.applicationId, WithdrawScheme(schemeToWithdraw.id, data.reason)).map { _ =>
            Redirect(routes.HomeController.present()).flashing(success("withdraw.scheme.success"))
          }
        }.getOrElse(Future(Redirect(routes.WithdrawController.presentWithdrawScheme())
          .flashing(danger("withdraw.scheme.invalid", data.scheme)))
        ).recover {
          case _: SiftExpired =>
            Redirect(routes.HomeController.present()).flashing(danger("withdraw.scheme.error", data.scheme))
        })
      )
  }

  def withdrawApplication: Action[AnyContent] = CSRSecureAppAction(AbleToWithdrawApplicationRole) { implicit request =>
    implicit user =>
      formWrapper.form.bindFromRequest.fold(
        invalidForm => Future.successful(Ok(views.html.application.withdraw(invalidForm))),
        data => {
          applicationClient.withdrawApplication(user.application.applicationId, WithdrawApplication(data.reason.get, data.otherReason))
            .map { _ =>
              Redirect(routes.HomeController.present()).flashing(success("application.withdrawn", feedbackUrl))
          }.recover {
            case _: CannotWithdraw => Redirect(routes.HomeController.present()).flashing(danger("error.cannot.withdraw"))
            case _: SiftExpired =>
              Redirect(routes.HomeController.present()).flashing(danger("withdraw.scheme.error", feedbackUrl))
          }
        }
      )
  }
}
