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

import model.ApplicationRoute.ApplicationRoute
import model.EvaluationResults.Green
import model.persisted.SchemeEvaluationResult

import javax.inject.{Inject, Singleton}
import model.{ApplicationRoute, ApplicationValidator, ProgressStatuses, SelectedSchemes}
import model.stc.{AuditEvents, DataStoreEvents, EmailEvents}
import play.api.Logging
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
                                            ) extends BackendController(cc) with EventSink with Logging {

  implicit val ec: ExecutionContext = cc.executionContext

  def submitApplication(userId: String, applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    val personalDetailsFuture = pdRepository.find(applicationId)
    val assistanceDetailsFuture = adRepository.find(applicationId)
    val contactDetailsFuture = cdRepository.find(userId)
    val schemePreferencesFuture = spRepository.find(applicationId)
    val schemesLocationsFuture = frameworkPrefRepository.tryGetPreferences(applicationId)
    val applicationStatusFuture = appRepository.findStatus(applicationId)

    val result = for {
      pd <- personalDetailsFuture
      ad <- assistanceDetailsFuture
      cd <- contactDetailsFuture
      sp <- schemePreferencesFuture
      as <- applicationStatusFuture
      sl <- schemesLocationsFuture
      availableRegions <- frameworkRegionsRepository.getFrameworksByRegionFilteredByQualification(CandidateHighestQualification.from(pd))
    } yield {
      if (ApplicationValidator(pd, ad, sl, availableRegions).validate) {
        for {
          _ <- submit(applicationId, cd.email, pd.preferredName, as.applicationRoute)
          _ <- createCurrentSchemeStatus(applicationId, sp)
          _ <- saveSocioEconomicScore(applicationId)
          _ <- postSubmissionCheck(applicationId, cd.email, pd.preferredName)
        } yield Ok
      } else {
        Future.successful(BadRequest)
      }
    }

    result flatMap identity
  }

  private def submit(applicationId: String, email: String, preferredName: String, applicationRoute: ApplicationRoute)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val submissionEvents = applicationRoute match {
      case ApplicationRoute.Faststream =>
        // All candidates are submitted but only Faststream candidates receive an email at submission
        DataStoreEvents.ApplicationSubmitted(applicationId) ::
          EmailEvents.ApplicationSubmitted(email, preferredName) ::
          AuditEvents.ApplicationSubmitted(applicationId) ::
          Nil
      case _ =>
        // Sdip candidates do not receive an email at submission. They receive an email depending on the post submission SEB check
        DataStoreEvents.ApplicationSubmitted(applicationId) ::
          AuditEvents.ApplicationSubmitted(applicationId) ::
          Nil
    }
    eventSink {
      appRepository.submit(applicationId) map { _ => submissionEvents }
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

  private def postSubmissionCheck(applicationId: String, email: String, preferredName: String)(implicit hc: HeaderCarrier, rh: RequestHeader) = {
    for {
      postSubmissionData <- appRepository.findPostSubmissionCheckData(applicationId)
    } yield {
      postSubmissionData.applicationRoute match {
        case model.ApplicationRoute.Faststream =>
          logger.debug(s"[postSubmissionCheck] $applicationId - dealing with a Faststream candidate so adding SUBMITTED_CHECK_PASSED")
          val newStatus = ProgressStatuses.SUBMITTED_CHECK_PASSED
          for {
            _ <- appRepository.addProgressStatusAndUpdateAppStatus(applicationId, newStatus)
          } yield ()
        case _ =>
          // Sdip candidate
          val lowerSebScores = Seq("SE-4", "SE-5")
          val newStatus = if (lowerSebScores.contains(postSubmissionData.socioEconomicScore)) {
            logger.debug(s"[postSubmissionCheck] $applicationId - dealing with a Sdip candidate with lower " +
              s"SEB score ${postSubmissionData.socioEconomicScore} so adding SUBMITTED_CHECK_PASSED")
            ProgressStatuses.SUBMITTED_CHECK_PASSED
          } else {
            logger.debug(s"[postSubmissionCheck] $applicationId - dealing with a Sdip candidate with higher " +
              s"SEB score ${postSubmissionData.socioEconomicScore} so adding SUBMITTED_CHECK_FAILED")
            ProgressStatuses.SUBMITTED_CHECK_FAILED
          }
          for {
            _ <- appRepository.addProgressStatusAndUpdateAppStatus(applicationId, newStatus)
            _ <- sendPostSubmittedCheckEmail(newStatus, applicationId, email, preferredName)
          } yield ()
      }
    }
  }

  // Only applicable for Sdip candidates
  private def sendPostSubmittedCheckEmail(
                                           postSubmissionStatus: ProgressStatuses.ProgressStatus,
                                           applicationId: String,
                                           email: String,
                                           preferredName: String)(
    implicit hc: HeaderCarrier, rh: RequestHeader) =
    postSubmissionStatus match {
      case ProgressStatuses.SUBMITTED_CHECK_PASSED =>
        logger.debug(s"[postSubmissionCheck] $applicationId - will send ApplicationPostSubmittedCheckPassed email")

        for {
          _ <- eventSink { EmailEvents.ApplicationPostSubmittedCheckPassed(email, preferredName) :: Nil }
        } yield ()
      case _ =>
        // This handles SUBMITTED_CHECK_FAILED
        logger.debug(s"[postSubmissionCheck] $applicationId - will send ApplicationPostSubmittedCheckFailed email")

        for {
          _ <- eventSink { EmailEvents.ApplicationPostSubmittedCheckFailed(email, preferredName) :: Nil }
          _ = logger.debug(s"[postSubmissionCheck] $applicationId - now adding SUBMITTED_CHECK_FAILED_NOTIFIED")
          _ <- appRepository.addProgressStatusAndUpdateAppStatus(applicationId, ProgressStatuses.SUBMITTED_CHECK_FAILED_NOTIFIED)
        } yield ()
    }
}
