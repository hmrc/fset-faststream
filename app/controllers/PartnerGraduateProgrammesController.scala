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

import _root_.forms.PartnerGraduateProgrammesForm
import connectors.ApplicationClient
import connectors.ApplicationClient.PartnerGraduateProgrammesNotFound
import connectors.exchange.PartnerGraduateProgrammes
import security.Roles.PartnerGraduateProgrammesRole

import scala.concurrent.Future

object PartnerGraduateProgrammesController extends PartnerGraduateProgrammesController(ApplicationClient)

class PartnerGraduateProgrammesController(applicationClient: ApplicationClient) extends BaseController(applicationClient) {

  def present = CSRSecureAppAction(PartnerGraduateProgrammesRole) { implicit request =>
    implicit user =>
      applicationClient.getPartnerGraduateProgrammes(user.application.applicationId).map { ad =>
        val form = PartnerGraduateProgrammesForm.form.fill(partnersGraduateProgrammesExchange2Data(ad))
        Ok(views.html.application.partnerGraduateProgrammes(form))
      }.recover {
        case e: PartnerGraduateProgrammesNotFound =>
          Ok(views.html.application.partnerGraduateProgrammes(PartnerGraduateProgrammesForm.form))
      }
  }

  def submit = CSRSecureAppAction(PartnerGraduateProgrammesRole) { implicit request =>
    implicit user =>
      PartnerGraduateProgrammesForm.form.bindFromRequest.fold(
        invalidForm =>
          Future.successful(Ok(views.html.application.partnerGraduateProgrammes(invalidForm))),
        data => {
          applicationClient.updatePartnerGraduateProgrammes(user.application.applicationId, sanitizeData(data)).flatMap { _ =>
            updateProgress()(_ => Redirect(routes.AssistanceDetailsController.present()))
          }
        }
      )
  }

  private def partnersGraduateProgrammesExchange2Data(pgp: PartnerGraduateProgrammes) =
    PartnerGraduateProgrammesForm.Data(if (pgp.interested) "Yes" else "No", pgp.partnerGraduateProgrammes)

  private def sanitizeData(data: PartnerGraduateProgrammesForm.Data) = {
    PartnerGraduateProgrammesForm.Data(
      data.interested,
      if (data.interested=="Yes") data.partnerGraduateProgrammes else None
    )
  }
}
