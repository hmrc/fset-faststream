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
import model.{ Commands, ProgressStatuses, SchemeId, SerialUpdateResult }
import model.command.ApplicationForSift
import model.persisted.SchemeEvaluationResult
import reactivemongo.bson.BSONDocument
import repositories.{ CommonBSONDocuments, CurrentSchemeStatusHelper, SchemeRepositoryImpl, SchemeYamlRepository }
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
  def schemeRepo: SchemeRepositoryImpl

  def nextApplicationsReadyForSiftStage(batchSize: Int): Future[Seq[ApplicationForSift]] = {
    applicationSiftRepo.nextApplicationsForSiftStage(batchSize)
  }

  def progressApplicationToSiftStage(applications: Seq[ApplicationForSift]): Future[SerialUpdateResult[ApplicationForSift]] = {
    val updates = FutureEx.traverseSerial(applications) { application =>
      FutureEx.futureToEither(application,
        applicationRepo.addProgressStatusAndUpdateAppStatus(application.applicationId, ProgressStatuses.SIFT_ENTERED)
      )
    }

    updates.map(SerialUpdateResult.fromEither)
  }

  def findApplicationsReadyForSchemeSift(schemeId: SchemeId): Future[Seq[Commands.Candidate]] = {
    applicationSiftRepo.findApplicationsReadyForSchemeSift(schemeId)
  }

  def siftApplicationForScheme(applicationId: String, result: SchemeEvaluationResult): Future[Unit] = {

    def maybeSetProgressStatus(siftedSchemes: Set[SchemeId], siftableSchemes: Set[SchemeId]) =
      if (siftedSchemes equals  siftableSchemes) {
        BSONDocument("$set" -> progressStatusOnlyBSON(ProgressStatuses.SIFT_COMPLETED))
      } else {
        BSONDocument.empty
      }

    (for {
      currentSchemeStatus <- applicationRepo.getCurrentSchemeStatus(applicationId)
      currentSiftEvaluation <- applicationSiftRepo.getSiftEvaluations(applicationId)
      (predicate, siftBson) = applicationSiftRepo.siftApplicationForSchemeBSON(applicationId, result)
    } yield {
      val newSchemeStatus = calculateCurrentSchemeStatus(currentSchemeStatus, result :: Nil)
      val action = s"Sifting application for ${result.schemeId.value}"
      val candidatesSiftableSchemes = schemeRepo.siftableSchemeIds.filter(s => currentSchemeStatus.map(_.schemeId).contains(s))
      val siftedSchemes = (currentSiftEvaluation.map(_.schemeId) :+ result.schemeId).distinct

      val mergedUpdate = Seq(
        BSONDocument("$set" -> currentSchemeStatusBSON(newSchemeStatus)),
        maybeSetProgressStatus(siftedSchemes.toSet, candidatesSiftableSchemes.toSet)
      ).foldLeft(siftBson) { (acc, doc) => acc ++ doc }

      applicationSiftRepo.update(applicationId, predicate, mergedUpdate, action)
    }) flatMap identity
  }
}
