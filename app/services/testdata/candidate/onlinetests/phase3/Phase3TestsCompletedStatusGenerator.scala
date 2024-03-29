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

package services.testdata.candidate.onlinetests.phase3

import common.FutureEx

import javax.inject.{Inject, Singleton}
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import play.api.mvc.RequestHeader
import repositories.onlinetesting.Phase3TestRepository
import services.onlinetesting.phase3.Phase3TestService
import services.testdata.candidate.ConstructiveGenerator
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

@Singleton
class Phase3TestsCompletedStatusGenerator @Inject() (val previousStatusGenerator: Phase3TestsStartedStatusGenerator,
                                                     otRepository: Phase3TestRepository,
                                                     otService: Phase3TestService
                                                    )(implicit ec: ExecutionContext) extends ConstructiveGenerator {

  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader, ec: ExecutionContext) = {
    for {
      candidate <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- FutureEx.traverseSerial(candidate.phase3TestGroup.get.tests.map(_.token))(token => otService.markAsCompleted(token))
    } yield candidate
  }
}
