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

import java.util.UUID

import config.CubiksGatewayConfig
import config.MicroserviceAppConfig.cubiksGatewayConfig
import model.Adjustments
import model.exchange.testdata.CreateCandidateResponse.{ TestGroupResponse, TestResponse }
import model.persisted.{ CubiksTest, Phase2TestGroup }
import model.testdata.CreateCandidateData.CreateCandidateData
import org.joda.time.DateTime
import play.api.mvc.RequestHeader
import repositories._
import repositories.onlinetesting.Phase2TestRepository
import services.testdata.candidate.ConstructiveGenerator
import services.testdata.faker.DataFaker._
import services.testdata.candidate.onlinetests.Phase1TestsPassedStatusGenerator

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier

object Phase2TestsInvitedStatusGenerator extends Phase2TestsInvitedStatusGenerator {
  override val previousStatusGenerator = Phase1TestsPassedStatusGenerator
  override val otRepository = phase2TestRepository
  override val gatewayConfig = cubiksGatewayConfig
}

trait Phase2TestsInvitedStatusGenerator extends ConstructiveGenerator {
  val otRepository: Phase2TestRepository
  val gatewayConfig: CubiksGatewayConfig

  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader) = {

    val etray = CubiksTest(
      cubiksUserId = scala.util.Random.nextInt(Int.MaxValue),
      token = UUID.randomUUID().toString,
      testUrl = generatorConfig.cubiksUrl,
      invitationDate = generatorConfig.phase2TestData.flatMap(_.start).getOrElse(DateTime.now()).plusDays(-1),
      participantScheduleId = 243357,
      scheduleId = gatewayConfig.phase2Tests.schedules("daro").scheduleId,
      usedForResults = true,
      invigilatedAccessCode = generatorConfig.adjustmentInformation.flatMap { adjustments =>
        if (isInvigilatedETray(adjustments)) {
          Some(Random.accessCode)
        } else {
          None
        }
      }
    )

    val phase2TestGroup = Phase2TestGroup(
      expirationDate = generatorConfig.phase2TestData.flatMap(_.expiry).getOrElse(DateTime.now().plusDays(7)),
      tests = List(etray)
    )

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- otRepository.insertOrUpdateTestGroup(candidateInPreviousStatus.applicationId.get, phase2TestGroup)
    } yield {
      val etray = phase2TestGroup.tests.head

      candidateInPreviousStatus.copy(
        phase2TestGroup = Some(TestGroupResponse(List(TestResponse(etray.cubiksUserId, "etray", etray.token, etray.testUrl)), None)),
        accessCode = etray.invigilatedAccessCode
      )
    }
  }

  def isInvigilatedETray(adjustments: Adjustments) = {
    val isConfirmed = adjustments.adjustmentsConfirmed.contains(true)
    val hasInvigilatedETray = adjustments.etray.exists(_.invigilatedInfo.isDefined)
    isConfirmed && hasInvigilatedETray
  }
}
