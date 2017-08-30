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
import model.EvaluationResults.Green
import model._
import model.command.ApplicationForProgression
import model.persisted.SchemeEvaluationResult
import repositories.{ CurrentSchemeStatusHelper, SchemeYamlRepository }
import repositories.application.GeneralApplicationRepository
import repositories.fsb.FsbRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AssessmentCentreToFsbOrOfferProgressionService extends AssessmentCentreToFsbOrOfferProgressionService {
  val fsbRequiredSchemeIds: Seq[SchemeId] = SchemeYamlRepository.fsbSchemeIds
  val applicationRepo = repositories.applicationRepository
  val fsbRepo = repositories.fsbRepository
}

trait AssessmentCentreToFsbOrOfferProgressionService extends CurrentSchemeStatusHelper {
  val fsbRequiredSchemeIds: Seq[SchemeId]
  def applicationRepo: GeneralApplicationRepository
  def fsbRepo: FsbRepository

  def nextApplicationsForFsbOrJobOffer(batchSize: Int): Future[Seq[ApplicationForProgression]] = {
    fsbRepo.nextApplicationForFsbOrJobOfferProgression(batchSize)
  }

  def progressApplicationsToFsbOrJobOffer(applications: Seq[ApplicationForProgression])
  : Future[SerialUpdateResult[ApplicationForProgression]] = {

    def maybeProgressToFsbOrJobOffer(application: ApplicationForProgression, firstResidual: SchemeEvaluationResult): Future[Unit] = {
      
      if (firstResidual.result == Green.toString && fsbRequiredSchemeIds.contains(firstResidual.schemeId)) {
        fsbRepo.progressToFsb(application)
      } else if (firstResidual.result == Green.toString) {
        fsbRepo.progressToJobOffer(application)
      } else {
        Future.successful(())
      }
    }

    val updates = FutureEx.traverseSerial(applications) { application =>
      FutureEx.futureToEither(application,
        for {
          currentSchemeStatus <- applicationRepo.getCurrentSchemeStatus(application.applicationId)
          firstResidual = firstResidualPreference(currentSchemeStatus).get
          _ <- maybeProgressToFsbOrJobOffer(application, firstResidual)
        } yield ()
      )
    }

    updates.map(SerialUpdateResult.fromEither)
  }
}
