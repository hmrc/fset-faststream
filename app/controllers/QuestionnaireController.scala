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

import _root_.forms.{ DiversityQuestionnaireForm, EducationQuestionnaireForm, ParentalOccupationQuestionnaireForm }
import connectors.ApplicationClient
import connectors.exchange.Questionnaire
import helpers.NotificationType._
import models.CachedDataWithApp
import play.api.mvc.{ Request, RequestHeader, Result }
import security.QuestionnaireRoles._
import security.Roles.{ CsrAuthorization, PreviewApplicationRole, SubmitApplicationRole }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.language.reflectiveCalls

object QuestionnaireController extends QuestionnaireController(ApplicationClient)

class QuestionnaireController(applicationClient: ApplicationClient) extends BaseController(applicationClient) {

  val QuestionnaireCompletedBanner = danger("questionnaire.completed")

  def presentStartOrContinue = CSRSecureAppAction(StartOrContinueQuestionnaireRole) { implicit request =>
    implicit user =>
      Future.successful {
        (PreviewApplicationRole.isAuthorized(user), QuestionnaireNotStartedRole.isAuthorized(user)) match {
          case (true, _) => Redirect(routes.HomeController.present()).flashing(QuestionnaireCompletedBanner)
          case (_, true) => Ok(views.html.questionnaire.intro(DiversityQuestionnaireForm.acceptanceForm))
          case _ => Ok(views.html.questionnaire.continue())
        }
      }
  }

  def presentFirstPage = CSRSecureAppAction(DiversityQuestionnaireRole) { implicit request =>
    implicit user =>
      presentPageIfNotFilledInPreviously(DiversityQuestionnaireCompletedRole,
        Ok(views.html.questionnaire.firstpage(DiversityQuestionnaireForm.form)))
  }

  def presentSecondPage = CSRSecureAppAction(EducationQuestionnaireRole) { implicit request =>
    implicit user =>
      presentPageIfNotFilledInPreviously(EducationQuestionnaireCompletedRole,
        Ok(views.html.questionnaire.secondpage(EducationQuestionnaireForm.form,
          if (user.application.fastPassDetails.exists(_.isCivilServant())) "Yes" else "No")))
  }

  def presentThirdPage = CSRSecureAppAction(ParentalOccupationQuestionnaireRole) { implicit request =>
    implicit user =>
      presentPageIfNotFilledInPreviously(ParentalOccupationQuestionnaireCompletedRole,
        Ok(views.html.questionnaire.thirdpage(ParentalOccupationQuestionnaireForm.form)))
  }

  def submitStart = CSRSecureAppAction(StartOrContinueQuestionnaireRole) { implicit request =>
    implicit user =>
      SubmitApplicationRole.isAuthorized(user) match {
        case true => Future.successful(Redirect(routes.HomeController.present()).flashing(QuestionnaireCompletedBanner))
        case false => DiversityQuestionnaireForm.acceptanceForm.bindFromRequest.fold(
          errorForm => {
            Future.successful(Ok(views.html.questionnaire.intro(errorForm)))
          },
          data => {
            submitQuestionnaire(data.toQuestionnaire, "start_questionnaire")(Redirect(routes.QuestionnaireController.presentFirstPage()))
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
              ParentalOccupationQuestionnaireCompletedRole.isAuthorized(user)) match {
              case (_, _, true) => Redirect(routes.SubmitApplicationController.present())
              case (_, true, _) => Redirect(routes.QuestionnaireController.presentThirdPage())
              case (true, _, _) => Redirect(routes.QuestionnaireController.presentSecondPage())
              case (_, _, _) => Redirect(routes.QuestionnaireController.presentFirstPage())
            }
          }
      }
  }

  def submitFirstPage = CSRSecureAppAction(DiversityQuestionnaireRole) { implicit request =>
    implicit user =>
      DiversityQuestionnaireCompletedRole.isAuthorized(user) match {
        case true => Future.successful(Redirect(routes.QuestionnaireController.presentStartOrContinue()).flashing(QuestionnaireCompletedBanner))
        case false => DiversityQuestionnaireForm.form.bindFromRequest.fold(
          errorForm => {
            Future.successful(Ok(views.html.questionnaire.firstpage(errorForm)))
          },
          data => {
            submitQuestionnaire(data.exchange, "diversity_questionnaire")(Redirect(routes.QuestionnaireController.presentSecondPage()))
          }
        )
      }
  }

  def submitSecondPage = CSRSecureAppAction(EducationQuestionnaireRole) { implicit request =>
    implicit user =>
      val isCivilServantString = if (user.application.fastPassDetails.exists(_.isCivilServant())) "Yes" else "No"
      EducationQuestionnaireCompletedRole.isAuthorized(user) match {
        case true => Future.successful(Redirect(routes.QuestionnaireController.presentStartOrContinue()).flashing(QuestionnaireCompletedBanner))
        case false => EducationQuestionnaireForm.form.bindFromRequest.fold(
          errorForm => {
            Future.successful(Ok(views.html.questionnaire.secondpage(errorForm, isCivilServantString)))

          },
          data => {
            submitQuestionnaire(data.sanitizeData.exchange(), "education_questionnaire")(
              Redirect(routes.QuestionnaireController.presentThirdPage()))
          }
        )
      }
  }

  def submitThirdPage = CSRSecureAppAction(ParentalOccupationQuestionnaireRole) { implicit request =>
    implicit user =>
      ParentalOccupationQuestionnaireCompletedRole.isAuthorized(user) match {
        case true => Future.successful(Redirect(routes.QuestionnaireController.presentStartOrContinue()).flashing(QuestionnaireCompletedBanner))
        case false => ParentalOccupationQuestionnaireForm.form.bindFromRequest.fold(
          errorForm => {
            Future.successful(Ok(views.html.questionnaire.thirdpage(errorForm)))
          },
          data => {
            submitQuestionnaire(data.exchange, "occupation_questionnaire")(Redirect(routes.PreviewApplicationController.present()))
          }
        )
      }
  }

  private def presentPageIfNotFilledInPreviously(pageFilledPreviously: CsrAuthorization, presentPage: => Result)
                                                (implicit user: CachedDataWithApp, requestHeader: RequestHeader) = {
    Future.successful {
      (pageFilledPreviously.isAuthorized(user), PreviewApplicationRole.isAuthorized(user)) match {
        case (_, true) => Redirect(routes.HomeController.present()).flashing(QuestionnaireCompletedBanner)
        case (true, _) => Redirect(routes.QuestionnaireController.presentStartOrContinue()).flashing(QuestionnaireCompletedBanner)
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
