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
import config.CSRHttp
import connectors.ApplicationClient.PersonalDetailsNotFound
import connectors.{ ApplicationClient, UserManagementClient }
import helpers.NotificationType._
import mappings.{ Address, DayMonthYear }
import models.ApplicationData.ApplicationStatus._
import org.joda.time.LocalDate
import security.Roles.PersonalDetailsRole

import scala.concurrent.Future

object FastTrackApplication extends FastTrackApplication {
  val http = CSRHttp
}

trait FastTrackApplication extends BaseController with ApplicationClient with UserManagementClient {

  def generalDetails(start: Option[String] = None) = CSRSecureAppAction(PersonalDetailsRole) { implicit request =>
    implicit user =>
      implicit val now: LocalDate = LocalDate.now

      val formFromUser = GeneralDetailsForm.form.fill(GeneralDetailsForm.Data(
        user.user.firstName,
        user.user.lastName,
        user.user.firstName,
        DayMonthYear("", "", ""),
        Address("", None, None, None),
        "",
        None,
        None,
        None
      ))

      findPersonalDetails(user.user.userID, user.application.applicationId).map { gd =>
        val form = GeneralDetailsForm.form.fill(GeneralDetailsForm.Data(
          gd.firstName,
          gd.lastName,
          gd.preferredName,
          gd.dateOfBirth,
          gd.address,
          gd.postCode,
          gd.phone,
          Some(gd.aLevel),
          Some(gd.stemLevel)
        ))
        Ok(views.html.application.generalDetails(form))

      }.recover {
        case e: PersonalDetailsNotFound =>
          Ok(views.html.application.generalDetails(formFromUser))
      }
  }

  def submitGeneralDetails = CSRSecureAppAction(PersonalDetailsRole) { implicit request =>
    implicit user =>
      implicit val now: LocalDate = LocalDate.now
      GeneralDetailsForm.form.bindFromRequest.fold(
        errorForm => {
          Future.successful(Ok(views.html.application.generalDetails(errorForm)))
        },
        generalDetails => {
          (for {
            _ <- updateGeneralDetails(user.application.applicationId, user.user.userID, generalDetails, user.user.email)
            _ <- updateDetails(user.user.userID, generalDetails.firstName, generalDetails.lastName, Some(generalDetails.preferredName))
            redirect <- updateProgress(data =>
              data.copy(
                user = user.user.copy(
                  firstName = generalDetails.firstName,
                  lastName = generalDetails.lastName, preferredName = Some(generalDetails.preferredName)
                ),
                application = data.application.map(_.copy(applicationStatus = IN_PROGRESS))
              ))(_ =>
              Redirect(routes.SchemeController.entryPoint()))
          } yield {
            redirect
          }) recover {
            case e: PersonalDetailsNotFound => Redirect(routes.HomeController.present()).flashing(danger("account.error"))
          }
        }
      )
  }
}
