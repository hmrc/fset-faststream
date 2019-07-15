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

package services.testdata.candidate.onlinetests.phase2

import common.FutureEx
import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.testdata.CreateCandidateData.CreateCandidateData
import org.joda.time.DateTime
import play.api.mvc.RequestHeader
import repositories._
import repositories.onlinetesting.Phase2TestRepository
import services.onlinetesting.phase1.Phase1TestService2
import services.onlinetesting.phase2.{Phase2TestService, Phase2TestService2}
import services.testdata.candidate.ConstructiveGenerator

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object Phase2TestsStartedStatusGenerator extends Phase2TestsStartedStatusGenerator {
  override val previousStatusGenerator = Phase2TestsInvitedStatusGenerator
  override val otRepository = phase2TestRepository
  override val otService = Phase2TestService2
}

trait Phase2TestsStartedStatusGenerator extends ConstructiveGenerator {
  val otRepository: Phase2TestRepository
  val otService: Phase2TestService2

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
      (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse] = {
    for {
      candidate <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- FutureEx.traverseSerial(candidate.phase2TestGroup.get.tests.map(_.orderId))(orderId =>
        otService.markAsStarted2(orderId, generatorConfig.phase2TestData.flatMap(_.start).getOrElse(DateTime.now))
      )
    } yield candidate
  }
}
