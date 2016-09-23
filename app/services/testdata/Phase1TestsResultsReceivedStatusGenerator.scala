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
import model.exchange.Phase1TestResultReady
import repositories._
import repositories.application.OnlineTestRepository
import services.onlinetesting.OnlineTestService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

object Phase1TestsResultsReceivedStatusGenerator extends Phase1TestsResultsReceivedStatusGenerator {
  override val previousStatusGenerator = Phase1TestsCompletedStatusGenerator
  override val otRepository = onlineTestRepository
  override val otService = OnlineTestService
}

trait Phase1TestsResultsReceivedStatusGenerator extends ConstructiveGenerator {
  val otRepository: OnlineTestRepository
  val otService: OnlineTestService

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {
    for {
      candidate <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- FutureEx.traverseSerial(candidate.phase1TestGroup.get.tests){test =>
        val id = test.cubiksUserId
        val result = Phase1TestResultReady(Some(id * 123), "Ready", Some(s"http://fakeurl.com/report$id"))
        otService.markAsReportReadyToDownload(id, result)}
    } yield candidate
  }
}
