/*
 * Copyright 2019 HM Revenue & Customs
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
import com.mohiva.play.silhouette.api.Silhouette
import connectors.ApplicationClient
import connectors.exchange.Questionnaire
import helpers.NotificationType
import helpers.NotificationType._
import models.{ ApplicationRoute, CachedDataWithApp }
import play.api.mvc._
import security.QuestionnaireRoles._
import security.Roles.{ CsrAuthorization, PreviewApplicationRole, SubmitApplicationRole }
import security.{ SecurityEnvironment, SilhouetteComponent }

import scala.concurrent.Future
import scala.language.reflectiveCalls
import play.api.i18n.Messages.Implicits._
import play.api.Play.current
import uk.gov.hmrc.http.HeaderCarrier

object QuestionnaireController extends QuestionnaireController(ApplicationClient) {
  lazy val silhouette: Silhouette[SecurityEnvironment] = SilhouetteComponent.silhouette
}

abstract class QuestionnaireController(applicationClient: ApplicationClient)
  extends BaseController {

  val QuestionnaireCompletedBanner: (NotificationType, String) = danger("questionnaire.completed")

  def presentStartOrContinue: Action[AnyContent] = CSRSecureAppAction(StartOrContinueQuestionnaireRole) { implicit request =>
    implicit user =>
      Future.successful {
        (PreviewApplicationRole.isAuthorized(user), QuestionnaireNotStartedRole.isAuthorized(user)) match {
          case (true, _) => Redirect(routes.HomeController.present()).flashing(QuestionnaireCompletedBanner)
          case (_, true) => Ok(views.html.questionnaire.intro(DiversityQuestionnaireForm.acceptanceForm))
          case _ => Ok(views.html.questionnaire.continue())
        }
      }
  }

  def presentFirstPage: Action[AnyContent] = CSRSecureAppAction(DiversityQuestionnaireRole) { implicit request =>
    implicit user =>
      presentPageIfNotFilledInPreviously(DiversityQuestionnaireCompletedRole,
        Ok(views.html.questionnaire.firstpage(DiversityQuestionnaireForm.form)))
  }

  def presentSecondPage: Action[AnyContent] = CSRSecureAppAction(EducationQuestionnaireRole) { implicit request =>
    implicit user =>
      presentPageIfNotFilledInPreviously(EducationQuestionnaireCompletedRole,
        Ok(views.html.questionnaire.secondpage(EducationQuestionnaireForm.form(universityMessageKey),
          if (user.application.civilServiceExperienceDetails.exists(_.isCivilServant)) "Yes" else "No")))
  }

  def presentThirdPage: Action[AnyContent] = CSRSecureAppAction(ParentalOccupationQuestionnaireRole) { implicit request =>
    implicit user =>
      presentPageIfNotFilledInPreviously(ParentalOccupationQuestionnaireCompletedRole,
        Ok(views.html.questionnaire.thirdpage(ParentalOccupationQuestionnaireForm.form)))
  }

  def submitStart: Action[AnyContent] = CSRSecureAppAction(StartOrContinueQuestionnaireRole) { implicit request =>
    implicit user =>
      if (SubmitApplicationRole.isAuthorized(user)) {
        Future.successful(Redirect(routes.HomeController.present()).flashing(QuestionnaireCompletedBanner))
      } else {
        DiversityQuestionnaireForm.acceptanceForm.bindFromRequest.fold(
          errorForm => {
            Future.successful(Ok(views.html.questionnaire.intro(errorForm)))
          },
          data => {
            submitQuestionnaire(data.toQuestionnaire, "start_questionnaire")(Redirect(routes.QuestionnaireController.presentFirstPage()))
          }
        )
      }
  }

  def submitContinue: Action[AnyContent] = CSRSecureAppAction(StartOrContinueQuestionnaireRole) { implicit request =>
    implicit user =>
      if (SubmitApplicationRole.isAuthorized(user)) {
        Future.successful(Redirect(routes.HomeController.present()).flashing(QuestionnaireCompletedBanner))
      } else {
        Future.successful {
          (DiversityQuestionnaireCompletedRole.isAuthorized(user), EducationQuestionnaireCompletedRole.isAuthorized(user),
            ParentalOccupationQuestionnaireCompletedRole.isAuthorized(user)) match {
            case (_, _, true) => Redirect(routes.SubmitApplicationController.presentSubmit())
            case (_, true, _) => Redirect(routes.QuestionnaireController.presentThirdPage())
            case (true, _, _) => Redirect(routes.QuestionnaireController.presentSecondPage())
            case (_, _, _) => Redirect(routes.QuestionnaireController.presentFirstPage())
          }
        }
      }
  }

  def submitFirstPage: Action[AnyContent] = CSRSecureAppAction(DiversityQuestionnaireRole) { implicit request =>
    implicit user =>
      if (DiversityQuestionnaireCompletedRole.isAuthorized(user)) {
        Future.successful(Redirect(routes.QuestionnaireController.presentStartOrContinue()).flashing(QuestionnaireCompletedBanner))
      } else {
        DiversityQuestionnaireForm.form.bindFromRequest.fold(
          errorForm => {
            Future.successful(Ok(views.html.questionnaire.firstpage(errorForm)))
          },
          data => {
            submitQuestionnaire(data.exchange, "diversity_questionnaire")(Redirect(routes.QuestionnaireController.presentSecondPage()))
          }
        )
      }
  }

  def submitSecondPage: Action[AnyContent] = CSRSecureAppAction(EducationQuestionnaireRole) { implicit request =>
    implicit user =>
      val isCivilServantString = if (user.application.civilServiceExperienceDetails.exists(_.isCivilServant)) "Yes" else "No"
      if (EducationQuestionnaireCompletedRole.isAuthorized(user)) {
        Future.successful(Redirect(routes.QuestionnaireController.presentStartOrContinue()).flashing(QuestionnaireCompletedBanner))
      } else {
        EducationQuestionnaireForm.form(universityMessageKey).bindFromRequest.fold(
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

  def submitThirdPage: Action[AnyContent] = CSRSecureAppAction(ParentalOccupationQuestionnaireRole) { implicit request =>
    implicit user =>
      if (ParentalOccupationQuestionnaireCompletedRole.isAuthorized(user)) {
        Future.successful(Redirect(routes.QuestionnaireController.presentStartOrContinue()).flashing(QuestionnaireCompletedBanner))
      } else {
        ParentalOccupationQuestionnaireForm.form.bindFromRequest.fold(
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
    applicationClient.updateQuestionnaire(user.application.applicationId, sectionId, data).map { _ => onSuccess }
  }

  private def universityMessageKey(implicit app: CachedDataWithApp) = app.application.applicationRoute match {
    case ApplicationRoute.Edip | ApplicationRoute.Sdip | ApplicationRoute.SdipFaststream => "currentUniversity"
    case ApplicationRoute.Faststream => "university"
  }
}
