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

import java.nio.file.{ Files, Path }

import com.mohiva.play.silhouette.api.Silhouette
import config.CSRCache
import connectors.{ ApplicationClient, ReferenceDataClient, SiftClient }
import connectors.ApplicationClient.{ ApplicationNotFound, CandidateAlreadyHasAnAnalysisExerciseException, CannotWithdraw, OnlineTestNotFound }
import connectors.exchange._
import forms.WithdrawApplicationForm
import helpers.NotificationType._
import models.ApplicationData.ApplicationStatus
import models.page._
import models._
import models.events.EventType
import play.api.Logger
import play.api.mvc.{ Action, AnyContent, Request, Result }
import security.RoleUtils._
import security.{ Roles, SecurityEnvironment, SilhouetteComponent }
import security.Roles._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import play.api.i18n.Messages.Implicits._
import play.api.Play.current

object WithdrawController extends WithdrawController(
  ApplicationClient,
  ReferenceDataClient,
  SiftClient,
  CSRCache
) {
  val appRouteConfigMap: Map[ApplicationRoute.Value, ApplicationRouteStateImpl] = config.FrontendAppConfig.applicationRoutesFrontend
  lazy val silhouette: Silhouette[SecurityEnvironment] = SilhouetteComponent.silhouette
}

abstract class WithdrawController(
  applicationClient: ApplicationClient,
  refDataClient: ReferenceDataClient,
  cacheClient: CSRCache
) extends BaseController(applicationClient, cacheClient) with CampaignAwareController {

  val Withdrawer = "Candidate"

  def presentWithdrawApplication: Action[AnyContent] = CSRSecureAppAction(AbleToWithdrawApplicationRole) { implicit request =>
    implicit user =>
      Future.successful(Ok(views.html.application.withdraw(WithdrawApplicationForm.form)))
  }

  def presentWithdrawScheme: Action[AnyContent] = CSRSecureAppAction(SchemeWithdrawRole) { implicit request =>
    implicit user =>
    Future(Ok(""))
  }

  def withdrawApplication: Action[AnyContent] = CSRSecureAppAction(AbleToWithdrawApplicationRole) { implicit request =>
    implicit user =>

      def updateApplicationStatus(data: CachedData): CachedData = {
        data.copy(application = data.application.map { app =>
          app.copy(
            applicationStatus = ApplicationStatus.WITHDRAWN,
            progress = app.progress.copy(withdrawn = true)
          )
        }
        )
      }

      WithdrawApplicationForm.form.bindFromRequest.fold(
        invalidForm => Future.successful(Ok(views.html.application.withdraw(invalidForm))),
        data => {
          applicationClient.withdrawApplication(user.application.applicationId, WithdrawApplication(data.reason.get, data.otherReason,
            Withdrawer)).flatMap { _ =>
            updateProgress(updateApplicationStatus)(_ =>
              Redirect(routes.HomeController.present()).flashing(success("application.withdrawn", feedbackUrl)))
          }.recover {
            case _: CannotWithdraw => Redirect(routes.HomeController.present()).flashing(danger("error.cannot.withdraw"))
          }
        }
      )
  }
}
