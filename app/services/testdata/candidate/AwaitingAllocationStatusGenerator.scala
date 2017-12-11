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

package services.testdata.candidate

import model.testdata.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import repositories._
import repositories.onlinetesting.Phase1TestRepository

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier

object AwaitingAllocationStatusGenerator extends AwaitingAllocationStatusGenerator {
  override val previousStatusGenerator = CreatedStatusGenerator // TODO: Fix this in faststream once the appropriate prior stage is complete
  override val otRepository = phase1TestRepository
}

trait AwaitingAllocationStatusGenerator extends ConstructiveGenerator {
  val otRepository: Phase1TestRepository

  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader) = {

    /*
    def getEvaluationResult(candidate: DataGenerationResponse): RuleCategoryResult = {
      RuleCategoryResult(
        generatorConfig.loc1scheme1Passmark.getOrElse(Random.passmark),
        generatorConfig.loc1scheme2Passmark,
        None,
        None,
        None
      )
    }*/

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      // TODO FAST STREAM FIX ME
      //_ <- otRepository.savePassMarkScore(candidateInPreviousStatus.applicationId.get, UUID.randomUUID().toString,
      //  getEvaluationResult(candidateInPreviousStatus), ApplicationStatuses.AwaitingAllocation)
    } yield {
      candidateInPreviousStatus
    }
  }
}
