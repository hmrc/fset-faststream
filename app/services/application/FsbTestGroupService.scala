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

import model.SchemeId
import model.exchange.ApplicationResult
import model.persisted.{ FsbResult, SchemeEvaluationResult }
import repositories.application.FsbTestGroupRepository

import scala.concurrent.Future

object FsbTestGroupService extends FsbTestGroupService {
  override val fsbTestGroupRepository = repositories.fsbTestGroupRepository
}

trait FsbTestGroupService {
  val fsbTestGroupRepository: FsbTestGroupRepository

  def saveResults(schemeId: SchemeId, applicationResults: List[ApplicationResult]): Future[Unit] = {
    Future.successful(
      applicationResults.foreach(applicationResult => saveResult(schemeId, applicationResult))
    )
  }

  def saveResult(schemeId: SchemeId, applicationResult: ApplicationResult): Future[Unit] = {
    val schemeEvaluationResult = SchemeEvaluationResult(schemeId, applicationResult.result)
    fsbTestGroupRepository.save(applicationResult.applicationId, schemeEvaluationResult)
  }

  def find(applicationIds: List[String]): Future[List[FsbResult]] = {
    fsbTestGroupRepository.findByApplicationIds(applicationIds)
  }
}
