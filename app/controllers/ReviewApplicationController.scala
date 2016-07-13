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

import config.CSRHttp
import connectors.ApplicationClient.{ AssistanceDetailsNotFound, PersonalDetailsNotFound }
import connectors.SchemeClient.CannotFindSelection
import connectors.{ ApplicationClient, SchemeClient }
import helpers.NotificationType._
import security.Roles.{ QuestionnaireInProgressRole, ReviewRole, StartQuestionnaireRole }

object ReviewApplicationController extends ReviewApplicationController {
  val http = CSRHttp
}

trait ReviewApplicationController extends BaseController with ApplicationClient with SchemeClient {

  def present = CSRSecureAppAction(ReviewRole) { implicit request =>
    implicit user =>
      val personalDetailsFut = findPersonalDetails(user.user.userID, user.application.applicationId)
      val assistanceDetailsFut = findAssistanceDetails(user.user.userID, user.application.applicationId)
      val frameworkLocationFut = getSelection(user.application.applicationId)

      (for {
        gd <- personalDetailsFut
        ad <- assistanceDetailsFut
        fl <- frameworkLocationFut
      } yield {
        Ok(views.html.application.review(gd, ad, fl, user.application))
      }).recover {
        case e @ (_: PersonalDetailsNotFound | _: AssistanceDetailsNotFound | _: CannotFindSelection) =>
          Redirect(routes.HomeController.present()).flashing(warning("info.cannot.review.yet"))
      }
  }

  def submit = CSRSecureAppAction(ReviewRole) { implicit request =>
    implicit user =>
      updateReview(user.application.applicationId).flatMap { _ =>
        updateProgress() { u =>
          if (StartQuestionnaireRole.isAuthorized(u) || QuestionnaireInProgressRole.isAuthorized(u)) {
            Redirect(routes.QuestionnaireController.start())
          } else {
            Redirect(routes.SubmitApplicationController.present())
          }
        }
      }

  }

}
