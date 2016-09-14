/*
 * Copyright 2016 HM Revenue & Customs
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

package services.testdata

import model.ApplicationStatuses
import model.EvaluationResults.{ Amber, RuleCategoryResult }
import repositories._
import repositories.application.OnlineTestRepository
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

object AwaitingOnlineTestReevaluationStatusGenerator extends AwaitingOnlineTestReevaluationStatusGenerator {
  override val previousStatusGenerator = OnlineTestCompletedStatusGenerator
  override val otRepository = onlineTestRepository
}

trait AwaitingOnlineTestReevaluationStatusGenerator extends ConstructiveGenerator {
  val otRepository: OnlineTestRepository

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {
    val ruleCategory = RuleCategoryResult(Amber, Some(Amber), Some(Amber), Some(Amber), Some(Amber))
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      // TODO FAST STREAM FIX ME
      //_ <- otRepository.savePassMarkScore(candidateInPreviousStatus.applicationId.get, "version2", ruleCategory,
      //  ApplicationStatuses.AwaitingOnlineTestReevaluation)
    } yield {
      candidateInPreviousStatus
    }
  }
}
