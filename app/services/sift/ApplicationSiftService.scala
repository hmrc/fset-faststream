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

package services.sift

import common.FutureEx
import factories.DateTimeFactory
import model.EvaluationResults.Green
import model.{ ProgressStatuses, SchemeId, SerialUpdateResult, SiftRequirement }
import model.command.ApplicationForSift
import model.persisted.SchemeEvaluationResult
import reactivemongo.bson.BSONDocument
import repositories.{ CommonBSONDocuments, CurrentSchemeStatusHelper, SchemeRepository, SchemeYamlRepository }
import repositories.application.{ GeneralApplicationMongoRepository, GeneralApplicationRepository }
import repositories.sift.{ ApplicationSiftMongoRepository, ApplicationSiftRepository }

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

object ApplicationSiftService extends ApplicationSiftService {
  val applicationSiftRepo: ApplicationSiftMongoRepository = repositories.applicationSiftRepository
  val applicationRepo: GeneralApplicationMongoRepository = repositories.applicationRepository
  val schemeRepo = SchemeYamlRepository
  val dateTimeFactory = DateTimeFactory
}

trait ApplicationSiftService extends CurrentSchemeStatusHelper with CommonBSONDocuments {
  def applicationSiftRepo: ApplicationSiftRepository
  def applicationRepo: GeneralApplicationRepository
  def schemeRepo: SchemeRepository

  def nextApplicationsReadyForSiftStage(batchSize: Int): Future[Seq[ApplicationForSift]] = {
    applicationSiftRepo.nextApplicationsForSiftStage(batchSize)
  }

  private def requiresForms(schemeIds: Seq[SchemeId]) = {
    schemeRepo.getSchemesForIds(schemeIds).exists(_.siftRequirement.contains(SiftRequirement.FORM))
  }

  private def progressStatusForSiftStage(app: ApplicationForSift) = if (requiresForms(app.currentSchemeStatus.map(_.schemeId))) {
    ProgressStatuses.SIFT_ENTERED
  } else {
    ProgressStatuses.SIFT_READY
  }

  def progressApplicationToSiftStage(applications: Seq[ApplicationForSift]): Future[SerialUpdateResult[ApplicationForSift]] = {
    val updates = FutureEx.traverseSerial(applications) { application =>

      FutureEx.futureToEither(application,
        applicationRepo.addProgressStatusAndUpdateAppStatus(application.applicationId, progressStatusForSiftStage(application))
      )
    }

    updates.map(SerialUpdateResult.fromEither)
  }

  def findApplicationsReadyForSchemeSift(schemeId: SchemeId): Future[Seq[model.Candidate]] = {
    applicationSiftRepo.findApplicationsReadyForSchemeSift(schemeId)
  }

  def siftApplicationForScheme(applicationId: String, result: SchemeEvaluationResult): Future[Unit] = {

    def maybeSetProgressStatus(siftedSchemes: Set[SchemeId], siftableSchemes: Set[SchemeId]) =
      if (siftedSchemes equals  siftableSchemes) {
        progressStatusOnlyBSON(ProgressStatuses.SIFT_COMPLETED)
      } else {
        BSONDocument.empty
      }

    (for {
      currentSchemeStatus <- applicationRepo.getCurrentSchemeStatus(applicationId)
      currentSiftEvaluation <- applicationSiftRepo.getSiftEvaluations(applicationId)
    } yield {
      val newSchemeStatus = calculateCurrentSchemeStatus(currentSchemeStatus, result :: Nil)
      val candidatesGreenSchemes = currentSchemeStatus.collect { case s if s.result == Green.toString => s.schemeId }
      val candidatesSiftableSchemes = schemeRepo.siftableSchemeIds.filter(s => candidatesGreenSchemes.contains(s))
      val siftedSchemes = (currentSiftEvaluation.map(_.schemeId) :+ result.schemeId).distinct

      val settableFields = Seq(currentSchemeStatusBSON(newSchemeStatus),
        maybeSetProgressStatus(siftedSchemes.toSet, candidatesSiftableSchemes.toSet)
      )

      play.api.Logger.error(s"\n\nBLAH : ${settableFields.map(BSONDocument.pretty)}")
      applicationSiftRepo.siftApplicationForScheme(applicationId, result, settableFields)

    }) flatMap identity
  }
}
