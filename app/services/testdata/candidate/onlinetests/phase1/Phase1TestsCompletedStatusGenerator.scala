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

package services.testdata.candidate.onlinetests.phase1

import common.FutureEx
import model.testdata.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import repositories._
import repositories.onlinetesting.{Phase1TestRepository, Phase1TestRepository2}
import services.onlinetesting.phase1.{Phase1TestService, Phase1TestService2}
import services.testdata.candidate.ConstructiveGenerator

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier

object Phase1TestsCompletedStatusGenerator extends Phase1TestsCompletedStatusGenerator {
  override val previousStatusGenerator = Phase1TestsStartedStatusGenerator
  override val otRepository = phase1TestRepository2
  override val otService = Phase1TestService2
}

trait Phase1TestsCompletedStatusGenerator extends ConstructiveGenerator {
  val otRepository: Phase1TestRepository2
  val otService: Phase1TestService2

  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader) = {
    for {
      candidate <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- FutureEx.traverseSerial(candidate.phase1TestGroup.get.tests.map(_.testId))(id => otService.markAsCompleted2(id))
    } yield candidate
  }
}
