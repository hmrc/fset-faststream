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

package services.application

import common.FutureEx
import connectors.{ CSREmailClient, EmailClient }
import model.ProgressStatuses.{ ASSESSMENT_CENTRE_FAILED, FSB_FAILED }
import model.{ ProgressStatuses, SerialUpdateResult }
import model.command.ApplicationForProgression
import org.joda.time.DateTime
import repositories.application.{ FinalOutcomeRepository, GeneralApplicationRepository }
import repositories.contactdetails.ContactDetailsRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object FinalOutcomeService extends FinalOutcomeService {

  val contactDetailsRepo = repositories.faststreamContactDetailsRepository
  val applicationRepo = repositories.applicationRepository
  val finalOutcomeRepo = repositories.finalOutcomeRepository
  val emailClient = CSREmailClient
}

trait FinalOutcomeService {

  def contactDetailsRepo: ContactDetailsRepository
  def applicationRepo: GeneralApplicationRepository
  def finalOutcomeRepo: FinalOutcomeRepository
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
        FutureEx.withErrLogging(s"Final success notification for app ${app.applicationId}") {
          for {
            (candidate, contactDetails) <- retrieveCandidateDetails(app.applicationId)
            firstSchemeRes = finalOutcomeRepo.firstResidualPreference(app.currentSchemeStatus)
              .getOrElse(sys.error(s"No first residual preference for ${app.applicationId}"))
            _ <- emailClient.notifyCandidateOnFinalSuccess(contactDetails.email, candidate.name, firstSchemeRes.schemeId.value)
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
        FutureEx.withErrLogging(s"Final failure notification for app ${app.applicationId}") {
          for {
            (candidate, contactDetails) <- retrieveCandidateDetails(app.applicationId)
            _ <- emailClient.notifyCandidateOnFinalFailure(contactDetails.email, candidate.name)
            progressStatuses <- applicationRepo.getProgressStatusTimestamps(app.applicationId)
            _ = progressToNotified(app, progressStatuses)
          } yield ()
        }
      )
    }.map(SerialUpdateResult.fromEither)
  }

  private def progressToNotified(app: ApplicationForProgression, progressStatuses: List[(String, DateTime)]): Future[Unit] = {
    val sorted = progressStatuses.sortBy{ case (_, dt) => dt}(Ordering.fromLessThan(_ isBefore _))
    sorted.last match {
      case (progressStatus, _) if progressStatus == ProgressStatuses.ASSESSMENT_CENTRE_FAILED_SDIP_GREEN.toString =>
        finalOutcomeRepo.progressToAssessmentCentreFailedSdipGreenNotified(app)
      case _ => finalOutcomeRepo.progressToFinalFailureNotified(app)
    }
  }

  private def retrieveCandidateDetails(applicationId: String)(implicit hc: HeaderCarrier) = {
    applicationRepo.find(applicationId).flatMap {
      case Some(app) => contactDetailsRepo.find(app.userId).map { cd => (app, cd) }
      case None => sys.error(s"Can't find application $applicationId")
    }
  }
}
