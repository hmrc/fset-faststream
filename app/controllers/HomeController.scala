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
import config.CSRCache
import connectors.ApplicationClient
import connectors.ApplicationClient.{ ApplicationNotFound, CannotWithdraw, OnlineTestNotFound }
import connectors.exchange._
import helpers.NotificationType._
import models.ApplicationData.ApplicationStatus
import models.page.{ DashboardPage, Phase1TestsPage, Phase2TestsPage, Phase3TestsPage }
import models.{ ApplicationData, CachedData }
import play.api.mvc.{ Request, Result }
import security.Roles
import security.RoleUtils._
import security.Roles._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

object HomeController extends HomeController(ApplicationClient, CSRCache) {
  val appRouteConfigMap = config.FrontendAppConfig.applicationRoutesFrontend
}

abstract class HomeController(applicationClient: ApplicationClient, cacheClient: CSRCache)
  extends BaseController(applicationClient, cacheClient) with CampaignAwareController {
  val Withdrawer = "Candidate"

  def present(implicit displaySdipEligibilityInfo: Boolean = false) = CSRSecureAction(ActiveUserRole) {
    implicit request => implicit cachedData =>
     cachedData.application.map { implicit application =>
       isPhase3TestsPassed match {
         case true => displayFinalResultsPage
         case _ => dashboardWithOnlineTests.recoverWith(dashboardWithoutOnlineTests)
       }
     }.getOrElse { dashboardWithoutApplication }
  }

  def resume = CSRSecureAppAction(ActiveUserRole) { implicit request =>
    implicit user =>
      Future.successful(Redirect(Roles.userJourneySequence.find(_._1.isAuthorized(user)).map(_._2).getOrElse(routes.HomeController.present())))
  }

  def create = CSRSecureAction(ApplicationStartRole) { implicit request =>
    implicit user =>
      for {
        response <- applicationClient.findApplication(user.user.userID, FrameworkId).recoverWith {
          case _: ApplicationNotFound => applicationClient.createApplication(user.user.userID, FrameworkId)
        }
        _ <- env.userService.save(user.copy(application = Some(response)))
        if isSubmitApplicationsEnabled(response.applicationRoute)
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
        Redirect(controllers.routes.HomeController.present()).flashing(success("success.allocation.confirmed"))
      }
  }

  private def displayFinalResultsPage(implicit application: ApplicationData, cachedData: CachedData,
                                      request: Request[_], hc: HeaderCarrier) =
    applicationClient.getFinalSchemeResults(application.applicationId).map { results =>
      Ok(views.html.home.viewFinalResults(cachedData, results.getOrElse(Nil)))
    }

  private def dashboardWithOnlineTests(implicit application: ApplicationData,
                                       displaySdipEligibilityInfo: Boolean,
                                       cachedData: CachedData, request: Request[_]) = {
    for {
      adjustmentsOpt <- getAdjustments
      assistanceDetailsOpt <- getAssistanceDetails
      phase1TestsWithNames <- applicationClient.getPhase1TestProfile(application.applicationId)
      phase2TestsWithNames <- getPhase2Test
      phase3Tests <- getPhase3Test
      updatedData <- env.userService.refreshCachedUser(cachedData.user.userID)(hc, request)
    } yield {
      val dashboardPage = DashboardPage(updatedData, Some(Phase1TestsPage(phase1TestsWithNames)),
        phase2TestsWithNames.map(Phase2TestsPage(_, adjustmentsOpt)),
        phase3Tests.map(Phase3TestsPage(_, adjustmentsOpt))
      )
      Ok(views.html.home.dashboard(updatedData, dashboardPage, assistanceDetailsOpt, adjustmentsOpt,
        submitApplicationsEnabled = true, displaySdipEligibilityInfo))
    }
  }

  private def dashboardWithoutOnlineTests(implicit application: ApplicationData,
                                          displaySdipEligibilityInfo: Boolean,
                                          cachedData: CachedData,
                                          request: Request[_]):PartialFunction[Throwable, Future[Result]] = {
    case e: OnlineTestNotFound =>
      val applicationSubmitted = !cachedData.application.forall { app =>
        app.applicationStatus == ApplicationStatus.CREATED || app.applicationStatus == ApplicationStatus.IN_PROGRESS
      }
      val isDashboardEnabled = isSubmitApplicationsEnabled(application.applicationRoute) || applicationSubmitted
      val dashboardPage = DashboardPage(cachedData, None, None, None)
      Future.successful(Ok(views.html.home.dashboard(cachedData, dashboardPage,
        submitApplicationsEnabled = isDashboardEnabled,
        displaySdipEligibilityInfo = displaySdipEligibilityInfo)))
  }

  private def dashboardWithoutApplication(implicit cachedData: CachedData,
                                          displaySdipEligibilityInfo: Boolean,
                                          request: Request[_]) = {
      val dashboardPage = DashboardPage(cachedData, None, None, None)
      Future.successful(
        Ok(views.html.home.dashboard(cachedData, dashboardPage,
          submitApplicationsEnabled = isSubmitApplicationsEnabled,
          displaySdipEligibilityInfo = displaySdipEligibilityInfo))
      )
  }

  private def getPhase2Test(implicit application: ApplicationData, hc: HeaderCarrier) = application.isPhase2 match {
    case true => applicationClient.getPhase2TestProfile(application.applicationId).map(Some(_))
    case false => Future.successful(None)
  }

  private def getPhase3Test(implicit application: ApplicationData, hc: HeaderCarrier) = application.isPhase3 match {
    case true => applicationClient.getPhase3TestGroup(application.applicationId).map(Some(_))
    case false => Future.successful(None)
  }

  private def getAdjustments(implicit application: ApplicationData, hc: HeaderCarrier) =
  application.progress.assistanceDetails match {
    case true => applicationClient.findAdjustments(application.applicationId)
    case false => Future.successful(None)
  }

  private def getAssistanceDetails(implicit application: ApplicationData,
                                   hc: HeaderCarrier, cachedData: CachedData) =
  application.progress.assistanceDetails match {
    case true => applicationClient.getAssistanceDetails(cachedData.user.userID, application.applicationId).map(a => Some(a))
    case false => Future.successful(None)
  }

}
