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
import config.AssessmentEvaluationMinimumCompetencyLevel
import model.EvaluationResults.Green
import model.ProgressStatuses.{ ASSESSMENT_CENTRE_FAILED, ASSESSMENT_CENTRE_PASSED, ASSESSMENT_CENTRE_SCORES_ACCEPTED }
import model._
import model.command.ApplicationForProgression
import model.exchange.passmarksettings.AssessmentCentrePassMarkSettings
import model.persisted.SchemeEvaluationResult
import model.persisted.fsac.{ AnalysisExercise, AssessmentCentreTests }
import play.api.Logger
import repositories.{ AssessmentScoresRepository, CurrentSchemeStatusHelper }
import repositories.application.GeneralApplicationRepository
import repositories.assessmentcentre.AssessmentCentreRepository
import repositories.fsb.FsbRepository
import services.assessmentcentre.AssessmentCentreService.CandidateAlreadyHasAnAnalysisExerciseException
import services.evaluation.AssessmentCentreEvaluationEngine
import services.passmarksettings.{ AssessmentCentrePassMarkSettingsService, PassMarkSettingsService }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AssessmentCentreToFsbOrOfferProgressionService extends AssessmentCentreToFsbOrOfferProgressionService {
  val applicationRepo = repositories.applicationRepository
  val fsbRepo = repositories.fsbRepository
}

trait AssessmentCentreToFsbOrOfferProgressionService extends CurrentSchemeStatusHelper {
  def applicationRepo: GeneralApplicationRepository
  def fsbRepo: FsbRepository

  def nextApplicationsForFsbOrJobOffer(batchSize: Int): Future[Seq[ApplicationForProgression]] = {
    fsbRepo.nextApplicationForFsbOrJobOfferProgression(batchSize)
  }

  def progressApplicationsToFsbOrJobOffer(applications: Seq[ApplicationForProgression])
  : Future[SerialUpdateResult[ApplicationForProgression]] = {
    val updates = FutureEx.traverseSerial(applications) { application =>
      FutureEx.futureToEither(application,
        for {
          currentSchemeStatus <- applicationRepo.getCurrentSchemeStatus(application.applicationId)
          firstResidualPreference <-
        }
        fsbRepo.progressToAssessmentCentre(application,
          ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)
      )
    }

    updates.map(SerialUpdateResult.fromEither)
  }
}
