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

import repositories._
import config.LaunchpadGatewayConfig
import config.MicroserviceAppConfig._
import model.ApplicationStatus._
import play.api.mvc.RequestHeader
import repositories.onlinetesting.{ Phase1TestRepository, Phase3TestRepository }
import _root_.services.onlinetesting.Phase3TestService
import model.OnlineTestCommands.OnlineTestApplication
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

object Phase3TestsInvitedStatusGenerator extends Phase3TestsInvitedStatusGenerator {
  override val previousStatusGenerator = Phase2TestsStartedStatusGenerator
  override val p3Repository = phase3TestRepository
  override val p3TestService = Phase3TestService
  override val gatewayConfig = launchpadGatewayConfig
}

trait Phase3TestsInvitedStatusGenerator extends ConstructiveGenerator {
  val p3Repository: Phase3TestRepository
  val p3TestService: Phase3TestService
  val gatewayConfig: LaunchpadGatewayConfig

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier, rh: RequestHeader) = {

    // TODO: This is "real" integration with launchpad, ultimately we should mock the invite
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      phase3TestApplication = OnlineTestApplication(
        candidateInPreviousStatus.applicationId.get,
        PHASE3_TESTS,
        candidateInPreviousStatus.userId,
        guaranteedInterview = false,
        needsAdjustments = false,
        candidateInPreviousStatus.preferredName,
        candidateInPreviousStatus.lastName,
        None
      )
      _ <- p3TestService.registerAndInviteForTestGroup(phase3TestApplication)
      testGroup <- p3Repository.getTestGroup(phase3TestApplication.applicationId)
    } yield {
      candidateInPreviousStatus.copy(
        phase3TestUrl = Some(testGroup.get.tests.find(_.usedForResults).get.testUrl)
      )
    }
  }
}
