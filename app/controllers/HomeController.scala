/*
 * Copyright 2016 HM Revenue & Customs
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

import _root_.forms.WithdrawApplicationForm
import connectors.ApplicationClient
import connectors.ApplicationClient.{ CannotWithdraw, OnlineTestNotFound }
import connectors.exchange.{ FrameworkId, WithdrawApplication }
import helpers.NotificationType._
import models.ApplicationData.ApplicationStatus
import models.page.{ DashboardPage, Phase1TestsPage, Phase2TestsPage }
import models.{ CachedData, CachedDataWithApp }
import security.Roles
import security.Roles._

import scala.concurrent.Future

object HomeController extends HomeController(ApplicationClient)

class HomeController(applicationClient: ApplicationClient) extends BaseController(applicationClient) {
  val Withdrawer = "Candidate"

  val present = CSRSecureAction(ActiveUserRole) { implicit request => implicit cachedData =>
    cachedData.application.map { application =>
      val dashboard = for {
        phase1TestsWithNames <- applicationClient.getPhase1TestProfile(application.applicationId)
        phase2TestsWithNames <- applicationClient.getPhase2TestProfile(application.applicationId)
        allocationDetails <- applicationClient.getAllocationDetails(application.applicationId)
        // TODO Work out a better way to invalidate the cache across the site
        app = CachedDataWithApp(cachedData.user, application)
        updatedData <- refreshCachedUser()(app, hc, request)
      } yield {
        val dashboardPage = DashboardPage(updatedData, allocationDetails, Some(Phase1TestsPage.apply(phase1TestsWithNames)),
          Some(Phase2TestsPage.apply(phase2TestsWithNames))
        )
        Ok(views.html.home.dashboard(updatedData, dashboardPage, allocationDetails))
      }

      dashboard recover {
        case e: OnlineTestNotFound =>
          val applicationSubmitted = !cachedData.application.forall { app =>
            app.applicationStatus == ApplicationStatus.CREATED || app.applicationStatus == ApplicationStatus.IN_PROGRESS
          }
          val isDashboardEnabled = faststreamConfig.applicationsSubmitEnabled || applicationSubmitted

          if (isDashboardEnabled) {
            val dashboardPage = DashboardPage(cachedData, None, None, None)
            Ok(views.html.home.dashboard(cachedData, dashboardPage, None))
          } else {
            Ok(views.html.home.submit_disabled(cachedData))
          }
      }
    }.getOrElse {
      val isDashboardEnabled = faststreamConfig.applicationsSubmitEnabled

      if (isDashboardEnabled) {
        val dashboardPage = DashboardPage(cachedData, None, None, None)
        Future.successful(Ok(views.html.home.dashboard(cachedData, dashboardPage, None)))
      } else {
        Future.successful(Ok(views.html.home.submit_disabled(cachedData)))
      }
    }
  }

  def resume = CSRSecureAppAction(ActiveUserRole) { implicit request =>
    implicit user =>
      Future.successful(Redirect(Roles.userJourneySequence.find(_._1.isAuthorized(user)).map(_._2).getOrElse(routes.HomeController.present())))
  }

  def create = CSRSecureAction(ApplicationStartRole) { implicit request =>
    implicit user =>
      for {
        response <- applicationClient.createApplication(user.user.userID, FrameworkId)
        _ <- env.userService.save(user.copy(application = Some(response)))
        if faststreamConfig.applicationsSubmitEnabled
      } yield {
        Redirect(routes.PersonalDetailsController.presentAndContinue())
      }
  }

  def presentWithdrawApplication = CSRSecureAppAction(WithdrawApplicationRole) { implicit request =>
    implicit user =>
      Future.successful(Ok(views.html.application.withdraw(WithdrawApplicationForm.form)))
  }

  def withdrawApplication = CSRSecureAppAction(WithdrawApplicationRole) { implicit request =>
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

  def confirmAlloc = CSRSecureAction(UnconfirmedAllocatedCandidateRole) { implicit request =>
    implicit user =>

      applicationClient.confirmAllocation(user.application.get.applicationId).map { _ =>
        Redirect(controllers.routes.HomeController.present).flashing(success("success.allocation.confirmed"))
      }
  }
}
