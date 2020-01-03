/*
 * Copyright 2020 HM Revenue & Customs
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

package services.assessmentcentre

import common.FutureEx
import connectors.{AuthProviderClient, CSREmailClient, EmailClient}
import model.EvaluationResults.Green
import model.ProgressStatuses._
import model._
import model.command.{ApplicationForProgression, ProgressResponse}
import model.fsb.FSBProgressStatus
import model.persisted.{FsbSchemeResult, SchemeEvaluationResult}
import org.joda.time.DateTime
import play.api.Logger
import repositories.{CurrentSchemeStatusHelper, SchemeYamlRepository}
import repositories.application.{GeneralApplicationMongoRepository, GeneralApplicationRepository}
import repositories.contactdetails.{ContactDetailsMongoRepository, ContactDetailsRepository}
import repositories.fsb.{FsbMongoRepository, FsbRepository}
import services.scheme.SchemePreferencesService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object ProgressionToFsbOrOfferService extends ProgressionToFsbOrOfferService {
  val fsbRequiredSchemeIds: Seq[SchemeId] = SchemeYamlRepository.fsbSchemeIds
  val applicationRepo: GeneralApplicationRepository = repositories.applicationRepository
  val contactDetailsRepo: ContactDetailsMongoRepository = repositories.faststreamContactDetailsRepository
  val fsbRepo: FsbMongoRepository = repositories.fsbRepository
  val emailClient: CSREmailClient = CSREmailClient
  val schemePreferencesService: SchemePreferencesService = SchemePreferencesService
}

trait ProgressionToFsbOrOfferService extends CurrentSchemeStatusHelper {

  def fsbRequiredSchemeIds: Seq[SchemeId]

  def applicationRepo: GeneralApplicationRepository

  def contactDetailsRepo: ContactDetailsRepository

  def fsbRepo: FsbRepository

  def emailClient: EmailClient

  def schemePreferencesService: SchemePreferencesService

  def nextApplicationsForFsbOrJobOffer(batchSize: Int): Future[Seq[ApplicationForProgression]] = {
    fsbRepo.nextApplicationForFsbOrJobOfferProgression(batchSize)
  }

  def nextApplicationForFsbOrJobOffer(applicationId: String): Future[Seq[ApplicationForProgression]] = {
    fsbRepo.nextApplicationForFsbOrJobOfferProgression(applicationId)
  }

  // scalastyle:off method.length
  def progressApplicationsToFsbOrJobOffer(applications: Seq[ApplicationForProgression])(implicit hc: HeaderCarrier)
  : Future[SerialUpdateResult[ApplicationForProgression]] = {

    def maybeProgressToFsbOrJobOffer(
      application: ApplicationForProgression,
      latestProgressStatus: ProgressStatus,
      firstResidualOpt: Option[SchemeEvaluationResult])(implicit hc: HeaderCarrier): Future[Unit] = {

      firstResidualOpt.map { firstResidual =>

        if (firstResidual.result == Green.toString && fsbRequiredSchemeIds.contains(firstResidual.schemeId)) {
          fsbRepo.progressToFsb(application).flatMap { _ =>
            retrieveCandidateDetails(application.applicationId).flatMap { case (candidate, cd) =>
              if (latestProgressStatus == ASSESSMENT_CENTRE_PASSED) {
                emailClient.sendCandidateAssessmentCompletedMovedToFsb(cd.email, candidate.name)
              } else {
                Future.successful(())
              }
            }
          }.map(_ => ())
        } else if (firstResidual.result == Green.toString) {
          fsbRepo.progressToJobOffer(application)
        } else {
          Future.successful(())
        }
      }.getOrElse(Future.successful(()))
    }

    def maybeArchiveOldFsbStatuses(application: ApplicationForProgression,
      latestProgressStatus: ProgressStatus, firstResidualOpt: Option[SchemeEvaluationResult]) = {

      def calculateFsbStatusesToArchive(progressTimestamps: Map[String, DateTime]): List[(ProgressStatus, DateTime)] = {

        val progressStatusesToArchive = List(FSB_ALLOCATION_CONFIRMED, FSB_ALLOCATION_UNCONFIRMED, FSB_AWAITING_ALLOCATION,
          FSB_FAILED, FSB_FAILED_TO_ATTEND, FSB_PASSED, FSB_RESULT_ENTERED)

        progressStatusesToArchive.flatMap { statusToArchive =>
          if (progressTimestamps.contains(statusToArchive.toString)) {
            Some(ProgressStatuses.nameToProgressStatus(statusToArchive.toString) -> progressTimestamps(statusToArchive.toString))
          } else {
            Nil
          }
        }
      }

      def calculateLastFsbFailedScheme(schemesInPreferenceOrder: Seq[SchemeId], fsbEvaluation: FsbSchemeResult) = {
        schemesInPreferenceOrder.filter(fsbEvaluation.results.map(_.schemeId).contains).last
      }

      if (latestProgressStatus == FSB_FAILED && firstResidualOpt.exists(firstResidual => firstResidual.result == Green.toString &&
        fsbRequiredSchemeIds.contains(firstResidual.schemeId))) {
        for {
          progressStatusTimestamps <- applicationRepo.getProgressStatusTimestamps(application.applicationId)
          fsbStatusesToArchive = calculateFsbStatusesToArchive(progressStatusTimestamps.toMap)
          schemesInPreferenceOrder = application.currentSchemeStatus.map(_.schemeId)
          fsbEvaluation <- fsbRepo.findByApplicationIds(List(application.applicationId), None)
          lastSchemeFailedAtFsb = calculateLastFsbFailedScheme(schemesInPreferenceOrder, fsbEvaluation.head)
          _ <- fsbRepo.addFsbProgressStatuses(application.applicationId,
            fsbStatusesToArchive.map(x => x._1 + "_" + lastSchemeFailedAtFsb -> x._2)
          )
          _ <- applicationRepo.removeProgressStatuses(application.applicationId, fsbStatusesToArchive.map(_._1))
          _ <- applicationRepo.addProgressStatusAndUpdateAppStatus(application.applicationId, ProgressStatuses.FSB_AWAITING_ALLOCATION)
        } yield ()
      } else {
        Future.successful(())
      }
    }

    val updates = FutureEx.traverseSerial(applications) { application =>
      FutureEx.futureToEither(application,
        withErrLogging("Failed while progressing to fsb or job offer") {
          for {
            //TODO: Filter this with selected schemes? Any regressions?
            schemePreferences <- schemePreferencesService.find(application.applicationId)
            currentSchemeStatusUnfiltered <- applicationRepo.getCurrentSchemeStatus(application.applicationId)
            currentSchemeStatus = currentSchemeStatusUnfiltered.filter(res => schemePreferences.schemes.contains(res.schemeId))
            firstResidual = firstResidualPreference(currentSchemeStatus)
            applicationStatus <- applicationRepo.findStatus(application.applicationId)
            _ <- maybeArchiveOldFsbStatuses(application, applicationStatus.latestProgressStatus.get, firstResidual)
            _ <- maybeProgressToFsbOrJobOffer(application, applicationStatus.latestProgressStatus.get, firstResidual)
          } yield ()
        }
      )
    }
    updates.map(SerialUpdateResult.fromEither)
  }
  // scalastyle:on

  private def retrieveCandidateDetails(applicationId: String)(implicit hc: HeaderCarrier) = {
    applicationRepo.find(applicationId).flatMap {
      case Some(app) => contactDetailsRepo.find(app.userId).map {cd => (app, cd)}
      case None => sys.error(s"Can't find application $applicationId")
    }
  }

  private def withErrLogging[T](logPrefix: String)(f: Future[T]): Future[T] = {
    f.recoverWith {
      case ex: Throwable => Logger.warn(s"$logPrefix: ${ex.getMessage}", ex)
        f
    }
  }

}
