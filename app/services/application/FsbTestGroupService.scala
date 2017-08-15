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

import model.ProgressStatuses.ProgressStatus
import model.{ FsbType, SchemeId }
import model.exchange.ApplicationResult
import model.persisted.{ FsbSchemeResult, SchemeEvaluationResult }
import repositories.application.{ FsbTestGroupRepository, GeneralApplicationMongoRepository }
import services.events.EventsService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object FsbTestGroupService extends FsbTestGroupService {
  override val applicationRepository = repositories.applicationRepository
  override val fsbTestGroupRepository = repositories.fsbTestGroupRepository
  override val eventsService = EventsService
}

trait FsbTestGroupService {
  val applicationRepository: GeneralApplicationMongoRepository
  val fsbTestGroupRepository: FsbTestGroupRepository
  val eventsService: EventsService

  def saveResults(schemeId: SchemeId, applicationResults: List[ApplicationResult]): Future[Unit] = {
    Future.successful(
      applicationResults.foreach { applicationResult =>
        saveResult(schemeId, applicationResult)
      }
    )
  }

  def saveResult(schemeId: SchemeId, applicationResult: ApplicationResult): Future[Unit] = {
    val schemeEvaluationResult = SchemeEvaluationResult(schemeId, applicationResult.result)
    for {
      _ <- fsbTestGroupRepository.save(applicationResult.applicationId, schemeEvaluationResult)
      _ <- applicationRepository.addProgressStatusAndUpdateAppStatus(applicationResult.applicationId, FSB_RESULTS_ENTERED)
    } yield ()
  }

  def findByApplicationIdsAndFsbType(applicationIds: List[String], fsbType: Option[String]): Future[List[FsbSchemeResult]] = {
    for {
      fsbTypes <- eventsService.getFsbTypes
      fsb <- Future(fsbTypes.find(f => fsbType.contains(f.key)))
      schemeId <- Future(fsb.map(f => SchemeId(f.schemeId)))
      fsbSchemes <- findByApplicationIdsAndScheme(applicationIds, schemeId)
    } yield fsbSchemes
  }

  def findByApplicationIdsAndScheme(applicationIds: List[String], schemeId: Option[SchemeId]): Future[List[FsbSchemeResult]] = {
    fsbTestGroupRepository.findByApplicationIds(applicationIds, schemeId)
  }

}
