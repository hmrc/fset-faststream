/*
 * Copyright 2023 HM Revenue & Customs
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

import model.EvaluationResults.Green
import model.persisted.SchemeEvaluationResult

import javax.inject.{Inject, Singleton}
import model.{ApplicationValidator, SelectedSchemes}
import model.stc.{AuditEvents, DataStoreEvents, EmailEvents}
import play.api.mvc.{Action, AnyContent, ControllerComponents, RequestHeader}
import repositories.FrameworkRepository.CandidateHighestQualification
import repositories.*
import repositories.application.GeneralApplicationRepository
import repositories.assistancedetails.AssistanceDetailsRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.personaldetails.PersonalDetailsRepository
import repositories.schemepreferences.SchemePreferencesRepository
import services.stc.{EventSink, StcEventService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmitApplicationController @Inject() (cc: ControllerComponents,
                                             pdRepository: PersonalDetailsRepository,
                                             adRepository: AssistanceDetailsRepository,
                                             cdRepository: ContactDetailsRepository,
                                             spRepository: SchemePreferencesRepository,
                                             frameworkPrefRepository: FrameworkPreferenceRepository,
                                             frameworkRegionsRepository: FrameworkRepository,
                                             appRepository: GeneralApplicationRepository,
                                             qRepository: QuestionnaireRepository,
                                             override val eventService: StcEventService
                                            ) extends BackendController(cc) with EventSink {

  implicit val ec: ExecutionContext = cc.executionContext

  def submitApplication(userId: String, applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    val personalDetailsFuture = pdRepository.find(applicationId)
    val assistanceDetailsFuture = adRepository.find(applicationId)
    val contactDetailsFuture = cdRepository.find(userId)
    val schemePreferencesFuture = spRepository.find(applicationId)
    val schemesLocationsFuture = frameworkPrefRepository.tryGetPreferences(applicationId)

    val result = for {
      pd <- personalDetailsFuture
      ad <- assistanceDetailsFuture
      cd <- contactDetailsFuture
      sp <- schemePreferencesFuture
      sl <- schemesLocationsFuture
      availableRegions <- frameworkRegionsRepository.getFrameworksByRegionFilteredByQualification(CandidateHighestQualification.from(pd))
    } yield {
      if (ApplicationValidator(pd, ad, sl, availableRegions).validate) {
        for {
          _ <- submit(applicationId, cd.email, pd.preferredName)
          _ <- createCurrentSchemeStatus(applicationId, sp)
          _ <- saveSocioEconomicScore(applicationId)
        } yield Ok
      } else {
        Future.successful(BadRequest)
      }
    }

    result flatMap identity
  }

  private def submit(applicationId: String, email: String, preferredName: String)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = eventSink {
    // Usually events are created in Service layer. Due to lack of Service layer for submit, they are created here
    appRepository.submit(applicationId) map { _ =>
      DataStoreEvents.ApplicationSubmitted(applicationId) ::
      EmailEvents.ApplicationSubmitted(email, preferredName) ::
      AuditEvents.ApplicationSubmitted(applicationId) ::
      Nil
    }
  }

  private def createCurrentSchemeStatus(applicationId: String, selectedSchemes: SelectedSchemes) =
    for {
      _ <- appRepository.updateCurrentSchemeStatus(
        applicationId,
        // Create a default css with all the schemes set to Green
        selectedSchemes.schemes.map(scheme => SchemeEvaluationResult(scheme.schemeId, Green.toString))
      )
    } yield ()

  private def saveSocioEconomicScore(applicationId: String) = {
    for {
      score <- qRepository.calculateSocioEconomicScore(applicationId)
      _ <- appRepository.saveSocioEconomicScore(applicationId, score)
    } yield ()
  }
}
