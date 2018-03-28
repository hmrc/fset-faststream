/*
 * Copyright 2018 HM Revenue & Customs
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
import connectors.ApplicationClient.{ SiftAnswersIncomplete, SiftAnswersNotFound, SiftExpired }
import connectors.{ ApplicationClient, ReferenceDataClient, SchemeClient, SiftClient }
import connectors.exchange.referencedata.{ Scheme, SchemeId, SiftRequirement }
import connectors.exchange.sift.{ GeneralQuestionsAnswers, SchemeSpecificAnswer, SiftAnswers, SiftAnswersStatus }
import forms.SchemeSpecificQuestionsForm
import forms.sift.GeneralQuestionsForm
import helpers.CachedUserWithSchemeData
import models.page.{ GeneralQuestionsPage, SiftPreviewPage }
import security.Roles.{ PreviewSchemeSpecificQuestionsRole, SchemeSpecificQuestionsRole }

import scala.concurrent.Future
import play.api.i18n.Messages.Implicits._
import play.api.Play.current
import play.api.mvc.{ Action, AnyContent, Result }
import security.{ SecurityEnvironment, SilhouetteComponent }
import helpers.NotificationType._
import models.{ SchemeStatus, UniqueIdentifier }
import uk.gov.hmrc.http.HeaderCarrier

object SiftQuestionsController extends SiftQuestionsController(ApplicationClient, SiftClient, ReferenceDataClient, SchemeClient) {
  val appRouteConfigMap: Map[models.ApplicationRoute.Value, ApplicationRouteStateImpl] = config.FrontendAppConfig.applicationRoutesFrontend
  lazy val silhouette: Silhouette[SecurityEnvironment] = SilhouetteComponent.silhouette
}

abstract class SiftQuestionsController(
  applicationClient: ApplicationClient,
  siftClient: SiftClient,
  referenceDataClient: ReferenceDataClient,
  schemeClient: SchemeClient)
  extends BaseController with CampaignAwareController {

  val GeneralQuestions = "generalQuestions"
  val SaveAndReturnAction = "saveAndReturn"
  val SaveAndContinueAction = "saveAndContinue"

  def schemeMetadata(schemeId: SchemeId)(implicit hc: HeaderCarrier): Future[Scheme] = {
    referenceDataClient.allSchemes().map { _.find(_.id == schemeId).get }
  }

  def presentGeneralQuestions(): Action[AnyContent] = CSRSecureAppAction(SchemeSpecificQuestionsRole) { implicit request =>
    implicit user =>
      for {
        answers <- siftClient.getGeneralQuestionsAnswers(user.application.applicationId)
      } yield {
        val page = GeneralQuestionsPage(answers)
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
            _ <- siftClient.updateGeneralAnswers(user.application.applicationId, GeneralQuestionsAnswers(form))
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
            Ok(views.html.application.additionalquestions.schemespecific(invalid, scheme))
          }
        },
        form => {
          for {
            schemes <- candidateCurrentSiftableSchemes(user.application.applicationId)
            _ <- siftClient.updateSchemeSpecificAnswer(user.application.applicationId, schemeId, SchemeSpecificAnswer.apply(form.rawText))
          } yield {
            continueOrReturn(
              getNextStep(schemeId, schemes),
              Redirect(routes.HomeController.present())
            )
          }
        }
      )
  }

  def presentPreview: Action[AnyContent] = CSRSecureAppAction(PreviewSchemeSpecificQuestionsRole) { implicit request =>
    implicit user =>

      def enrichSchemeAnswersAddingMissingSiftSchemes(siftAnswers: SiftAnswers, userMetadata: CachedUserWithSchemeData) = {
        val enrichedExisting = referenceDataClient.allSchemes map { allSchemes =>
          siftAnswers.schemeAnswers flatMap { case (schemeId, answer) =>
            allSchemes.collect { case s if s.id == SchemeId(schemeId) => s -> answer }
          }
        }

        enrichedExisting map { ee =>
          val toAdd = userMetadata.schemesForSiftForms.toSet diff ee.keySet
          ee ++ toAdd.map {scheme => scheme -> SchemeSpecificAnswer("")}
        }
      }

      def noSiftAnswersRecovery: PartialFunction[Throwable, Future[SiftAnswers]] = {
        case _: SiftAnswersNotFound =>
          for {
            schemeIds <- candidateCurrentSiftableSchemes(user.application.applicationId)
            sa = SiftAnswers(user.application.applicationId.toString, SiftAnswersStatus.DRAFT, None,
              schemeIds.map(s => s.value -> SchemeSpecificAnswer("")).toMap)
          } yield sa
      }

      def removeWithdrawnAnswers(answers: SiftAnswers, userMetadata: CachedUserWithSchemeData) = {
        val withdrawnSchemeIds = userMetadata.withdrawnSchemes.map(_.id)
        answers.copy(schemeAnswers = answers.schemeAnswers.filterKeys(schemeId => !withdrawnSchemeIds.contains(SchemeId(schemeId))))
      }

      for {
        allSchemes <- referenceDataClient.allSchemes()
        schemeStatus <- applicationClient.getCurrentSchemeStatus(user.application.applicationId)
        schemePreferences <- schemeClient.getSchemePreferences(user.application.applicationId)
        answers <- siftClient.getSiftAnswers(user.application.applicationId) recoverWith noSiftAnswersRecovery
        userMetadata = CachedUserWithSchemeData(user.user, user.application, schemePreferences, allSchemes, None, None, schemeStatus)
        filteredAnswers = removeWithdrawnAnswers(answers, userMetadata)
        enrichedAnswers <- enrichSchemeAnswersAddingMissingSiftSchemes(filteredAnswers, userMetadata)
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
        case _: SiftExpired =>
          Redirect(routes.HomeController.present()).flashing(danger("additionalquestions.sift.expired"))
      }
  }

  private def candidateCurrentSiftableSchemes(applicationId: UniqueIdentifier)(implicit hc: HeaderCarrier) = {
    applicationClient.getCurrentSchemeStatus(applicationId).flatMap { schemes =>
      val resultIsGreen: (String => Boolean) = (schemeRes: String) => schemeRes == SchemeStatus.Green.toString
      Future.traverse(schemes.collect {
        case scheme if resultIsGreen(scheme.result) => scheme.schemeId
      }) { schemeId =>
        schemeMetadata(schemeId)
      }.map(_.collect { case s if s.siftRequirement.contains(SiftRequirement.FORM) => s.id})
    }
  }

  private def getFormAction(implicit request: SecuredRequest[_, _]) = {
    request.body.asInstanceOf[AnyContent].asFormUrlEncoded.getOrElse(Map.empty).get("action").flatMap(_.headOption)
        .getOrElse(SaveAndReturnAction)
  }

  private def continueOrReturn(continue: Result, returnHome: Result)(implicit request: SecuredRequest[_, _]) = {
    getFormAction match {
      case SaveAndContinueAction => continue
      case SaveAndReturnAction => returnHome
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
