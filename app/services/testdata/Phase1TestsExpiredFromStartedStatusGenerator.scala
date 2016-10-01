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

import model.PersistedObjects.ExpiringOnlineTest
import repositories._
import repositories.onlinetesting.Phase1TestRepository
import services.onlinetesting.{OnlineTestExpiryService, OnlineTestService}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

object Phase1TestsExpiredFromStartedStatusGenerator extends Phase1TestsExpiredFromStartedStatusGenerator {
  override val previousStatusGenerator = Phase1TestsStartedStatusGenerator
  override val otRepository = phase1TestRepository
  override val otService = OnlineTestService
  override val oteService = OnlineTestExpiryService
}

trait Phase1TestsExpiredFromStartedStatusGenerator extends ConstructiveGenerator {
  val otRepository: Phase1TestRepository
  val otService: OnlineTestService
  val oteService: OnlineTestExpiryService

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- oteService.commitExpiredProgressStatus(ExpiringOnlineTest(
        candidateInPreviousStatus.applicationId.get, candidateInPreviousStatus.userId, candidateInPreviousStatus.preferredName
      ))
    } yield {
      candidateInPreviousStatus
    }

  }
}
