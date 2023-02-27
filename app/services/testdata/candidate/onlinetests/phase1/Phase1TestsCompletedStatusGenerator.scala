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

package services.testdata.candidate.onlinetests.phase1

import common.FutureEx
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import services.onlinetesting.phase1.Phase1TestService
import services.testdata.candidate.ConstructiveGenerator
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class Phase1TestsCompletedStatusGenerator @Inject() (val previousStatusGenerator: Phase1TestsStartedStatusGenerator,
                                                     otService: Phase1TestService
                                                    )(implicit ec: ExecutionContext) extends ConstructiveGenerator {

  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader, ec: ExecutionContext) = {
    for {
      candidate <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- FutureEx.traverseSerial(candidate.phase1TestGroup.get.tests.map(_.orderId))(otService.markAsCompleted(_))
    } yield candidate
  }
}
