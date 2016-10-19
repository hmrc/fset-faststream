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

import common.FutureEx
import play.api.mvc.RequestHeader
import repositories._
import repositories.onlinetesting.Phase2TestRepository
import services.onlinetesting.Phase2TestService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

object Phase2TestsCompletedStatusGenerator extends Phase2TestsCompletedStatusGenerator {
  override val previousStatusGenerator = Phase2TestsStartedStatusGenerator
  override val otRepository = phase2TestRepository
  override val otService = Phase2TestService
}

trait Phase2TestsCompletedStatusGenerator extends ConstructiveGenerator {
  val otRepository: Phase2TestRepository
  val otService: Phase2TestService

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier, rh: RequestHeader) = {
    for {
      candidate <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- FutureEx.traverseSerial(candidate.phase2TestGroup.get.tests.map(_.cubiksUserId))(id => otService.markAsCompleted(id))
    } yield candidate
  }
}
