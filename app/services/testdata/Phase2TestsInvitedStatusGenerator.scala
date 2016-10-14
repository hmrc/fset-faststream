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
import connectors.testdata.ExchangeObjects.{ CubiksTestResponse, TestGroupResponse }
import model.persisted.{ CubiksTest, Phase2TestGroup }
import org.joda.time.DateTime
import repositories._
import repositories.onlinetesting.Phase2TestRepository
import uk.gov.hmrc.play.http.HeaderCarrier
import config.MicroserviceAppConfig.cubiksGatewayConfig
import play.api.mvc.RequestHeader

import scala.concurrent.ExecutionContext.Implicits.global

object Phase2TestsInvitedStatusGenerator extends Phase2TestsInvitedStatusGenerator {
  override val previousStatusGenerator = Phase1TestsResultsReceivedStatusGenerator
  override val otRepository = phase2TestRepository
  override val gatewayConfig = cubiksGatewayConfig
}

trait Phase2TestsInvitedStatusGenerator extends ConstructiveGenerator {
  val otRepository: Phase2TestRepository
  val gatewayConfig: CubiksGatewayConfig

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier, rh: RequestHeader) = {

    val etray = CubiksTest(
      cubiksUserId = scala.util.Random.nextInt(Int.MaxValue),
      token = UUID.randomUUID().toString,
      testUrl = generatorConfig.cubiksUrl,
      invitationDate = generatorConfig.phase1StartTime.getOrElse(DateTime.now()).withDurationAdded(86400000, -1),
      participantScheduleId = 243357,
      scheduleId = gatewayConfig.phase2Tests.schedules("daro").scheduleId,
      usedForResults = true
    )

    val phase2TestGroup = Phase2TestGroup(
      expirationDate = generatorConfig.phase1ExpiryTime.getOrElse(DateTime.now().plusDays(7)),
      tests = List(etray)
    )

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- otRepository.insertOrUpdateTestGroup(candidateInPreviousStatus.applicationId.get, phase2TestGroup)
    } yield {
      val etray = phase2TestGroup.tests.head

      candidateInPreviousStatus.copy(phase1TestGroup = Some(
        TestGroupResponse(
          List(CubiksTestResponse(etray.cubiksUserId, etray.token, etray.testUrl))
        )
      ))
    }
  }
}
