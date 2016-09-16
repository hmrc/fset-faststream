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

package services.applicationassessment

import config.AssessmentEvaluationMinimumCompetencyLevel
import connectors.{ CSREmailClient, EmailClient }
import model.ApplicationStatuses
import model.AssessmentEvaluationCommands.AssessmentPassmarkPreferencesAndScores
import model.EvaluationResults._
import model.Exceptions.IncorrectStatusInApplicationException
import model.PersistedObjects.ApplicationForNotification
import play.api.Logger
import repositories.application.GeneralApplicationRepository
import repositories._
import repositories.onlinetests.OnlineTestRepository
import services.AuditService
import services.evaluation.AssessmentCentrePassmarkRulesEngine
import services.passmarksettings.AssessmentCentrePassMarkSettingsService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object ApplicationAssessmentService extends ApplicationAssessmentService {

  val appAssessRepository = applicationAssessmentRepository
  val otRepository = onlineTestRepository
  val aRepository = applicationRepository
  val aasRepository = applicationAssessmentScoresRepository
  val fpRepository = frameworkPreferenceRepository
  val cdRepository = contactDetailsRepository

  val emailClient = CSREmailClient
  val auditService = AuditService

  val passmarkService = AssessmentCentrePassMarkSettingsService
  val passmarkRulesEngine = AssessmentCentrePassmarkRulesEngine
}

trait ApplicationAssessmentService {

  implicit def headerCarrier = new HeaderCarrier()

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val appAssessRepository: ApplicationAssessmentRepository
  val otRepository: OnlineTestRepository
  val aRepository: GeneralApplicationRepository
  val aasRepository: ApplicationAssessmentScoresRepository
  val fpRepository: FrameworkPreferenceRepository
  val cdRepository: ContactDetailsRepository

  val emailClient: EmailClient

  val auditService: AuditService
  val passmarkService: AssessmentCentrePassMarkSettingsService
  val passmarkRulesEngine: AssessmentCentrePassmarkRulesEngine

  def removeFromApplicationAssessmentSlot(applicationId: String) = {

    appAssessRepository.delete(applicationId).flatMap { _ =>

      auditService.logEventNoRequest("ApplicationAssessmentDeleted", Map(
        "applicationId" -> applicationId
      ))

      // TODO FAST STREAM FIX ME
      //otRepository.removeCandidateAllocationStatus(applicationId).map { _ =>
      //  auditService.logEventNoRequest("ApplicationDeallocated", Map(
      //    "applicationId" -> applicationId
      //  ))
      //}
      Future.successful()
    }
  }

  def deleteApplicationAssessment(applicationId: String) = {

    appAssessRepository.delete(applicationId).map { _ =>
      auditService.logEventNoRequest("ApplicationAssessmentDeleted", Map(
        "applicationId" -> applicationId
      ))
    }
  }

  def nextAssessmentCandidateScoreReadyForEvaluation: Future[Option[AssessmentPassmarkPreferencesAndScores]] = {
    passmarkService.getLatestVersion.flatMap {
      case passmark if passmark.schemes.forall(_.overallPassMarks.isDefined) =>
        aRepository.nextApplicationReadyForAssessmentScoreEvaluation(passmark.info.get.version).flatMap {
          case Some(appId) =>
            for {
              scoresOpt <- aasRepository.tryFind(appId)
              preferencesOpt <- fpRepository.tryGetPreferences(appId)
            } yield {
              for {
                scores <- scoresOpt
                preferences <- preferencesOpt
              } yield AssessmentPassmarkPreferencesAndScores(passmark, preferences, scores)
            }
          case None => Future.successful(None)
        }
      case _ =>
        Logger.warn("Passmark settings are not set for all schemes")
        Future.successful(None)
    }
  }

  def evaluateAssessmentCandidateScore(
    scores: AssessmentPassmarkPreferencesAndScores,
    config: AssessmentEvaluationMinimumCompetencyLevel
  ): Future[Unit] = {
    val result = passmarkRulesEngine.evaluate(scores, config)
    val applicationStatus = determineStatus(result)

    aRepository.saveAssessmentScoreEvaluation(scores.scores.applicationId, scores.passmark.info.get.version, result,
      applicationStatus).map { _ =>
      auditNewStatus(scores.scores.applicationId, applicationStatus)
    }
  }

