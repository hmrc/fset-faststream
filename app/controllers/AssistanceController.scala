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

import _root_.forms.AssistanceForm
import config.CSRHttp
import connectors.ApplicationClient
import connectors.ApplicationClient.AssistanceDetailsNotFound
import helpers.NotificationType._
import security.Roles.AssistanceRole

import scala.concurrent.Future

object AssistanceController extends AssistanceController {
  val http = CSRHttp
}

trait AssistanceController extends BaseController with ApplicationClient {

  def present = CSRSecureAppAction(AssistanceRole) { implicit request =>
    implicit user =>

      findAssistanceDetails(user.user.userID, user.application.applicationId).map { ad =>
        val form = AssistanceForm.form.fill(AssistanceForm.Data(
          ad.needsAssistance,
          ad.typeOfdisability,
          ad.detailsOfdisability,
          ad.guaranteedInterview,
          ad.needsAdjustment.getOrElse(""),
          ad.typeOfAdjustments,
          ad.otherAdjustments,
          ad.campaignReferrer,
          ad.campaignOther
        ))
        Ok(views.html.application.assistance(form))
      }.recover {
        case e: AssistanceDetailsNotFound => Ok(views.html.application.assistance(AssistanceForm.form))
      }
  }

  def submit = CSRSecureAppAction(AssistanceRole) { implicit request =>
    implicit user =>

      AssistanceForm.form.bindFromRequest.fold(
        invalidForm =>
          Future.successful(Ok(views.html.application.assistance(invalidForm))),
        data => {
          addMedia(user.user.userID, extractMediaReferrer(data)).flatMap { _ =>
            updateAssistanceDetails(user.application.applicationId, user.user.userID, data).flatMap { _ =>
              updateProgress()(_ => Redirect(routes.ReviewApplicationController.present()))
            }.recover {
              case e: AssistanceDetailsNotFound =>
                Redirect(routes.HomeController.present()).flashing(danger("account.error"))
            }
          }
        }
      )
  }

  private def extractMediaReferrer(data: AssistanceForm.Data): String = {
    if (data.campaignReferrer.contains("Other") || data.campaignReferrer.contains("Careers fair")) {
      data.campaignOther.getOrElse("")
    } else {
      data.campaignReferrer.getOrElse("")
    }
  }

}
