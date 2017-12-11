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

package services.testdata.candidate.fsb

import model.EvaluationResults.Green
import model.Exceptions.{ InvalidApplicationStatusAndProgressStatusException, SchemeNotFoundException }
import model.command.testdata.CreateCandidateRequest.FsbTestGroupDataRequest
import model.exchange.testdata.CreateCandidateResponse.{ CreateCandidateResponse, FsbTestGroupResponse }
import model.persisted.SchemeEvaluationResult
import model.testdata.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import services.application.FsbService
import services.testdata.candidate.{ BaseGenerator, ConstructiveGenerator }
import services.testdata.faker.DataFaker

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object FsbResultEnteredStatusGenerator extends FsbResultEnteredStatusGenerator {
  override val previousStatusGenerator: BaseGenerator = FsbAllocationConfirmedStatusGenerator
  override val fsbTestGroupService = FsbService
  override val dataFaker: DataFaker = DataFaker
}

trait FsbResultEnteredStatusGenerator extends ConstructiveGenerator {
  val fsbTestGroupService: FsbService
  val dataFaker: DataFaker

  def generate(generationId: Int, createCandidateDataIn: CreateCandidateData)
              (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse] = {

    val createCandidateData = if (createCandidateDataIn.fsbTestGroupData.isEmpty) {
      val schemeTypes = createCandidateDataIn.schemeTypes.getOrElse(dataFaker.Random.schemeTypes.map(_.id))
      val schemeResults = schemeTypes.map { schemeId =>
        SchemeEvaluationResult(schemeId, Green.toString) // fake data
      }
      val res = FsbTestGroupDataRequest(schemeResults)
      createCandidateDataIn.copy(fsbTestGroupData = Some(res), schemeTypes = Some(schemeTypes))
    } else {
      createCandidateDataIn
    }

    if (createCandidateData.fsbTestGroupData.isDefined) {
      for {
        candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, createCandidateData)
        applicationId = candidateInPreviousStatus.applicationId.get
      } yield {
        createCandidateData.fsbTestGroupData.get.results.map { result =>
          // service updates progress status when saving result
          if (createCandidateData.schemeTypes.get.contains(result.schemeId)) {
            fsbTestGroupService.saveResult(applicationId, result)
          } else {
            throw SchemeNotFoundException(s"Candidate scheme preference does not have ${result.schemeId} ")
          }
        }
        val fsbTestGroupResponse = createCandidateData.fsbTestGroupData.map(data => FsbTestGroupResponse(data.results))
        candidateInPreviousStatus.copy(fsbTestGroup = fsbTestGroupResponse)
      }
    } else {
      throw InvalidApplicationStatusAndProgressStatusException("ProgressStatuses.FSB_RESULT_ENTERED status must have fsbTestGroupData")
    }
  }

}
