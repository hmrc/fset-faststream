/*
 * Copyright 2023 HM Revenue & Customs
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

import config.{MicroserviceAppConfig, OnlineTestsGatewayConfig}

import javax.inject.{Inject, Singleton}
import model.Adjustments
import model.exchange.testdata.CreateCandidateResponse.{TestGroupResponse2, TestResponse2}
import model.persisted.{Phase2TestGroup, PsiTest}
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import org.joda.time.DateTime
import play.api.mvc.RequestHeader
import repositories.onlinetesting.Phase2TestRepository
import services.testdata.candidate.ConstructiveGenerator
import services.testdata.candidate.onlinetests.Phase1TestsPassedStatusGenerator
import services.testdata.faker.DataFaker
import uk.gov.hmrc.http.HeaderCarrier

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext

@Singleton
class Phase2TestsInvitedStatusGenerator @Inject() (val previousStatusGenerator: Phase1TestsPassedStatusGenerator,
                                                   otRepository: Phase2TestRepository,
                                                   appConfig: MicroserviceAppConfig,
                                                   dataFaker: DataFaker
                                                  )(implicit ec: ExecutionContext) extends ConstructiveGenerator {

  val onlineTestsGatewayConfig: OnlineTestsGatewayConfig = appConfig.onlineTestsGatewayConfig

  //scalastyle:off method.length
  def generate(generationId: Int, generatorConfig: CreateCandidateData)(implicit hc: HeaderCarrier, rh: RequestHeader, ec: ExecutionContext) = {

    val psiTests = onlineTestsGatewayConfig.phase2Tests.standard.map { testName =>
      (
        testName,
        onlineTestsGatewayConfig.phase2Tests.tests(testName).inventoryId,
        onlineTestsGatewayConfig.phase2Tests.tests(testName).assessmentId,
        onlineTestsGatewayConfig.phase2Tests.tests(testName).reportId,
        onlineTestsGatewayConfig.phase2Tests.tests(testName).normId
      )
    }.map { case (testName, inventoryId, assessmentId, reportId, normId) =>
      val orderId = java.util.UUID.randomUUID.toString
      val test = PsiTest(
        inventoryId = inventoryId,
        orderId = orderId,
        usedForResults = true,
        testUrl = s"${generatorConfig.psiUrl}/PartnerRestService/$testName?key=$orderId",
        invitationDate = generatorConfig.phase2TestData.flatMap(_.start).getOrElse(OffsetDateTime.now().plusDays(-1)),
        invigilatedAccessCode = generatorConfig.adjustmentInformation.flatMap { adjustments =>
          if (isInvigilated(Adjustments(adjustments))) {
            Some(dataFaker.accessCode)
          } else {
            None
          }
        },
        assessmentId = assessmentId,
        reportId = reportId,
        normId = normId
      )
      test
    }

    val phase2TestGroup = Phase2TestGroup(
      expirationDate = generatorConfig.phase2TestData.flatMap(_.expiry).getOrElse(OffsetDateTime.now().plusDays(7)),
      tests = psiTests
    )

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- otRepository.insertOrUpdateTestGroup(candidateInPreviousStatus.applicationId.get, phase2TestGroup)
    } yield {
      val onePsiTest = psiTests.head
      val testResponses = psiTests.map(test => TestResponse2(test.inventoryId, test.orderId, test.testUrl))
      candidateInPreviousStatus.copy(
        phase2TestGroup = Some(TestGroupResponse2(testResponses, None)),
        accessCode = onePsiTest.invigilatedAccessCode
      )
    }
  }
  //scalastyle:on

  private def isInvigilated(adjustments: Adjustments) = {
    val isConfirmed = adjustments.adjustmentsConfirmed.contains(true)
    val hasInvigilated = adjustments.etray.exists(_.invigilatedInfo.isDefined)
    isConfirmed && hasInvigilated
  }
}
