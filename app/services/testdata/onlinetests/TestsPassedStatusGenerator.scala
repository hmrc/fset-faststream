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

package services.testdata.onlinetests

import connectors.testdata.ExchangeObjects.DataGenerationResponse
import model.ProgressStatuses.{ PHASE1_TESTS_PASSED, PHASE2_TESTS_PASSED, PHASE3_TESTS_PASSED, ProgressStatus }
import model.command.testdata.GeneratorConfig
import model.persisted.PassmarkEvaluation
import play.api.mvc.RequestHeader
import repositories._
import repositories.onlinetesting.OnlineTestEvaluationRepository
import services.testdata.ConstructiveGenerator
import services.testdata.onlinetests.phase1.Phase1TestsResultsReceivedStatusGenerator
import services.testdata.onlinetests.phase2.Phase2TestsResultsReceivedStatusGenerator
import services.testdata.onlinetests.phase3.Phase3TestsResultsReceivedStatusGenerator
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Phase1TestsPassedStatusGenerator extends TestsPassedStatusGenerator {
  val previousStatusGenerator = Phase1TestsResultsReceivedStatusGenerator
  val evaluationRepository = faststreamPhase1EvaluationRepository
  val passedStatus = PHASE1_TESTS_PASSED

  def passmarkEvaluation(generatorConfig: GeneratorConfig) =
    generatorConfig.phase1TestData.flatMap(_.passmarkEvaluation)
      .getOrElse(PassmarkEvaluation("", None, Nil))
}

object Phase2TestsPassedStatusGenerator extends TestsPassedStatusGenerator {
  val previousStatusGenerator = Phase2TestsResultsReceivedStatusGenerator
  val evaluationRepository = faststreamPhase2EvaluationRepository
  val passedStatus = PHASE2_TESTS_PASSED

  def passmarkEvaluation(generatorConfig: GeneratorConfig) =
    generatorConfig.phase2TestData.flatMap(_.passmarkEvaluation)
      .getOrElse(PassmarkEvaluation("", None, Nil))
}

object Phase3TestsPassedStatusGenerator extends TestsPassedStatusGenerator {
  val previousStatusGenerator = Phase3TestsResultsReceivedStatusGenerator
  val evaluationRepository = faststreamPhase3EvaluationRepository
  val appRepository = applicationRepository
  val passedStatus = PHASE3_TESTS_PASSED

  def passmarkEvaluation(generatorConfig: GeneratorConfig) =
    generatorConfig.phase3TestData.flatMap(_.passmarkEvaluation)
      .getOrElse(PassmarkEvaluation("", None, Nil))
}

trait TestsPassedStatusGenerator extends ConstructiveGenerator {
  val evaluationRepository: OnlineTestEvaluationRepository
  val passedStatus: ProgressStatus
  def passmarkEvaluation(generatorConfig: GeneratorConfig): PassmarkEvaluation

  def generate(generationId: Int, generatorConfig: GeneratorConfig)
              (implicit hc: HeaderCarrier, rh: RequestHeader): Future[DataGenerationResponse] = {
    for {
      candidate <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- evaluationRepository.savePassmarkEvaluation(candidate.applicationId.getOrElse(""),
        passmarkEvaluation(generatorConfig), Some(passedStatus))
    } yield candidate
  }
}
