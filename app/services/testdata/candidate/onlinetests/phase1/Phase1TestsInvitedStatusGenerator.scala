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

import java.util.UUID

import config.OnlineTestsGatewayConfig
import config.MicroserviceAppConfig.onlineTestsGatewayConfig
import model.exchange.testdata.CreateCandidateResponse.{ TestGroupResponse, TestResponse }
import model.persisted.{ CubiksTest, Phase1TestProfile }
import model.testdata.CreateCandidateData.CreateCandidateData
import org.joda.time.DateTime
import play.api.mvc.RequestHeader
import repositories._
import repositories.onlinetesting.Phase1TestRepository
import services.testdata.candidate.{ ConstructiveGenerator, SubmittedStatusGenerator }

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier

object Phase1TestsInvitedStatusGenerator extends Phase1TestsInvitedStatusGenerator {
  override val previousStatusGenerator = SubmittedStatusGenerator
  override val otRepository = phase1TestRepository
  override val gatewayConfig = onlineTestsGatewayConfig
}

trait Phase1TestsInvitedStatusGenerator extends ConstructiveGenerator {
  val otRepository: Phase1TestRepository
  val gatewayConfig: OnlineTestsGatewayConfig

  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader) = {

    val sjqTest = CubiksTest(
      cubiksUserId = scala.util.Random.nextInt(Int.MaxValue),
      token = UUID.randomUUID().toString,
      testUrl = generatorConfig.cubiksUrl,
      invitationDate = generatorConfig.phase1TestData.flatMap(_.start).getOrElse(DateTime.now()).withDurationAdded(86400000, -1),
      participantScheduleId = 149245,
      scheduleId = gatewayConfig.phase1Tests.scheduleIds("sjq"),
      usedForResults = true
    )

    val bqTest = CubiksTest(
      cubiksUserId = scala.util.Random.nextInt(Int.MaxValue),
      token = UUID.randomUUID().toString,
      testUrl = generatorConfig.cubiksUrl,
      invitationDate = generatorConfig.phase1TestData.flatMap(_.start).getOrElse(DateTime.now()).withDurationAdded(86400000, -1),
      participantScheduleId = 149245,
      scheduleId = gatewayConfig.phase1Tests.scheduleIds("bq"),
      usedForResults = true
    )

    val phase1TestProfile = Phase1TestProfile(
      expirationDate = generatorConfig.phase1TestData.flatMap(_.expiry).getOrElse(DateTime.now().plusDays(7)),
      tests = if (generatorConfig.assistanceDetails.setGis) List(sjqTest) else List(sjqTest, bqTest)
    )

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- otRepository.insertOrUpdateTestGroup(candidateInPreviousStatus.applicationId.get, phase1TestProfile)
    } yield {
      val sjq = phase1TestProfile.tests.find(t => t.cubiksUserId == sjqTest.cubiksUserId).get
      val bq = phase1TestProfile.tests.find(t => t.cubiksUserId == bqTest.cubiksUserId)

      candidateInPreviousStatus.copy(phase1TestGroup = Some(
        TestGroupResponse(
          List(TestResponse(sjq.cubiksUserId, "sjq", sjq.token, sjq.testUrl)) ++
          bq.map { b =>
            List(TestResponse(b.cubiksUserId, "bq", b.token, b.testUrl))
          }.getOrElse(Nil),
          None
        )
      ))
    }
  }
}
