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
import model.{ AssessmentPassMarksSchemesAndScores, SchemeId }
import model.exchange.ApplicationResult
import model.persisted.{ FsbSchemeResult, SchemeEvaluationResult }
import play.api.Logger
import repositories.application.GeneralApplicationMongoRepository
import repositories.fsb.{ FsbMongoRepository, FsbRepository }
import repositories.{ SchemeRepository, SchemeYamlRepository }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

object FsbService extends FsbService {
  override val applicationRepo: GeneralApplicationMongoRepository = repositories.applicationRepository
  override val fsbRepo: FsbMongoRepository = repositories.fsbRepository
  override val schemeRepo: SchemeYamlRepository.type = SchemeYamlRepository
}

trait FsbService {
  val applicationRepo: GeneralApplicationMongoRepository
  val fsbRepo: FsbRepository
  val schemeRepo: SchemeRepository

  val logPrefix = "[FsbEvaluation]"

  def nextFsbCandidateReadyForEvaluation: Future[Option[AssessmentPassMarksSchemesAndScores]] = {
    fsbRepo.nextApplicationReadyForFsbEvaluation().flatMap {
          case Some(appId) =>
            Logger.debug(s"$logPrefix Found candidate to process - applicationId = $appId")
            tryToFindEvaluationData(appId, passmark)
          case None =>
            Logger.debug(s"$logPrefix Completed - no candidates found")
            Future.successful(None)
        }

  }
  def saveResults(schemeId: SchemeId, applicationResults: List[ApplicationResult]): Future[List[Unit]] = {
    Future.sequence(
      applicationResults.map(applicationResult => saveResult(schemeId, applicationResult))
    )
  }

  def saveResult(schemeId: SchemeId, applicationResult: ApplicationResult): Future[Unit] = {
    saveResult(applicationResult.applicationId, SchemeEvaluationResult(schemeId, applicationResult.result))
  }

  def saveResult(applicationId: String, schemeEvaluationResult: SchemeEvaluationResult): Future[Unit] = {
    for {
      _ <- fsbRepo.save(applicationId, schemeEvaluationResult)
      _ <- applicationRepo.addProgressStatusAndUpdateAppStatus(applicationId, FSB_RESULT_ENTERED)
    } yield ()
  }

  def findByApplicationIdsAndFsbType(applicationIds: List[String], mayBeFsbType: Option[String]): Future[List[FsbSchemeResult]] = {
    val maybeSchemeId = mayBeFsbType.flatMap { fsb =>
      Try(schemeRepo.getSchemeForFsb(fsb)).toOption
    }.map(_.id)
    findByApplicationIdsAndScheme(applicationIds, maybeSchemeId)
  }

  def findByApplicationIdsAndScheme(applicationIds: List[String], mayBeSchemeId: Option[SchemeId]): Future[List[FsbSchemeResult]] = {
    fsbRepo.findByApplicationIds(applicationIds, mayBeSchemeId)
  }

}
