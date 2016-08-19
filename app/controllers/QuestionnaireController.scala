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
import connectors.ApplicationClient
import connectors.ExchangeObjects.Questionnaire
import models.CachedDataWithApp
import play.api.mvc.{ Request, RequestHeader, Result }
import security.QuestionnaireRoles._
import uk.gov.hmrc.play.http.HeaderCarrier
import helpers.NotificationType._
import security.Roles.{ CsrAuthorization, PreviewApplicationRole, SubmitApplicationRole }

import scala.concurrent.Future
import scala.language.reflectiveCalls

object QuestionnaireController extends QuestionnaireController(ApplicationClient)

class QuestionnaireController(applicationClient: ApplicationClient) extends BaseController(applicationClient) {

  val QuestionnaireCompletedBanner = danger("questionnaire.completed")

  def startOrContinue = CSRSecureAppAction(StartOrContinueQuestionnaireRole) { implicit request =>
    implicit user =>
      Future.successful {
        (PreviewApplicationRole.isAuthorized(user), QuestionnaireNotStartedRole.isAuthorized(user)) match {
          case (true, _) => Redirect(routes.HomeController.present()).flashing(QuestionnaireCompletedBanner)
          case (_, true) => Ok(views.html.questionnaire.intro(QuestionnaireDiversityInfoForm.acceptanceForm))
          case _ =>   Ok(views.html.questionnaire.continue())
        }
      }
  }

  def firstPageView = CSRSecureAppAction(DiversityQuestionnaireRole) { implicit request =>
    implicit user =>
      presentPageIfNotFilledInPreviously(DiversityQuestionnaireCompletedRole,
        Ok(views.html.questionnaire.firstpage(QuestionnaireDiversityInfoForm.form)))
  }

  def secondPageView = CSRSecureAppAction(EducationQuestionnaireRole) { implicit request =>
    implicit user =>
      presentPageIfNotFilledInPreviously(EducationQuestionnaireCompletedRole,
        Ok(views.html.questionnaire.secondpage(QuestionnaireEducationInfoForm.form)))
  }

  def thirdPageView = CSRSecureAppAction(OccupationQuestionnaireRole) { implicit request =>
    implicit user =>
      presentPageIfNotFilledInPreviously(OccupationQuestionnaireCompletedRole,
        Ok(views.html.questionnaire.thirdpage(QuestionnaireOccupationInfoForm.form)))
  }

  def submitStart = CSRSecureAppAction(StartOrContinueQuestionnaireRole) { implicit request =>
    implicit user =>
      SubmitApplicationRole.isAuthorized(user) match {
        case true => Future.successful(Redirect(routes.HomeController.present()).flashing(QuestionnaireCompletedBanner))
        case false => QuestionnaireDiversityInfoForm.acceptanceForm.bindFromRequest.fold(
          errorForm => {
            Future.successful(Ok(views.html.questionnaire.intro(errorForm)))
          },
          data => {
            submitQuestionnaire(data.toQuestionnaire, "start_questionnaire")(Redirect(routes.QuestionnaireController.firstPageView()))
          }
        )
      }
  }

  def submitContinue = CSRSecureAppAction(StartOrContinueQuestionnaireRole) { implicit request =>
    implicit user =>

      SubmitApplicationRole.isAuthorized(user) match {
        case true => Future.successful(Redirect(routes.HomeController.present()).flashing(QuestionnaireCompletedBanner))
        case false =>
          Future.successful {
            (DiversityQuestionnaireCompletedRole.isAuthorized(user), EducationQuestionnaireCompletedRole.isAuthorized(user),
              OccupationQuestionnaireCompletedRole.isAuthorized(user)) match {
              case (_, _, true) => Redirect(routes.SubmitApplicationController.present())
              case (_, true, _) => Redirect(routes.QuestionnaireController.thirdPageView())
              case (true, _, _) => Redirect(routes.QuestionnaireController.secondPageView())
              case (_, _, _) => Redirect(routes.QuestionnaireController.firstPageView())
            }
          }
      }
  }

  def firstPageSubmit = CSRSecureAppAction(DiversityQuestionnaireRole) { implicit request =>
    implicit user =>
      DiversityQuestionnaireCompletedRole.isAuthorized(user) match {
        case true => Future.successful(Redirect(routes.QuestionnaireController.startOrContinue()).flashing(QuestionnaireCompletedBanner))
        case false => QuestionnaireDiversityInfoForm.form.bindFromRequest.fold(
          errorForm => {
            Future.successful(Ok(views.html.questionnaire.firstpage(errorForm)))
          },
          data => {
            submitQuestionnaire(data.toQuestionnaire, "diversity_questionnaire")(Redirect(routes.QuestionnaireController.secondPageView()))
          }
        )
      }
  }

  def secondPageSubmit = CSRSecureAppAction(EducationQuestionnaireRole) { implicit request =>
    implicit user =>
      EducationQuestionnaireCompletedRole.isAuthorized(user) match {
        case true => Future.successful(Redirect(routes.QuestionnaireController.startOrContinue()).flashing(QuestionnaireCompletedBanner))
        case false => QuestionnaireEducationInfoForm.form.bindFromRequest.fold(
          errorForm => {
            Future.successful(Ok(views.html.questionnaire.secondpage(errorForm)))
          },
          data => {
            submitQuestionnaire(data.toQuestionnaire, "education_questionnaire")(Redirect(routes.QuestionnaireController.thirdPageView()))
          }
        )
      }
  }

  def thirdPageSubmit = CSRSecureAppAction(OccupationQuestionnaireRole) { implicit request =>
    implicit user =>
      OccupationQuestionnaireCompletedRole.isAuthorized(user) match {
        case true => Future.successful(Redirect(routes.QuestionnaireController.startOrContinue()).flashing(QuestionnaireCompletedBanner))
        case false => QuestionnaireOccupationInfoForm.form.bindFromRequest.fold(
          errorForm => {
            Future.successful(Ok(views.html.questionnaire.thirdpage(errorForm)))
          },
          data => {
            submitQuestionnaire(data.toQuestionnaire, "occupation_questionnaire")(Redirect(routes.PreviewApplicationController.present()))
          }
        )
      }
  }

  private def presentPageIfNotFilledInPreviously(pageFilledPreviously:CsrAuthorization, presentPage: => Result)
                                                (implicit user: CachedDataWithApp, requestHeader: RequestHeader) = {
    Future.successful {
      (pageFilledPreviously.isAuthorized(user), PreviewApplicationRole.isAuthorized(user)) match {
        case (_, true) => Redirect(routes.HomeController.present()).flashing(QuestionnaireCompletedBanner)
        case (true, _) => Redirect(routes.QuestionnaireController.startOrContinue()).flashing(QuestionnaireCompletedBanner)
        case _ => presentPage
      }
    }
  }

  private def submitQuestionnaire(data: Questionnaire, sectionId: String)(onSuccess: Result)(
    implicit
    user: CachedDataWithApp, hc: HeaderCarrier, request: Request[_]
  ) = {
    applicationClient.updateQuestionnaire(user.application.applicationId, sectionId, data).flatMap { _ =>
      updateProgress()(_ => onSuccess)
    }
  }

}
