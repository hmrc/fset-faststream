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

import _root_.forms.GeneralDetailsForm
import connectors.ApplicationClient.PersonalDetailsNotFound
import connectors.{ ApplicationClient, UserManagementClient }
import helpers.NotificationType._
import mappings.{ Address, DayMonthYear }
import models.ApplicationData.ApplicationStatus._
import models.CachedDataWithApp
import org.joda.time.LocalDate
import play.api.mvc.{ Request, Result }
import security.Roles.{ EditPersonalDetailsAndContinueRole, EditPersonalDetailsRole }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

object PersonalDetailsController extends PersonalDetailsController(ApplicationClient, UserManagementClient)

class PersonalDetailsController(applicationClient: ApplicationClient, userManagementClient: UserManagementClient)
  extends BaseController(applicationClient) {

  def presentAndContinue = CSRSecureAppAction(EditPersonalDetailsRole) { implicit request =>
    implicit user =>
      personalDetails(continue = true)
  }

  def present = CSRSecureAppAction(EditPersonalDetailsRole) { implicit request =>
    implicit user =>
      personalDetails(continue = false)
  }

  private def personalDetails(continue: Boolean)(implicit user: CachedDataWithApp, hc: HeaderCarrier, request: Request[_]): Future[Result] = {
    implicit val now: LocalDate = LocalDate.now
    applicationClient.getPersonalDetails(user.user.userID, user.application.applicationId).map { gd =>
      val form = GeneralDetailsForm.form.fill(GeneralDetailsForm.Data(
        gd.firstName,
        gd.lastName,
        gd.preferredName,
        gd.dateOfBirth,
        Some(gd.outsideUk),
        gd.address,
        gd.postCode,
        gd.phone
      ))
      Ok(views.html.application.generalDetails(form, continue))

    }.recover {
      case e: PersonalDetailsNotFound =>
        val formFromUser = GeneralDetailsForm.form.fill(GeneralDetailsForm.Data(
          user.user.firstName,
          user.user.lastName,
          user.user.firstName,
          DayMonthYear.emptyDate,
          outsideUk = None,
          address = Address.EmptyAddress,
          postCode = None,
          phone = None
        ))
        Ok(views.html.application.generalDetails(formFromUser, continue))
    }
  }

  def submitGeneralDetailsAndContinue() = CSRSecureAppAction(EditPersonalDetailsAndContinueRole) { implicit request =>
    implicit user =>
      submit(updateStatus = true, Redirect(routes.SchemePreferencesController.present()))
  }

  def submitGeneralDetails() = CSRSecureAppAction(EditPersonalDetailsRole) { implicit request =>
    implicit user =>
      submit(updateStatus = false, Redirect(routes.HomeController.present()).flashing(success("personalDetails.updated")))
  }

  private def submit(updateStatus: Boolean, redirectOnSuccess: Result)(
    implicit user: CachedDataWithApp, hc: HeaderCarrier, request: Request[_]) = {
    implicit val now: LocalDate = LocalDate.now
    GeneralDetailsForm.form.bindFromRequest.fold(
      errorForm => Future.successful(Ok(views.html.application.generalDetails(errorForm, updateStatus))),
      gd => for {
        _ <- applicationClient.updateGeneralDetails(user.application.applicationId, user.user.userID,
          removePostCodeWhenOutsideUK(gd), user.user.email, updateStatus)
        _ <- userManagementClient.updateDetails(user.user.userID, gd.firstName, gd.lastName, Some(gd.preferredName))
        redirect <- updateProgress(data => data.copy(user = user.user.copy(firstName = gd.firstName, lastName = gd.lastName,
          preferredName = Some(gd.preferredName)), application =
          if (updateStatus) data.application.map(_.copy(applicationStatus = IN_PROGRESS)) else data.application
        ))(_ => redirectOnSuccess)
      } yield {
        redirect
      }
    )
  }

  private def removePostCodeWhenOutsideUK(generalDetails: GeneralDetailsForm.Data): GeneralDetailsForm.Data =
    if (generalDetails.outsideUk.getOrElse(false)) generalDetails.copy(postCode = None) else generalDetails
}
