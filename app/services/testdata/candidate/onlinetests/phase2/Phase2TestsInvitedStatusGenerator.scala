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

import config.{OnlineTestsGatewayConfig, TestIntegrationGatewayConfig}
import config.MicroserviceAppConfig.{onlineTestsGatewayConfig, testIntegrationGatewayConfig}
import model.Adjustments
import model.exchange.testdata.CreateCandidateResponse.{TestGroupResponse, TestGroupResponse2, TestResponse, TestResponse2}
import model.persisted.{CubiksTest, Phase2TestGroup, Phase2TestGroup2, PsiTest}
import model.testdata.CreateCandidateData.CreateCandidateData
import org.joda.time.DateTime
import play.api.mvc.RequestHeader
import repositories._
import repositories.onlinetesting.{Phase2TestRepository, Phase2TestRepository2}
import services.testdata.candidate.ConstructiveGenerator
import services.testdata.faker.DataFaker._
import services.testdata.candidate.onlinetests.Phase1TestsPassedStatusGenerator

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier

object Phase2TestsInvitedStatusGenerator extends Phase2TestsInvitedStatusGenerator {
  override val previousStatusGenerator = Phase1TestsPassedStatusGenerator
  override val otRepository = phase2TestRepository2
  override val onlineTestGatewayConfig2 = testIntegrationGatewayConfig
}

trait Phase2TestsInvitedStatusGenerator extends ConstructiveGenerator {
  val otRepository: Phase2TestRepository2
  val onlineTestGatewayConfig2: TestIntegrationGatewayConfig

  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader) = {


    val psiTests = onlineTestGatewayConfig2.phase2Tests.tests.map { testName =>
      (testName, onlineTestGatewayConfig2.phase2Tests.inventoryIds(testName))
    }.map { case (testName, inventoryId) => {
      val orderId = java.util.UUID.randomUUID.toString
      // TODO: Check values
      val test = PsiTest(
        inventoryId = inventoryId,
        orderId = orderId,
        usedForResults = true,
        // TODO: right URL?
        testUrl = s"${generatorConfig.psiUrl}/PartnerRestService/${testName}?key=$orderId",
        invitationDate = generatorConfig.phase2TestData.flatMap(_.start).getOrElse(DateTime.now()).plusDays(-1),
        resultsReadyToDownload = false,
        invigilatedAccessCode = generatorConfig.adjustmentInformation.flatMap { adjustments =>
          if (isInvigilatedETray(adjustments)) {
            Some(Random.accessCode)
          } else {
            None
          }
        }
      )
      test
    }
    }

    val phase2TestGroup = Phase2TestGroup2(
      expirationDate = generatorConfig.phase2TestData.flatMap(_.expiry).getOrElse(DateTime.now().plusDays(7)),
      tests = psiTests
    )

    // TODO: Invigilated? Is still relevant?
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- otRepository.insertOrUpdateTestGroup(candidateInPreviousStatus.applicationId.get, phase2TestGroup)
    } yield {
      val onePsiTest = psiTests.head
      val testResponses = psiTests.map(test => TestResponse2(test.inventoryId, test.orderId, test.testUrl))
      candidateInPreviousStatus.copy(phase2TestGroup = Some(
        TestGroupResponse2(
          testResponses,
          None
        )
      ), accessCode = onePsiTest.invigilatedAccessCode)
    }
  }

  def isInvigilatedETray(adjustments: Adjustments) = {
    val isConfirmed = adjustments.adjustmentsConfirmed.contains(true)
    // TODO: etray?
    val hasInvigilatedETray = adjustments.etray.exists(_.invigilatedInfo.isDefined)
    isConfirmed && hasInvigilatedETray
  }
}
