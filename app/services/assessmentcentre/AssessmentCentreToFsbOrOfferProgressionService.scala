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

package services.assessmentcentre

import common.FutureEx
import connectors.{ AuthProviderClient, CSREmailClient, EmailClient }
import model.EvaluationResults.Green
import model._
import model.command.ApplicationForProgression
import model.persisted.SchemeEvaluationResult
import play.api.Logger
import repositories.{ CurrentSchemeStatusHelper, SchemeYamlRepository }
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.fsb.FsbRepository
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AssessmentCentreToFsbOrOfferProgressionService extends AssessmentCentreToFsbOrOfferProgressionService {
  val fsbRequiredSchemeIds: Seq[SchemeId] = SchemeYamlRepository.fsbSchemeIds
  val applicationRepo = repositories.applicationRepository
  val contactDetailsRepo = repositories.faststreamContactDetailsRepository
  val fsbRepo = repositories.fsbRepository
  val emailClient = CSREmailClient
}

trait AssessmentCentreToFsbOrOfferProgressionService extends CurrentSchemeStatusHelper {

  def fsbRequiredSchemeIds: Seq[SchemeId]

  def applicationRepo: GeneralApplicationRepository

  def contactDetailsRepo: ContactDetailsRepository

  def fsbRepo: FsbRepository

  def emailClient: EmailClient

  def nextApplicationsForFsbOrJobOffer(batchSize: Int): Future[Seq[ApplicationForProgression]] = {
    fsbRepo.nextApplicationForFsbOrJobOfferProgression(batchSize)
  }

  def progressApplicationsToFsbOrJobOffer(applications: Seq[ApplicationForProgression])(implicit hc: HeaderCarrier)
  : Future[SerialUpdateResult[ApplicationForProgression]] = {

    def maybeProgressToFsbOrJobOffer(
      application: ApplicationForProgression,
      firstResidual: SchemeEvaluationResult)(implicit hc: HeaderCarrier): Future[Unit] = {

      if (firstResidual.result == Green.toString && fsbRequiredSchemeIds.contains(firstResidual.schemeId)) {
        fsbRepo.progressToFsb(application).flatMap { _ =>
          retrieveCandidateDetails(application.applicationId).flatMap { case (candidate, cd) =>
            emailClient.sendCandidateAssessmentCompletedMovedToFsb(cd.email, candidate.name)
          }
        }.map(_ => ())
      } else if (firstResidual.result == Green.toString) {
        fsbRepo.progressToJobOffer(application)
      } else {
        Future.successful(())
      }
    }

    val updates = FutureEx.traverseSerial(applications) { application =>
      FutureEx.futureToEither(application,
        withErrLogging("Failed while progress to fsb or job offer") {
          for {
            currentSchemeStatus <- applicationRepo.getCurrentSchemeStatus(application.applicationId)
            firstResidual = firstResidualPreference(currentSchemeStatus).get
            _ <- maybeProgressToFsbOrJobOffer(application, firstResidual)
          } yield ()
        }
      )
    }
    updates.map(SerialUpdateResult.fromEither)
  }

  private def retrieveCandidateDetails(applicationId: String)(implicit hc: HeaderCarrier) = {
    applicationRepo.find(applicationId).flatMap {
      case Some(app) => contactDetailsRepo.find(app.userId).map {cd => (app, cd)}
      case None => sys.error(s"Can't find application $applicationId")
    }
  }

  private def withErrLogging[T](logPrefix: String)(f: Future[T]): Future[T] = {
    f.recoverWith { case ex => Logger.warn(s"$logPrefix: ${ex.getMessage}"); f }
  }

}