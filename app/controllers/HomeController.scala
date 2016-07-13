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
import config.CSRHttp
import connectors.ApplicationClient.{ CannotWithdraw, OnlineTestNotFound }
import connectors.ExchangeObjects.WithdrawApplicationRequest
import connectors.{ ApplicationClient, ExchangeObjects }
import helpers.NotificationType._
import models.ApplicationData.ApplicationStatus
import models.page.DashboardPage
import models.{ CachedData, CachedDataWithApp }
import security.Roles
import security.Roles._

import scala.concurrent.Future

object HomeController extends HomeController {
  val http = CSRHttp
}

trait HomeController extends BaseController with ApplicationClient {
  val Withdrawer = "Candidate"
  val present = CSRSecureAction(ActiveUserRole) { implicit request =>
    implicit user =>
      // TODO: I think the non existance of the onlineTest can be handled with an Option
      // not an Exception. It is not really an exceptional situation as in most situations
      // the onlineTest will not be present until later.
      getTestAssessment(user.user.userID).flatMap { onlineTest =>
        getAllocationDetails(user.application.get.applicationId).flatMap { allocationDetails =>
          // It is possible that the scheduler may have enabled testing, but that the
          // current user session has older cached user data, so force an update
          // TODO Work out a better way to invalidate the cache across the site
          implicit val app = CachedDataWithApp(user.user, user.application.get)
          refreshCachedUser().map { updatedData =>
            // The application must exist if the user is invited
            implicit val appOpt = Some(updatedData)
            val dashboardPage = DashboardPage.fromUser(updatedData)

            Ok(views.html.home.dashboard(updatedData, dashboardPage, Some(onlineTest), allocationDetails))
          }
        }
      } recover {
        case e: OnlineTestNotFound =>
          val applicationSubmitted = !user.application.forall { app =>
            app.applicationStatus == ApplicationStatus.CREATED || app.applicationStatus == ApplicationStatus.IN_PROGRESS
          }
          val isDashboardEnabled = fasttrackConfig.applicationsSubmitEnabled || applicationSubmitted

          if (isDashboardEnabled) {
            val dashboardPage = DashboardPage.fromUser(user)
            Ok(views.html.home.dashboard(user, dashboardPage, None, None))
          } else {
            Ok(views.html.home.submit_disabled(user))
          }
      }

  }

  val resume = CSRSecureAppAction(ActiveUserRole) { implicit request =>
    implicit user =>
      Future.successful(Redirect(Roles.userJourneySequence.find(_._1.isAuthorized(user)).map(_._2).getOrElse(routes.HomeController.present())))
  }

  val create = CSRSecureAction(ApplicationStartRole) { implicit request =>
    implicit user =>
      for {
        response <- createApplication(user.user.userID, ExchangeObjects.frameworkId)
        _ <- env.userService.save(user.copy(application = Some(response)))
        if fasttrackConfig.applicationsSubmitEnabled
      } yield {
        Redirect(routes.FastTrackApplication.generalDetails(Some("start")))
      }
  }

  val presentWithDraw = CSRSecureAppAction(WithdrawApplicationRole) { implicit request =>
    implicit user =>
      Future.successful(
        Ok(views.html.application.withdraw(WithdrawApplicationForm.form))
      )
  }

  val withdraw = CSRSecureAppAction(WithdrawApplicationRole) { implicit request =>
    implicit user =>

      def updateStatus(data: CachedData) = data.copy(application =
        data.application.map(_.copy(applicationStatus = ApplicationStatus.WITHDRAWN)))

      WithdrawApplicationForm.form.bindFromRequest.fold(
        invalidForm => Future.successful(Ok(views.html.application.withdraw(invalidForm))),
        data => {
          withdrawApplication(user.application.applicationId, WithdrawApplicationRequest(data.reason, data.otherReason,
            Withdrawer)).flatMap { _ =>
            updateProgress(updateStatus)(_ => Redirect(routes.HomeController.present()).flashing(success("application.withdrawn")))
          }.recover {
            case _: CannotWithdraw => Redirect(routes.HomeController.present()).flashing(danger("error.cannot.withdraw"))
          }
        }
      )
  }

  val confirmAlloc = CSRSecureAction(UnconfirmedAllocatedCandidateRole) { implicit request =>
    implicit user =>

      confirmAllocation(user.application.get.applicationId).map { _ =>
        Redirect(controllers.routes.HomeController.present).flashing(success("success.allocation.confirmed"))
      }
  }
}
