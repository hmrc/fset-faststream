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

import config.MicroserviceAppConfig.{onlineTestsGatewayConfig, testIntegrationGatewayConfig}
import config.{OnlineTestsGatewayConfig, TestIntegrationGatewayConfig}
import model.exchange.testdata.CreateCandidateResponse.{TestGroupResponse, TestGroupResponse2, TestResponse, TestResponse2}
import model.persisted.{Phase1TestProfile2, PsiTest}
import model.testdata.CreateCandidateData.CreateCandidateData
import org.joda.time.DateTime
import play.api.mvc.RequestHeader
import repositories._
import repositories.onlinetesting.Phase1TestRepository2
import services.testdata.candidate.{ConstructiveGenerator, SubmittedStatusGenerator}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

object Phase1TestsInvitedStatusGenerator extends Phase1TestsInvitedStatusGenerator {
  override val previousStatusGenerator = SubmittedStatusGenerator
  override val otRepository = phase1TestRepository2
  override val gatewayConfig = onlineTestsGatewayConfig
  override val onlineTestGatewayConfig2 = testIntegrationGatewayConfig
}

trait Phase1TestsInvitedStatusGenerator extends ConstructiveGenerator {
  val otRepository: Phase1TestRepository2
  val gatewayConfig: OnlineTestsGatewayConfig
  val onlineTestGatewayConfig2: TestIntegrationGatewayConfig

  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader) = {

    val testsNames = if (generatorConfig.assistanceDetails.setGis) {
      onlineTestGatewayConfig2.phase1Tests.gis
    } else {
      onlineTestGatewayConfig2.phase1Tests.standard
    }

    val testsIndices = (1 to testsNames.size).toList
    val testsZipped = testsIndices zip testsNames
    val testsToGenerate = testsZipped.map(testPairWithName =>
      (testPairWithName._1, onlineTestGatewayConfig2.phase1Tests.inventoryIds(testPairWithName._2))).map(testPairWithInventoryId => {
      val orderId = java.util.UUID.randomUUID.toString
      val test = PsiTest(
        inventoryId = testPairWithInventoryId._2,
        orderId = orderId,
        usedForResults = true,
        testUrl = s"${generatorConfig.psiUrl}/PartnerRestService/test${testPairWithInventoryId._1}?key=$orderId",
        invitationDate = generatorConfig.phase1TestData.flatMap(_.start).getOrElse(DateTime.now()).
          withDurationAdded(86400000, -1),
        resultsReadyToDownload = false
      )
      test
    }
    )

    val phase1TestProfile2 = Phase1TestProfile2(
      expirationDate = generatorConfig.phase1TestData.flatMap(_.expiry).getOrElse(DateTime.now().plusDays(7)),
      tests = testsToGenerate
    )

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- otRepository.insertOrUpdateTestGroup(candidateInPreviousStatus.applicationId.get, phase1TestProfile2)
    } yield {
      // TODO: Set good values
      val testToGenerateZipped = testsIndices zip testsToGenerate
      val testResponses = testToGenerateZipped.map(test => TestResponse2(test._2.orderId, s"${test._1}", "TodoToken", test._2.testUrl))
      candidateInPreviousStatus.copy(phase1TestGroup = Some(
        TestGroupResponse2(
          testResponses,
          None
        )
      ))
    }
  }
}
