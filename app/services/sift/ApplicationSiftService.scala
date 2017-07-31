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
import model.{ Commands, ProgressStatuses, SchemeId, SerialUpdateResult }
import model.command.ApplicationForSift
import model.persisted.SchemeEvaluationResult
import repositories.CurrentSchemeStatusHelper
import repositories.application.{ GeneralApplicationMongoRepository, GeneralApplicationRepository }
import repositories.sift.{ ApplicationSiftMongoRepository, ApplicationSiftRepository }

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

object ApplicationSiftService extends ApplicationSiftService {
  val applicationSiftRepo: ApplicationSiftMongoRepository = repositories.applicationSiftRepository
  val applicationRepo: GeneralApplicationMongoRepository = repositories.applicationRepository
}

trait ApplicationSiftService extends CurrentSchemeStatusHelper{
  def applicationSiftRepo: ApplicationSiftRepository
  def applicationRepo: GeneralApplicationRepository

  def nextApplicationsReadyForSiftStage(batchSize: Int): Future[Seq[ApplicationForSift]] = {
    applicationSiftRepo.nextApplicationsForSiftStage(batchSize)
  }

  def progressApplicationToSiftStage(applications: Seq[ApplicationForSift]): Future[SerialUpdateResult[ApplicationForSift]] = {
    val updates = FutureEx.traverseSerial(applications) { application =>
      FutureEx.futureToEither(application,
        applicationRepo.addProgressStatusAndUpdateAppStatus(application.applicationId, ProgressStatuses.ALL_SCHEMES_SIFT_ENTERED)
      )
    }

    updates.map(SerialUpdateResult.fromEither)
  }

  def findApplicationsReadyForSchemeSift(schemeId: SchemeId): Future[Seq[Commands.Candidate]] = {
    applicationSiftRepo.findApplicationsReadyForSchemeSift(schemeId)
  }

  def siftApplicationForScheme(applicationId: String, result: SchemeEvaluationResult): Future[Unit] = {
    applicationRepo.getCurrentSchemeStatus(applicationId).flatMap { currentSchemeStatus =>
      val newSchemeStatus = calculateCurrentSchemeStatus(currentSchemeStatus, result :: Nil)
      val (predicate, update) = applicationSiftRepo.siftApplicationForSchemeBSON(applicationId, result)
      val action = s"Sifting application for $SchemeId"

      applicationSiftRepo.update(applicationId, predicate, update ++ currentSchemeStatusBSON(newSchemeStatus), action).map(_ => ())
    }
  }
}
