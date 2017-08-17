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

import model.ProgressStatuses.FSB_RESULT_ENTERED
import model.SchemeId
import model.exchange.ApplicationResult
import model.persisted.{ FsbSchemeResult, SchemeEvaluationResult }
import repositories.application.{ FsbTestGroupRepository, GeneralApplicationMongoRepository }
import repositories.{ SchemeRepository, SchemeYamlRepository }
import repositories.application.FsbTestGroupRepository
import services.events.EventsService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

object FsbTestGroupService extends FsbTestGroupService {
  override val applicationRepository = repositories.applicationRepository
  override val fsbTestGroupRepository = repositories.fsbTestGroupRepository
  override val eventsService = EventsService
  override val schemeRepository = SchemeYamlRepository
}

trait FsbTestGroupService {
  val applicationRepository: GeneralApplicationMongoRepository
  val fsbTestGroupRepository: FsbTestGroupRepository
  val schemeRepository: SchemeRepository
  val eventsService: EventsService

  def saveResults(schemeId: SchemeId, applicationResults: List[ApplicationResult]): Future[List[Unit]] = {
    Future.sequence(
      applicationResults.map(applicationResult => saveResult(schemeId, applicationResult))
    )
  }

  def saveResult(schemeId: SchemeId, applicationResult: ApplicationResult): Future[Unit] = {
    val schemeEvaluationResult = SchemeEvaluationResult(schemeId, applicationResult.result)
    for {
      _ <- fsbTestGroupRepository.save(applicationResult.applicationId, schemeEvaluationResult)
      _ <- applicationRepository.addProgressStatusAndUpdateAppStatus(applicationResult.applicationId, FSB_RESULT_ENTERED)
    } yield ()
  }

  def findByApplicationIdsAndFsbType(applicationIds: List[String], mayBeFsbType: Option[String]): Future[List[FsbSchemeResult]] = {
    val maybeSchemeId = mayBeFsbType.flatMap { fsb =>
      Try(schemeRepository.getSchemeForFsb(fsb)).toOption
    }.map(_.id)
    findByApplicationIdsAndScheme(applicationIds, maybeSchemeId)
  }

  def findByApplicationIdsAndScheme(applicationIds: List[String], mayBeSchemeId: Option[SchemeId]): Future[List[FsbSchemeResult]] = {
    fsbTestGroupRepository.findByApplicationIds(applicationIds, mayBeSchemeId)
  }

}
