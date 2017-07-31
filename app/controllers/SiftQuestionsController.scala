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
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import config.CSRCache
import connectors.ApplicationClient.SiftAnswersIncomplete
import connectors.{ ApplicationClient, ReferenceDataClient, SiftClient }
import connectors.exchange.referencedata.{ Scheme, SchemeId, SiftRequirement }
import connectors.exchange.sift.{ GeneralQuestionsAnswers, SchemeSpecificAnswer, SiftAnswers }
import forms.SchemeSpecificQuestionsForm
import forms.sift.GeneralQuestionsForm
import models.page.{ GeneralQuestionsPage, SiftPreviewPage }
import security.Roles.SchemeSpecificQuestionsRole

import scala.concurrent.Future
import play.api.i18n.Messages.Implicits._
import play.api.Play.current
import play.api.mvc.{ Action, AnyContent, Call, Result }
import security.{ SecurityEnvironment, SilhouetteComponent }
import views.html.helper.form
import helpers.NotificationType._
import models.UniqueIdentifier
import uk.gov.hmrc.play.http.HeaderCarrier

object SiftQuestionsController extends SiftQuestionsController(ApplicationClient, SiftClient, ReferenceDataClient, CSRCache) {
  val appRouteConfigMap: Map[models.ApplicationRoute.Value, ApplicationRouteStateImpl] = config.FrontendAppConfig.applicationRoutesFrontend
  lazy val silhouette: Silhouette[SecurityEnvironment] = SilhouetteComponent.silhouette
}

abstract class SiftQuestionsController(
  applicationClient: ApplicationClient, siftClient: SiftClient, referenceDataClient: ReferenceDataClient, cacheClient: CSRCache)
  extends BaseController(applicationClient, cacheClient) with CampaignAwareController {

  val GeneralQuestions = "generalQuestions"

  def schemeMetadata(schemeId: SchemeId)(implicit hc: HeaderCarrier): Future[Scheme] = {
    referenceDataClient.allSchemes().map { _.find(_.id == schemeId).get }
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

  def saveGeneralQuestions(): Action[AnyContent] =
    CSRSecureAppAction(SchemeSpecificQuestionsRole) { implicit request =>
    implicit user =>
      GeneralQuestionsForm().form.bindFromRequest.fold(
        invalid => {
          Future(Ok(views.html.application.additionalquestions.generalQuestions(GeneralQuestionsPage(invalid))))
        },
        form => {
          for {
            schemes <- candidateCurrentSiftableSchemes(user.application.applicationId)
            _ <- siftClient.updateGeneralAnswers(user.application.applicationId, GeneralQuestionsAnswers.apply(form))
          } yield {
            continueOrReturn(
              getNextStep(schemes),
              Redirect(routes.HomeController.present())
            )
          }
        }
      )
  }

  def presentSchemeForm(schemeId: SchemeId): Action[AnyContent] =
    CSRSecureAppAction(SchemeSpecificQuestionsRole) { implicit request =>
    implicit user =>
      for {
        scheme <- schemeMetadata(schemeId)
        schemeAnswer <- siftClient.getSchemeSpecificAnswer(user.application.applicationId, schemeId)
      } yield {
        val form = schemeAnswer.map(SchemeSpecificQuestionsForm.form.fill).getOrElse(SchemeSpecificQuestionsForm.form)
        Ok(views.html.application.additionalquestions.schemespecific(form, scheme))
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
          for {
            schemes <- candidateCurrentSiftableSchemes(user.application.applicationId)
            _ <- siftClient.updateSchemeSpecificAnswer(user.application.applicationId,schemeId, SchemeSpecificAnswer.apply(form.rawText))
          } yield {
            continueOrReturn(
              getNextStep(schemeId, schemes),
              Redirect(routes.HomeController.present())
            )
          }
        }
      )
  }

  def presentPreview: Action[AnyContent] = CSRSecureAppAction(SchemeSpecificQuestionsRole) { implicit request =>
    implicit user =>

      def enrichSchemeAnswers(siftAnswers: SiftAnswers) = Future.traverse(siftAnswers.schemeAnswers) { case (schemeId, answer) =>
        schemeMetadata(SchemeId(schemeId)).map { scheme =>
          scheme -> answer
        }
      }.map(_.toMap)

      for {
        answers <- siftClient.getSiftAnswers(user.application.applicationId)
        enrichedAnswers <- enrichSchemeAnswers(answers)
      } yield {
         val page = SiftPreviewPage(
          answers.applicationId,
          answers.status,
          answers.generalAnswers,
           enrichedAnswers
        )
        Ok(views.html.application.additionalquestions.previewAdditionalAnswers(page))
      }
  }

  def submitAdditionalQuestions: Action[AnyContent] = CSRSecureAppAction(SchemeSpecificQuestionsRole) { implicit request =>
    implicit user =>
      siftClient.submitSiftAnswers(user.application.applicationId).map { _ =>
        Redirect(routes.HomeController.present()).flashing(success("additionalquestions.submitted"))
      } recover {
        case _: SiftAnswersIncomplete =>
          Redirect(routes.HomeController.present()).flashing(danger("additionalquestions.section.missing"))
      }
  }

  // TODO This is horrible
  private def candidateCurrentSiftableSchemes(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    applicationClient.getPhase3Results(applicationId).flatMap(_.map { s =>
      Future.traverse(s.collect {
        case scheme if scheme.result == "Green" => scheme.schemeId
      }) { schemeId =>
        schemeMetadata(schemeId)
      }.map(_.collect { case s if s.siftRequirement.contains(SiftRequirement.FORM) => s.id})
    }.getOrElse(Future(Nil)))
  }

  private def getFormAction(implicit request: SecuredRequest[_, _]) = {
    request.body.asInstanceOf[AnyContent].asFormUrlEncoded.getOrElse(Map.empty).get("action").flatMap(_.headOption)
        .getOrElse("saveAndReturn")
  }

  private def continueOrReturn(continue: Result, returnHome: Result)(implicit request: SecuredRequest[_, _]) = {
    getFormAction match {
      case "saveAndContinue" => continue
      case "saveAndReturn" => returnHome
      case _ => returnHome
    }
  }

  private def getNextStep(currentSchemePage: SchemeId, schemesForSift: Seq[SchemeId]) = {
    val destination = if (schemesForSift.last == currentSchemePage) {
      routes.SiftQuestionsController.presentPreview()
    } else {
      schemesForSift.lift(schemesForSift.indexOf(currentSchemePage) + 1).map { nextScheme =>
        routes.SiftQuestionsController.presentSchemeForm(nextScheme)
      }.getOrElse(routes.SiftQuestionsController.presentPreview())
    }

    Redirect(destination)
  }

  private def getNextStep(schemesForSift: Seq[SchemeId]) = {
    Redirect(schemesForSift.headOption.map { scheme =>
      routes.SiftQuestionsController.presentSchemeForm(scheme)
    }.getOrElse(routes.HomeController.present()))
  }
}
