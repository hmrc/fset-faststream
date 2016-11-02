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
import connectors.testdata.ExchangeObjects.{ TestGroupResponse, CubiksTestResponse }
import model.persisted.{ CubiksTest, Phase1TestProfile }
import model.command.testdata.GeneratorConfig
import org.joda.time.DateTime
import repositories._
import repositories.onlinetesting.Phase1TestRepository
import uk.gov.hmrc.play.http.HeaderCarrier
import config.MicroserviceAppConfig.cubiksGatewayConfig
import play.api.mvc.RequestHeader
import mode.command.testdata.GeneratorConfig

import scala.concurrent.ExecutionContext.Implicits.global

object Phase1TestsInvitedStatusGenerator extends Phase1TestsInvitedStatusGenerator {
  override val previousStatusGenerator = SubmittedStatusGenerator
  override val otRepository = phase1TestRepository
  override val gatewayConfig = cubiksGatewayConfig
}

trait Phase1TestsInvitedStatusGenerator extends ConstructiveGenerator {
  val otRepository: Phase1TestRepository
  val gatewayConfig: CubiksGatewayConfig

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier, rh: RequestHeader) = {

    val sjqTest = CubiksTest(
      cubiksUserId = scala.util.Random.nextInt(Int.MaxValue),
      token = UUID.randomUUID().toString,
      testUrl = generatorConfig.cubiksUrl,
      invitationDate = generatorConfig.phase1StartTime.getOrElse(DateTime.now()).withDurationAdded(86400000, -1),
      participantScheduleId = 149245,
      scheduleId = gatewayConfig.phase1Tests.scheduleIds("sjq"),
      usedForResults = true
    )

    val bqTest = CubiksTest(
      cubiksUserId = scala.util.Random.nextInt(Int.MaxValue),
      token = UUID.randomUUID().toString,
      testUrl = generatorConfig.cubiksUrl,
      invitationDate = generatorConfig.phase1StartTime.getOrElse(DateTime.now()).withDurationAdded(86400000, -1),
      participantScheduleId = 149245,
      scheduleId = gatewayConfig.phase1Tests.scheduleIds("bq"),
      usedForResults = true
    )

    val phase1TestProfile = Phase1TestProfile(
      expirationDate = generatorConfig.phase1ExpiryTime.getOrElse(DateTime.now().plusDays(7)),
      tests = if (generatorConfig.setGis) List(sjqTest) else List(sjqTest, bqTest)
    )

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- otRepository.insertOrUpdateTestGroup(candidateInPreviousStatus.applicationId.get, phase1TestProfile)
    } yield {
      val sjq = phase1TestProfile.tests.find(t => t.cubiksUserId == sjqTest.cubiksUserId).get
      val bq = phase1TestProfile.tests.find(t => t.cubiksUserId == bqTest.cubiksUserId)

      candidateInPreviousStatus.copy(phase1TestGroup = Some(
        TestGroupResponse(
          List(CubiksTestResponse(sjq.cubiksUserId, sjq.token, sjq.testUrl)) ++
          bq.map { b =>
            List(CubiksTestResponse(b.cubiksUserId, b.token, b.testUrl))
          }.getOrElse(Nil)
        )
      ))
    }
  }
}
