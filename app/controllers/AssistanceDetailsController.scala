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

import _root_.forms.AssistanceDetailsForm
import connectors.ApplicationClient
import connectors.ApplicationClient.AssistanceDetailsNotFound
import connectors.exchange.AssistanceDetails
import models.CachedData
import security.RoleUtils
import security.Roles.AssistanceDetailsRole

import scala.concurrent.Future

object AssistanceDetailsController extends AssistanceDetailsController(ApplicationClient)

class AssistanceDetailsController(applicationClient: ApplicationClient) extends BaseController(applicationClient) {

  def present = CSRSecureAppAction(AssistanceDetailsRole) { implicit request =>
    implicit user =>
      applicationClient.getAssistanceDetails(user.user.userID, user.application.applicationId).map { ad =>
        val form = AssistanceDetailsForm.form.fill(assistanceDetailsExchange2Data(ad))
        Ok(views.html.application.assistanceDetails(form))
      }.recover {
        case e: AssistanceDetailsNotFound => Ok(views.html.application.assistanceDetails(AssistanceDetailsForm.form))
      }
  }

  def submit = CSRSecureAppAction(AssistanceDetailsRole) { implicit request =>
    implicit user =>
      AssistanceDetailsForm.form.bindFromRequest.fold(
        invalidForm =>
          Future.successful(Ok(views.html.application.assistanceDetails(invalidForm))),
        data => {
          applicationClient.updateAssistanceDetails(user.application.applicationId, user.user.userID, sanitizeData(data)).flatMap { _ =>
            updateProgress()(_ => {
              if (RoleUtils.hasOccupation(CachedData(user.user, Some(user.application)))) {
                Redirect(routes.PreviewApplicationController.present())
              } else {
                Redirect(routes.QuestionnaireController.startOrContinue())
              }
            })
          }
        }
      )
  }

  private def assistanceDetailsExchange2Data(ad: AssistanceDetails) = {
    AssistanceDetailsForm.Data(
      ad.hasDisability,
      ad.hasDisabilityDescription,
      ad.guaranteedInterview.map {
        case true => "Yes"
        case false => "No"
      },
      ad.needsSupportForOnlineAssessment match {
        case true => "Yes"
        case false => "No"
      },
      ad.needsSupportForOnlineAssessmentDescription,
      ad.needsSupportAtVenue match {
        case true => "Yes"
        case false => "No"
      },
      ad.needsSupportAtVenueDescription
    )
  }

  private def sanitizeData(data: AssistanceDetailsForm.Data) = {
    AssistanceDetailsForm.Data(
      data.hasDisability,
      if (data.hasDisability == "Yes") data.hasDisabilityDescription else None,
      if (data.hasDisability == "Yes") data.guaranteedInterview else None,
      data.needsSupportForOnlineAssessment,
      if (data.needsSupportForOnlineAssessment=="Yes") data.needsSupportForOnlineAssessmentDescription else None,
      data.needsSupportAtVenue,
      if (data.needsSupportAtVenue=="Yes") data.needsSupportAtVenueDescription else None)
  }
}
