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

import _root_.forms.PersonalDetailsForm
import connectors.ApplicationClient.PersonalDetailsNotFound
import connectors.exchange.CivilServiceExperienceDetails._
import connectors.exchange.{ CivilServiceExperienceDetails, SelectedSchemes }
import connectors.{ ApplicationClient, ReferenceDataClient, SchemeClient, UserManagementClient }
import forms.FastPassForm._
import helpers.NotificationType._
import mappings.{ Address, DayMonthYear }
import models.{ ApplicationRoute, CachedDataWithApp }
import org.joda.time.LocalDate
import play.api.Play.current
import play.api.data.Form
import play.api.i18n.Messages.Implicits._
import play.api.mvc.{ Request, Result }
import security.Roles.{ EditPersonalDetailsAndContinueRole, EditPersonalDetailsRole }
import security.SilhouetteComponent
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

object PersonalDetailsController extends PersonalDetailsController(ApplicationClient, SchemeClient, UserManagementClient, ReferenceDataClient) {
  lazy val silhouette = SilhouetteComponent.silhouette
}

abstract class PersonalDetailsController(applicationClient: ApplicationClient,
                                         schemeClient: SchemeClient,
                                         userManagementClient: UserManagementClient,
                                         refDataClient: ReferenceDataClient)
  extends BaseController with PersonalDetailsToExchangeConverter {

  private sealed trait OnSuccess
  private case object ContinueToNextStepInJourney extends OnSuccess
  private case object RedirectToTheDashboard extends OnSuccess

  def presentAndContinue = CSRSecureAppAction(EditPersonalDetailsRole) { implicit request =>
    implicit user =>
      personalDetails(afterSubmission = ContinueToNextStepInJourney)
  }

  def present = CSRSecureAppAction(EditPersonalDetailsRole) { implicit request =>
    implicit user =>
      personalDetails(afterSubmission = RedirectToTheDashboard)
  }

  private def getCivilServantSchemeNamesRequiringQualifications(implicit hc: HeaderCarrier) = {
    for {
      allSchemes <- refDataClient.allSchemes()
    } yield {
      allSchemes.collect{ case s if !s.civilServantEligible && s.degree.isDefined => s.name }
    }
  }

  private def personalDetails(afterSubmission: OnSuccess)
                             (implicit user: CachedDataWithApp, hc: HeaderCarrier, request: Request[_]): Future[Result] = {
    implicit val now: LocalDate = LocalDate.now
    val continueToTheNextStep = continuetoTheNextStep(afterSubmission)

    (for {
      schemesRequiringQualifications <- getCivilServantSchemeNamesRequiringQualifications
      gd <- applicationClient.getPersonalDetails(user.user.userID, user.application.applicationId)
    } yield {
      val form = PersonalDetailsForm.form.fill(PersonalDetailsForm.Data(
        gd.firstName,
        gd.lastName,
        gd.preferredName,
        gd.dateOfBirth,
        Some(gd.outsideUk),
        gd.address,
        gd.postCode,
        gd.country,
        gd.phone,
        gd.civilServiceExperienceDetails,
        gd.edipCompleted.map(_.toString)
      ))
      Future.successful(Ok(views.html.application.personalDetails(form, continueToTheNextStep, schemesRequiringQualifications)))
    }).recover {
      case _: PersonalDetailsNotFound =>
        getCivilServantSchemeNamesRequiringQualifications.map{ schemesRequiringQualifications =>
          val formFromUser = PersonalDetailsForm.form.fill(PersonalDetailsForm.Data(
            user.user.firstName,
            user.user.lastName,
            user.user.firstName,
            DayMonthYear.emptyDate,
            outsideUk = None,
            address = Address.EmptyAddress,
            postCode = None,
            country = None,
            phone = None,
            civilServiceExperienceDetails = EmptyCivilServiceExperienceDetails,
            edipCompleted = None
          ))
          Ok(views.html.application.personalDetails(formFromUser, continueToTheNextStep, schemesRequiringQualifications))
        }
    }.flatMap( identity )
  }

  def submitPersonalDetailsAndContinue() = CSRSecureAppAction(EditPersonalDetailsAndContinueRole) { implicit request =>
    implicit user =>
      val redirect = if(user.application.applicationRoute == ApplicationRoute.Faststream ||
      user.application.applicationRoute == ApplicationRoute.SdipFaststream) {
        Redirect(routes.SchemePreferencesController.present())
      } else {
        Redirect(routes.AssistanceDetailsController.present())
      }
      submit(PersonalDetailsForm.form(LocalDate.now), ContinueToNextStepInJourney, redirect)
  }

  def submitPersonalDetails() = CSRSecureAppAction(EditPersonalDetailsRole) { implicit request =>
    implicit user =>
      submit(PersonalDetailsForm.form(LocalDate.now, ignoreFastPassValidations = true), RedirectToTheDashboard,
        Redirect(routes.HomeController.present()).flashing(success("personalDetails.updated")))
  }

  private def continuetoTheNextStep(onSuccess: OnSuccess) = onSuccess match {
    case ContinueToNextStepInJourney => true
    case RedirectToTheDashboard => false
  }

  private def submit(personalDetailsForm: Form[PersonalDetailsForm.Data], onSuccess: OnSuccess, redirectOnSuccess: Result)
                    (implicit cachedData: CachedDataWithApp, hc: HeaderCarrier, request: Request[_]) = {

    val handleFormWithErrors = (errorForm:Form[PersonalDetailsForm.Data]) => {
      getCivilServantSchemeNamesRequiringQualifications.map { schemesRequiringQualifications =>
        Ok(views.html.application.personalDetails(
          personalDetailsForm.bind(errorForm.data.cleanupFastPassFields), continuetoTheNextStep(onSuccess),
          schemesRequiringQualifications)
        )
      }
    }

    val handleValidForm = (form: PersonalDetailsForm.Data) => {
      val civilServiceExperienceDetails: Option[CivilServiceExperienceDetails] =
        cachedData.application.civilServiceExperienceDetails.orElse(form.civilServiceExperienceDetails)
      val edipCompleted = cachedData.application.edipCompleted.orElse(form.edipCompleted.map(_.toBoolean))
      for {
        _ <- applicationClient.updatePersonalDetails(cachedData.application.applicationId, cachedData.user.userID,
          toExchange(form, cachedData.user.email, Some(continuetoTheNextStep(onSuccess)), edipCompleted))
        _ <- createDefaultSchemes
        _ <- userManagementClient.updateDetails(cachedData.user.userID, form.firstName, form.lastName, Some(form.preferredName))
      } yield {
        redirectOnSuccess
      }
    }
    personalDetailsForm.bindFromRequest.fold(handleFormWithErrors, handleValidForm)
  }

  private def createDefaultSchemes(implicit cacheData: CachedDataWithApp, hc: HeaderCarrier, request: Request[_]): Future[Unit] =
    cacheData.application.applicationRoute match {
    case appRoute@(ApplicationRoute.Edip | ApplicationRoute.Sdip) =>
      for {
        _ <- schemeClient.updateSchemePreferences(SelectedSchemes(List(appRoute), orderAgreed = true,
          eligible = true))(cacheData.application.applicationId)
        _ <- env.userService.refreshCachedUser(cacheData.user.userID)
      } yield ()
    case _ => Future.successful(())
  }
}

trait PersonalDetailsToExchangeConverter {

  def toExchange(personalDetails: PersonalDetailsForm.Data, email: String, updateApplicationStatus: Option[Boolean],
                 edipCompleted: Option[Boolean] = None) = {
    val pd = personalDetails.insideUk match {
      case true => personalDetails.copy(country = None)
      case false => personalDetails.copy(postCode = None)
    }
    pd.toExchange(email, updateApplicationStatus, edipCompleted)
  }
}