  def processNextAssessmentCentrePassedOrFailedApplication: Future[Unit] = {
    aRepository.nextAssessmentCentrePassedOrFailedApplication.flatMap {
      case Some(application) => {
        Logger.debug(s"processAssessmentCentrePassedOrFailedApplication() with application id [${application.applicationId}] " +
          s"and status [${application.applicationStatus}]")
        for {
          emailAddress <- candidateEmailAddress(application.userId)
          _ <- emailCandidate(application, emailAddress)
          _ <- commitNotifiedStatus(application)
        } yield ()
      }
      case None => Future.successful(())
    }
  }

  private def determineStatus(result: AssessmentRuleCategoryResult): String = result.passedMinimumCompetencyLevel match {
    case Some(false) =>
      ApplicationStatuses.AssessmentCentreFailed
    case _ =>
      val allResults = List(result.location1Scheme1, result.location1Scheme2, result.location2Scheme1, result.location2Scheme2,
        result.alternativeScheme).flatten

      allResults match {
        case _ if allResults.forall(_ == Red) => ApplicationStatuses.AssessmentCentreFailed
        case _ if allResults.contains(Green) => ApplicationStatuses.AssessmentCentrePassed
        case _ => ApplicationStatuses.AwaitingAssessmentCentreReevaluation
      }
  }

  private def auditNewStatus(appId: String, newStatus: String): Unit = {
    val event = newStatus match {
      case ApplicationStatuses.AssessmentCentrePassedNotified => "ApplicationAssessmentPassedNotified"
      case ApplicationStatuses.AssessmentCentreFailedNotified => "ApplicationAssessmentFailedNotified"
      case ApplicationStatuses.AssessmentCentreFailed | ApplicationStatuses.AssessmentCentrePassed |
        ApplicationStatuses.AwaitingAssessmentCentreReevaluation => "ApplicationAssessmentEvaluated"
    }
    Logger.info(s"$event for $appId. The new status: $newStatus")
    auditService.logEventNoRequest(
      event,
      Map("applicationId" -> appId, "applicationStatus" -> newStatus)
    )
  }

  private[applicationassessment] def emailCandidate(application: ApplicationForNotification, emailAddress: String): Future[Unit] = {
    application.applicationStatus match {
      case ApplicationStatuses.AssessmentCentrePassed =>
        emailClient.sendAssessmentCentrePassed(emailAddress, application.preferredName).map { _ =>
          auditNotified("AssessmentCentrePassedEmailed", application, Some(emailAddress))
        }
      case ApplicationStatuses.AssessmentCentreFailed =>
        emailClient.sendAssessmentCentreFailed(emailAddress, application.preferredName).map { _ =>
          auditNotified("AssessmentCentreFailedEmailed", application, Some(emailAddress))
        }
      case _ =>
        Logger.warn(s"We cannot send email to candidate for application [${application.applicationId}] because its status is " +
          s"[${application.applicationStatus}].")
        Future.failed(new IncorrectStatusInApplicationException(
          "Application should have been in ASSESSMENT_CENTRE_FAILED or ASSESSMENT_CENTRE_PASSED status"
        ))
    }
  }

  private def commitNotifiedStatus(application: ApplicationForNotification): Future[Unit] =
    aRepository.updateStatus(application.applicationId, s"${application.applicationStatus}_NOTIFIED").map { _ =>
      auditNewStatus(application.applicationId, s"${application.applicationStatus}_NOTIFIED")
    }

  private def candidateEmailAddress(userId: String): Future[String] =
    cdRepository.find(userId).map(_.email)

  private def auditNotified(event: String, application: ApplicationForNotification, emailAddress: Option[String] = None): Unit = {
    // Only log user ID (not email).
    Logger.info(s"$event for user ${application.userId}")
    auditService.logEventNoRequest(
      event,
      Map("userId" -> application.userId) ++ emailAddress.map("email" -> _).toMap
    )
  }
}
