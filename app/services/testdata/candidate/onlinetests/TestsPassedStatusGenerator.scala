/*
 * Copyright 2019 HM Revenue & Customs
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

package services.testdata.candidate.onlinetests

import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import factories.UUIDFactory
import model.EvaluationResults
import model.ProgressStatuses.{ PHASE1_TESTS_PASSED, PHASE2_TESTS_PASSED, PHASE3_TESTS_PASSED, ProgressStatus }
import model.persisted.{ PassmarkEvaluation, SchemeEvaluationResult }
import model.testdata.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.GeneralApplicationMongoRepository
import repositories.onlinetesting._
import services.testdata.candidate.ConstructiveGenerator
import services.testdata.candidate.onlinetests.phase1.Phase1TestsResultsReceivedStatusGenerator
import services.testdata.candidate.onlinetests.phase2.Phase2TestsResultsReceivedStatusGenerator
import services.testdata.candidate.onlinetests.phase3.Phase3TestsResultsReceivedStatusGenerator

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object Phase1TestsPassedStatusGenerator extends TestsPassedStatusGenerator {
  val previousStatusGenerator = Phase1TestsResultsReceivedStatusGenerator
  val evaluationRepository: Phase1EvaluationMongoRepository = faststreamPhase1EvaluationRepository
  val passedStatus = PHASE1_TESTS_PASSED

  def passmarkEvaluation(generatorConfig: CreateCandidateData, dgr: CreateCandidateResponse): PassmarkEvaluation = {
    generatorConfig.phase1TestData.flatMap(_.passmarkEvaluation)
      .getOrElse {
        val schemeEvaluation = dgr.schemePreferences.map(_.schemes.map(scheme =>
          SchemeEvaluationResult(scheme, EvaluationResults.Green.toString))
        ).getOrElse(Nil)
        val passmarkVersion = UUIDFactory.generateUUID().toString
        val resultVersion = UUIDFactory.generateUUID().toString
        PassmarkEvaluation(passmarkVersion, None, schemeEvaluation, resultVersion, None)
      }
  }

  def updateGenerationResponse(dgr: CreateCandidateResponse, pme: PassmarkEvaluation): CreateCandidateResponse = dgr.copy(
    phase1TestGroup = dgr.phase1TestGroup.map( p1 => p1.copy(schemeResult = Some(pme)))
  )
}

object Phase2TestsPassedStatusGenerator extends TestsPassedStatusGenerator {
  val previousStatusGenerator = Phase2TestsResultsReceivedStatusGenerator
  val evaluationRepository: Phase2EvaluationMongoRepository = faststreamPhase2EvaluationRepository
  val passedStatus = PHASE2_TESTS_PASSED

  def passmarkEvaluation(generatorConfig: CreateCandidateData, dgr: CreateCandidateResponse): PassmarkEvaluation =
    generatorConfig.phase2TestData.flatMap(_.passmarkEvaluation)
      .getOrElse {
        val schemeEvaluation = dgr.schemePreferences.map(_.schemes.map(scheme => SchemeEvaluationResult(scheme,
          EvaluationResults.Green.toString
        ))).getOrElse(Nil)
        val passmarkVersion = UUIDFactory.generateUUID().toString
        val resultVersion = UUIDFactory.generateUUID().toString
        PassmarkEvaluation(passmarkVersion, dgr.phase1TestGroup.flatMap(_.schemeResult.map(_.passmarkVersion)),
          schemeEvaluation, resultVersion, dgr.phase1TestGroup.flatMap(_.schemeResult.map(_.resultVersion)))
      }

  def updateGenerationResponse(dgr: CreateCandidateResponse, pme: PassmarkEvaluation): CreateCandidateResponse = dgr.copy(
    phase2TestGroup = dgr.phase2TestGroup.map( p2 => p2.copy(schemeResult = Some(pme)))
  )
}

object Phase3TestsPassedStatusGenerator extends TestsPassedStatusGenerator {
  val previousStatusGenerator = Phase3TestsResultsReceivedStatusGenerator
  val evaluationRepository: Phase3EvaluationMongoRepository = faststreamPhase3EvaluationRepository
  val appRepository: GeneralApplicationMongoRepository = applicationRepository
  val passedStatus = PHASE3_TESTS_PASSED

  def passmarkEvaluation(generatorConfig: CreateCandidateData, dgr: CreateCandidateResponse): PassmarkEvaluation =
    generatorConfig.phase3TestData.flatMap(_.passmarkEvaluation)
      .getOrElse {
        val schemeEvaluation = dgr.schemePreferences.map(_.schemes.map(scheme => SchemeEvaluationResult(scheme,
          EvaluationResults.Green.toString
        ))).getOrElse(Nil)
        val passmarkVersion = UUIDFactory.generateUUID().toString
        val resultVersion = UUIDFactory.generateUUID().toString
        PassmarkEvaluation(passmarkVersion, dgr.phase2TestGroup.flatMap(_.schemeResult.map(_.passmarkVersion)),
          schemeEvaluation, resultVersion, dgr.phase2TestGroup.flatMap(_.schemeResult.map(_.resultVersion)))
      }

  def updateGenerationResponse(dgr: CreateCandidateResponse, pme: PassmarkEvaluation): CreateCandidateResponse = dgr.copy(
    phase3TestGroup = dgr.phase3TestGroup.map( p3 => p3.copy(schemeResult = Some(pme)))
  )
}

trait TestsPassedStatusGenerator extends ConstructiveGenerator {
  val evaluationRepository: OnlineTestEvaluationRepository
  val passedStatus: ProgressStatus
  def passmarkEvaluation(generatorConfig: CreateCandidateData, dgr: CreateCandidateResponse): PassmarkEvaluation
  def updateGenerationResponse(dgr: CreateCandidateResponse, pme: PassmarkEvaluation): CreateCandidateResponse

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
              (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse] = {

    previousStatusGenerator.generate(generationId, generatorConfig).flatMap { candidate =>
      val evaluation = passmarkEvaluation(generatorConfig, candidate)
      evaluationRepository.savePassmarkEvaluation(candidate.applicationId.getOrElse(""), evaluation, Some(passedStatus)).map { _ =>
        updateGenerationResponse(candidate, evaluation)
      }
    }
  }
}
