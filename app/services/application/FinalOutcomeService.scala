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

package services.application

import common.FutureEx
import connectors.{ AuthProviderClient, CSREmailClient, EmailClient }
import model.ProgressStatuses.{ ASSESSMENT_CENTRE_FAILED, FSB_FAILED }
import model.SerialUpdateResult
import model.command.ApplicationForProgression
import play.api.Logger
import repositories.application.{ FinalOutcomeRepository, GeneralApplicationRepository }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


object FinalOutcomeService extends FinalOutcomeService {

  val applicationRepo = repositories.applicationRepository
  val finalOutcomeRepo = repositories.finalOutcomeRepository
  val emailClient = CSREmailClient
  val authProviderClient = AuthProviderClient


}

trait FinalOutcomeService {

  def applicationRepo: GeneralApplicationRepository
  def finalOutcomeRepo: FinalOutcomeRepository
  def authProviderClient: AuthProviderClient
  def emailClient: EmailClient

  val FinalFailedStates = Seq(ASSESSMENT_CENTRE_FAILED, FSB_FAILED)

  def nextApplicationsFinalFailNotification(batchSize: Int): Future[Seq[ApplicationForProgression]] = {
    finalOutcomeRepo.nextApplicationForFinalFailureNotification(batchSize)
  }

  def nextApplicationsFinalSuccessNotification(batchSize: Int): Future[Seq[ApplicationForProgression]] = {
    finalOutcomeRepo.nextApplicationForFinalSuccessNotification(batchSize)
  }

  def progressApplicationsToFinalSuccessNotified(applications: Seq[ApplicationForProgression])(implicit hc: HeaderCarrier)
  : Future[SerialUpdateResult[ApplicationForProgression]] = {
    FutureEx.traverseSerial(applications) { app =>
      FutureEx.futureToEither(app,
        withErrLogging(s"Final success notification for app ${app.applicationId}") {
          for {
            candidate <- retrieveCandidateDetails(app.applicationId)
            firstSchemeRes = finalOutcomeRepo.firstResidualPreference(app.currentSchemeStatus)
              .getOrElse(sys.error(s"No first residual preference for ${app.applicationId}"))
            _ <- emailClient.notifyCandidateOnFinalSuccess(candidate.email, candidate.name, firstSchemeRes.schemeId.value)
            _ <- finalOutcomeRepo.progressToJobOfferNotified(app)
          } yield ()
        }
      )
    } map SerialUpdateResult.fromEither
  }

  def progressApplicationsToFinalFailureNotified(applications: Seq[ApplicationForProgression])(implicit hc: HeaderCarrier)
  : Future[SerialUpdateResult[ApplicationForProgression]] = {
    FutureEx.traverseSerial(applications) { app =>
      FutureEx.futureToEither(app,
        withErrLogging(s"Final failure notification for app ${app.applicationId}") {
          for {
            candidate <- retrieveCandidateDetails(app.applicationId)
            _ <- emailClient.notifyCandidateOnFinalFailure(candidate.email, candidate.name)
            _ <- finalOutcomeRepo.progressToFinalFailureNotified(app)
          } yield ()
        }
      )
    }.map(SerialUpdateResult.fromEither)
  }

  private def retrieveCandidateDetails(applicationId: String)(implicit hc: HeaderCarrier) = {
    applicationRepo.find(applicationId).flatMap {
      case Some(app) => authProviderClient.findByUserIds(Seq(app.userId))
      case None => sys.error(s"Can't find application $applicationId")
    }.map(_.headOption.getOrElse(sys.error(s"Can't find user records for $applicationId")))
  }

  private def withErrLogging[T](logPrefix: String)(f: Future[T]): Future[T] = {
    f.recoverWith { case ex => Logger.warn(s"$logPrefix: ${ex.getMessage}"); f }
  }

}
