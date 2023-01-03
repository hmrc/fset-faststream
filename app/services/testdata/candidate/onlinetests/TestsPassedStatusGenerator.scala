/*
 * Copyright 2023 HM Revenue & Customs
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

import factories.UUIDFactory
import javax.inject.{ Inject, Named, Singleton }
import model.EvaluationResults
import model.ProgressStatuses.{ PHASE1_TESTS_PASSED, PHASE2_TESTS_PASSED, PHASE3_TESTS_PASSED, ProgressStatus }
import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.persisted.{ PassmarkEvaluation, SchemeEvaluationResult }
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import repositories.onlinetesting._
import services.testdata.candidate.ConstructiveGenerator
import services.testdata.candidate.onlinetests.phase1.Phase1TestsResultsReceivedStatusGenerator
import services.testdata.candidate.onlinetests.phase2.Phase2TestsResultsReceivedStatusGenerator
import services.testdata.candidate.onlinetests.phase3.Phase3TestsResultsReceivedStatusGenerator
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait TestsPassedStatusGenerator extends ConstructiveGenerator {
  val evaluationRepository: OnlineTestEvaluationRepository
  val passedStatus: ProgressStatus
  def passmarkEvaluation(generatorConfig: CreateCandidateData, dgr: CreateCandidateResponse): PassmarkEvaluation
  def updateGenerationResponse(dgr: CreateCandidateResponse, pme: PassmarkEvaluation): CreateCandidateResponse

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
              (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse] = {
    previousStatusGenerator.generate(generationId, generatorConfig).flatMap { candidateResponse =>
      val evaluation = passmarkEvaluation(generatorConfig, candidateResponse)
      evaluationRepository.savePassmarkEvaluation(candidateResponse.applicationId.getOrElse(""), evaluation, Some(passedStatus)).map { _ =>
        updateGenerationResponse(candidateResponse, evaluation)
      }
    }
  }
}

@Singleton
class Phase1TestsPassedStatusGenerator @Inject() (val previousStatusGenerator: Phase1TestsResultsReceivedStatusGenerator,
                                                  @Named("Phase1EvaluationRepository")
                                                  val evaluationRepository: OnlineTestEvaluationRepository,
                                                  val uuidFactory: UUIDFactory
                                                 ) extends TestsPassedStatusGenerator {
//  val previousStatusGenerator = Phase1TestsResultsReceivedStatusGenerator
//  val evaluationRepository: Phase1EvaluationMongoRepository = faststreamPhase1EvaluationRepository
  override val passedStatus = PHASE1_TESTS_PASSED

  def passmarkEvaluation(generatorConfig: CreateCandidateData, candidateResponse: CreateCandidateResponse): PassmarkEvaluation = {
    generatorConfig.phase1TestData.flatMap(_.passmarkEvaluation)
      .getOrElse {
        val schemeEvaluation = candidateResponse.schemePreferences.map(_.schemes.map(scheme =>
          SchemeEvaluationResult(scheme, EvaluationResults.Green.toString))
        ).getOrElse(Nil)
        val passmarkVersion = uuidFactory.generateUUID().toString
        val resultVersion = uuidFactory.generateUUID().toString
        PassmarkEvaluation(passmarkVersion, None, schemeEvaluation, resultVersion, None)
      }
  }

  def updateGenerationResponse(dgr: CreateCandidateResponse, pme: PassmarkEvaluation): CreateCandidateResponse = dgr.copy(
    phase1TestGroup = dgr.phase1TestGroup.map( p1 => p1.copy(schemeResult = Some(pme)))
  )
}

@Singleton
class Phase2TestsPassedStatusGenerator @Inject() (val previousStatusGenerator: Phase2TestsResultsReceivedStatusGenerator,
                                                  @Named("Phase2EvaluationRepository")
                                                  val evaluationRepository: OnlineTestEvaluationRepository,
                                                  val uuidFactory: UUIDFactory
                                                 ) extends TestsPassedStatusGenerator {
//  val previousStatusGenerator = Phase2TestsResultsReceivedStatusGenerator
//  val evaluationRepository: Phase2EvaluationMongoRepository = faststreamPhase2EvaluationRepository
  val passedStatus = PHASE2_TESTS_PASSED

  def passmarkEvaluation(generatorConfig: CreateCandidateData, candidateResponse: CreateCandidateResponse): PassmarkEvaluation =
    generatorConfig.phase2TestData.flatMap(_.passmarkEvaluation)
      .getOrElse {
        val schemeEvaluation = candidateResponse.schemePreferences.map(_.schemes.map(scheme => SchemeEvaluationResult(scheme,
          EvaluationResults.Green.toString
        ))).getOrElse(Nil)
        val passmarkVersion = uuidFactory.generateUUID().toString
        val resultVersion = uuidFactory.generateUUID().toString
        PassmarkEvaluation(passmarkVersion, candidateResponse.phase1TestGroup.flatMap(_.schemeResult.map(_.passmarkVersion)),
          schemeEvaluation, resultVersion, candidateResponse.phase1TestGroup.flatMap(_.schemeResult.map(_.resultVersion)))
      }

  def updateGenerationResponse(dgr: CreateCandidateResponse, pme: PassmarkEvaluation): CreateCandidateResponse = dgr.copy(
    phase2TestGroup = dgr.phase2TestGroup.map( p2 => p2.copy(schemeResult = Some(pme)))
  )
}

@Singleton
class Phase3TestsPassedStatusGenerator @Inject() (val previousStatusGenerator: Phase3TestsResultsReceivedStatusGenerator,
                                                  @Named("Phase3EvaluationRepository")
                                                  val evaluationRepository: OnlineTestEvaluationRepository,
                                                  val uuidFactory: UUIDFactory
                                                 ) extends TestsPassedStatusGenerator {
//  val previousStatusGenerator = Phase3TestsResultsReceivedStatusGenerator
//  val evaluationRepository: Phase3EvaluationMongoRepository = faststreamPhase3EvaluationRepository
//  val appRepository: GeneralApplicationMongoRepository = applicationRepository
  val passedStatus = PHASE3_TESTS_PASSED

  def passmarkEvaluation(generatorConfig: CreateCandidateData, candidateResponse: CreateCandidateResponse): PassmarkEvaluation =
    generatorConfig.phase3TestData.flatMap(_.passmarkEvaluation)
      .getOrElse {
        val schemeEvaluation = candidateResponse.schemePreferences.map(_.schemes.map(scheme => SchemeEvaluationResult(scheme,
          EvaluationResults.Green.toString
        ))).getOrElse(Nil)
        val passmarkVersion = uuidFactory.generateUUID().toString
        val resultVersion = uuidFactory.generateUUID().toString
        PassmarkEvaluation(passmarkVersion, candidateResponse.phase2TestGroup.flatMap(_.schemeResult.map(_.passmarkVersion)),
          schemeEvaluation, resultVersion, candidateResponse.phase2TestGroup.flatMap(_.schemeResult.map(_.resultVersion)))
      }

  def updateGenerationResponse(dgr: CreateCandidateResponse, pme: PassmarkEvaluation): CreateCandidateResponse = {
    dgr.copy(
      phase3TestGroup = dgr.phase3TestGroup.map( p3 => p3.copy(schemeResult = Some(pme)))
    )
  }
}
