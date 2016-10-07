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

import java.util.UUID

import config.CubiksGatewayConfig
import connectors.testdata.ExchangeObjects.{ Phase1TestGroupResponse, Phase1TestResponse }
import model.OnlineTestCommands.{ Phase1Test, Phase1TestProfile }
import org.joda.time.DateTime
import play.api.mvc.RequestHeader
import repositories.onlinetesting.Phase1TestRepository
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

object Phase3TestsInvitedStatusGenerator extends Phase3TestsInvitedStatusGenerator {
  override val previousStatusGenerator = SubmittedStatusGenerator
  // override val otRepository = phase1TestRepository
  // override val gatewayConfig = cubiksGatewayConfig
}

trait Phase3TestsInvitedStatusGenerator extends ConstructiveGenerator {
  // val otRepository: Phase1TestRepository
  // val gatewayConfig: CubiksGatewayConfig

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier, rh: RequestHeader) = {

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- otRepository.insertOrUpdatePhase1TestGroup(candidateInPreviousStatus.applicationId.get, phase1TestProfile)
    } yield {

      candidateInPreviousStatus
    }
  }
}
