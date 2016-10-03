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

import connectors.testdata.ExchangeObjects.DataGenerationResponse
import model.ApplicationStatuses
import model.Commands.ApplicationAssessment
import repositories._
import repositories.onlinetesting.Phase1TestRepository
import services.testdata.faker.DataFaker.Random
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AllocationStatusGenerator extends AllocationStatusGenerator {
  override val previousStatusGenerator = AwaitingAllocationStatusGenerator
  override val otRepository = phase1TestRepository
  override val aaRepository = applicationAssessmentRepository

  val SlotFindingLockObj = new Object()
}

trait AllocationStatusGenerator extends ConstructiveGenerator {
  val otRepository: Phase1TestRepository
  val aaRepository: ApplicationAssessmentRepository

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier): Future[DataGenerationResponse] = {

    def getApplicationAssessment(candidate: DataGenerationResponse) = {
      for {
        availableAssessment <- Random.availableAssessmentVenueAndDate
      } yield {
        ApplicationAssessment(
          candidate.applicationId.get,
          availableAssessment.venue.venueName,
          availableAssessment.date,
          availableAssessment.session,
          generationId,
          confirmed = generatorConfig.confirmedAllocation
        )
      }
    }

    val newStatus = if (generatorConfig.confirmedAllocation) {
      ApplicationStatuses.AllocationConfirmed
    } else {
      ApplicationStatuses.AllocationUnconfirmed
    }

    AllocationStatusGenerator.SlotFindingLockObj.synchronized {
      for {
        candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
        randomAssessment <- getApplicationAssessment(candidateInPreviousStatus)
        _ <- aaRepository.create(List(randomAssessment))
        //TODO FAST STREAM FIX ME
        //_ <- otRepository.saveCandidateAllocationStatus(candidateInPreviousStatus.applicationId.get, newStatus, None)
      } yield {
        candidateInPreviousStatus.copy(
          applicationAssessment = Some(randomAssessment)
        )
      }
    }
  }
}
