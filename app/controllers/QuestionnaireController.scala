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

import _root_.forms.{ QuestionnaireDiversityInfoForm, QuestionnaireEducationInfoForm, QuestionnaireOccupationInfoForm }
import config.CSRHttp
import connectors.ApplicationClient
import connectors.ExchangeObjects.Questionnaire
import models.CachedDataWithApp
import play.api.mvc.{ Request, Result }
import security.Roles.{ DiversityQuestionnaireRole, EducationQuestionnaireRole, OccupationQuestionnaireRole, StartQuestionnaireRole }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.language.reflectiveCalls

object QuestionnaireController extends QuestionnaireController {
  val http = CSRHttp
}

trait QuestionnaireController extends BaseController with ApplicationClient {

  def start = CSRSecureAppAction(StartQuestionnaireRole) { implicit request =>
    implicit user =>
      val p = user.application.progress
      Future.successful {
        if (!p.diversityQuestionnaire && !p.educationQuestionnaire && !p.occupationQuestionnaire) {
          Ok(views.html.questionnaire.index())
        } else {
          Ok(views.html.questionnaire.continue())
        }
      }
  }

  def firstPageView = CSRSecureAppAction(DiversityQuestionnaireRole) { implicit request =>
    implicit user =>
      Future.successful(Ok(views.html.questionnaire.firstpage(QuestionnaireDiversityInfoForm.form)))
  }

  def secondPageView = CSRSecureAppAction(EducationQuestionnaireRole) { implicit request =>
    implicit user =>
      Future.successful(Ok(views.html.questionnaire.secondpage(QuestionnaireEducationInfoForm.form)))
  }

  def thirdPageView = CSRSecureAppAction(OccupationQuestionnaireRole) { implicit request =>
    implicit user =>
      Future.successful(Ok(views.html.questionnaire.thirdpage(QuestionnaireOccupationInfoForm.form)))
  }

  def submitStart = CSRSecureAppAction(StartQuestionnaireRole) { implicit request =>
    implicit user =>
      val empty = Questionnaire(List())
      submitQuestionnaire(empty, "start_questionnaire")(Redirect(routes.QuestionnaireController.firstPageView()))
  }

  def submitContinue = CSRSecureAppAction(StartQuestionnaireRole) { implicit request =>
    implicit user =>
      val p = user.application.progress
      Future.successful((p.diversityQuestionnaire, p.educationQuestionnaire, p.occupationQuestionnaire) match {
        case (_, _, true) => Redirect(routes.SubmitApplicationController.present())
        case (_, true, _) => Redirect(routes.QuestionnaireController.thirdPageView())
        case (true, _, _) => Redirect(routes.QuestionnaireController.secondPageView())
        case (_, _, _) => Redirect(routes.QuestionnaireController.firstPageView())
      })
  }

  def firstPageSubmit = CSRSecureAppAction(DiversityQuestionnaireRole) { implicit request =>
    implicit user =>
      QuestionnaireDiversityInfoForm.form.bindFromRequest.fold(
        errorForm => {
          Future.successful(Ok(views.html.questionnaire.firstpage(errorForm)))
        },
        data => {
          submitQuestionnaire(data.toQuestionnaire, "diversity_questionnaire")(Redirect(routes.QuestionnaireController.secondPageView()))
        }
      )
  }

  def secondPageSubmit = CSRSecureAppAction(EducationQuestionnaireRole) { implicit request =>
    implicit user =>
      QuestionnaireEducationInfoForm.form.bindFromRequest.fold(
        errorForm => {
          Future.successful(Ok(views.html.questionnaire.secondpage(errorForm)))
        },
        data => {
          submitQuestionnaire(data.toQuestionnaire, "education_questionnaire")(Redirect(routes.QuestionnaireController.thirdPageView()))
        }
      )
  }

  def thirdPageSubmit = CSRSecureAppAction(OccupationQuestionnaireRole) { implicit request =>
    implicit user =>
      QuestionnaireOccupationInfoForm.form.bindFromRequest.fold(
        errorForm => {
          Future.successful(Ok(views.html.questionnaire.thirdpage(errorForm)))
        },
        data => {
          submitQuestionnaire(data.toQuestionnaire, "occupation_questionnaire")(Redirect(routes.SubmitApplicationController.present()))
        }
      )
  }

  def submitQuestionnaire(data: Questionnaire, sectionId: String)(onSuccess: Result)(
    implicit
    user: CachedDataWithApp, hc: HeaderCarrier, request: Request[_]
  ) = {
    updateQuestionnaire(user.application.applicationId, sectionId, data).flatMap { _ =>
      updateProgress()(_ => onSuccess)
    }
  }

}
