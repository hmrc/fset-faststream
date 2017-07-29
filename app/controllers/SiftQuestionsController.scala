/*
 * Copyright 2017 HM Revenue & Customs
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

import com.mohiva.play.silhouette.api.Silhouette
import config.CSRCache
import connectors.{ ApplicationClient, ReferenceDataClient, SiftClient }
import connectors.exchange.referencedata.{ Scheme, SchemeId }
import connectors.exchange.sift.{ GeneralQuestionsAnswers, SchemeSpecificAnswer }
import forms.SchemeSpecificQuestionsForm
import forms.sift.GeneralQuestionsForm
import models.page.GeneralQuestionsPage
import security.Roles.SchemeSpecificQuestionsRole

import scala.concurrent.Future
import play.api.i18n.Messages.Implicits._
import play.api.Play.current
import play.api.mvc.{ Action, AnyContent }
import security.{ SecurityEnvironment, SilhouetteComponent }
import views.html.helper.form
import helpers.NotificationType._
import uk.gov.hmrc.play.http.HeaderCarrier

object SiftQuestionsController extends SiftQuestionsController(ApplicationClient, SiftClient, ReferenceDataClient, CSRCache) {
  val appRouteConfigMap: Map[models.ApplicationRoute.Value, ApplicationRouteStateImpl] = config.FrontendAppConfig.applicationRoutesFrontend
  lazy val silhouette: Silhouette[SecurityEnvironment] = SilhouetteComponent.silhouette
}

abstract class SiftQuestionsController(
  applicationClient: ApplicationClient, siftClient: SiftClient, referenceDataClient: ReferenceDataClient, cacheClient: CSRCache)
  extends BaseController(applicationClient, cacheClient) with CampaignAwareController {

  def schemeMetadata(schemeId: SchemeId)(implicit hc: HeaderCarrier): Future[Scheme] = {
    referenceDataClient.allSchemes().map(schemes => schemes.find(_.id == schemeId).get)
  }

  def presentGeneralQuestions(): Action[AnyContent] = CSRSecureAppAction(SchemeSpecificQuestionsRole) { implicit request =>
    implicit user =>
      for {
        answers <- siftClient.getGeneralQuestionsAnswers(user.application.applicationId)
      } yield {
        val page = GeneralQuestionsPage.apply(answers)
        Ok(views.html.application.additionalquestions.generalQuestions(page))
      }
  }

  def saveGeneralQuestions(): Action[AnyContent] = CSRSecureAppAction(SchemeSpecificQuestionsRole) { implicit request =>
    implicit user =>
      GeneralQuestionsForm.form.bindFromRequest.fold(
        invalid => {
          Future(Ok(views.html.application.additionalquestions.generalQuestions(GeneralQuestionsPage(invalid))))
        },
        form => {
          val dataToSave = GeneralQuestionsAnswers.apply(form)
          siftClient.updateGeneralAnswers(user.application.applicationId, dataToSave).map { _ =>
            Redirect(routes.HomeController.present()).flashing(success("additionalquestions.section.saved"))
          }
        }
      )
  }

  def presentSchemeForm(schemeId: SchemeId): Action[AnyContent] = CSRSecureAppAction(SchemeSpecificQuestionsRole) { implicit request =>
    implicit user =>
      schemeMetadata(schemeId).map { scheme =>
        if (canAnswersBeModified()) {
          Ok(views.html.application.additionalquestions.schemespecific(SchemeSpecificQuestionsForm.form, scheme))
        } else {
          Redirect(routes.HomeController.present())
        }
      }
  }

  def saveSchemeForm(schemeId: SchemeId): Action[AnyContent] = CSRSecureAppAction(SchemeSpecificQuestionsRole) { implicit request =>
    implicit user =>
      SchemeSpecificQuestionsForm.form.bindFromRequest.fold(
        invalid => {
          schemeMetadata(schemeId).map { scheme =>
            Ok(views.html.application.additionalquestions.schemespecific(SchemeSpecificQuestionsForm.form, scheme))
          }
        },
        form => {
          val dataToSave = SchemeSpecificAnswer.apply(form.rawText)
          siftClient.updateSchemeSpecificAnswer(user.application.applicationId, schemeId, dataToSave).map { _ =>
            Redirect(routes.HomeController.present())
          }
        }
      )
  }

  def presentSummary: Action[AnyContent] = CSRSecureAppAction(SchemeSpecificQuestionsRole) { implicit request =>
    implicit user =>
      Future.successful(Ok(views.html.application.success()))
  }

  def submitAdditionalQuestions: Action[AnyContent] = CSRSecureAppAction(SchemeSpecificQuestionsRole) { implicit request =>
    implicit user =>
      siftClient.submitSiftAnswers(user.application.applicationId).map { _ =>
        //Ok(views.html.application.success())
        Redirect(routes.HomeController.present()).flashing(success("additionalquestions.submitted"))
      }
  }


  private def canAnswersBeModified(): Boolean = {
    true
  }
}
