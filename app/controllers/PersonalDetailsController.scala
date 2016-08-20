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

import _root_.forms.FastPassForm._
import _root_.forms.GeneralDetailsForm
import connectors.ApplicationClient.PersonalDetailsNotFound
import connectors.{ ApplicationClient, UserManagementClient }
import mappings.{ Address, DayMonthYear }
import models.ApplicationData.ApplicationStatus._
import org.joda.time.LocalDate
import security.Roles.PersonalDetailsRole

import scala.concurrent.Future

object PersonalDetailsController extends PersonalDetailsController(ApplicationClient, UserManagementClient)

class PersonalDetailsController(applicationClient: ApplicationClient, userManagementClient: UserManagementClient)
  extends BaseController(applicationClient) {

  def present(start: Option[String] = None) = CSRSecureAppAction(PersonalDetailsRole) { implicit request =>
    implicit user =>
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
          gd.phone,
          gd.fastPassDetails
        ))
        Ok(views.html.application.generalDetails(form))

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
            phone = None,
            fastPassDetails = EmptyFastPassDetails
          ))
          Ok(views.html.application.generalDetails(formFromUser))
      }
  }

  def submitGeneralDetails = CSRSecureAppAction(PersonalDetailsRole) { implicit request =>
    implicit user =>
      implicit val now: LocalDate = LocalDate.now
      GeneralDetailsForm.form.bindFromRequest.fold(
        errorForm => {
          Future.successful(Ok(views.html.application.generalDetails(GeneralDetailsForm.form.
            bind(errorForm.data.cleanupFastPassFields()))))
        },
        gd => {
          for {
            _ <- applicationClient.updateGeneralDetails(user.application.applicationId, user.user.userID,
              removePostCodeWhenOutsideUK(gd).toExchange(user.user.email))
            _ <- userManagementClient.updateDetails(user.user.userID, gd.firstName, gd.lastName, Some(gd.preferredName))
            redirect <- updateProgress(data => data.copy(user = user.user.copy(firstName = gd.firstName, lastName = gd.lastName,
              preferredName = Some(gd.preferredName)), application = data.application.map(_.copy(applicationStatus = IN_PROGRESS))
            ))(_ => Redirect(routes.SchemePreferencesController.present()))
          } yield {
            redirect
          }
        }
      )
  }

  private def removePostCodeWhenOutsideUK(generalDetails: GeneralDetailsForm.Data) =
    if (generalDetails.outsideUk.getOrElse(false)) generalDetails.copy(postCode = None) else generalDetails
}
