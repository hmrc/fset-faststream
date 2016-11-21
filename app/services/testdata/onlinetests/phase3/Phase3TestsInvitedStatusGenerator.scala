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

package services.testdata.onlinetests.phase3

import java.util.UUID

import _root_.services.onlinetesting.Phase3TestService
import _root_.services.testdata.ConstructiveGenerator
import _root_.services.testdata.onlinetests.phase2.Phase2TestsResultsReceivedStatusGenerator
import config.LaunchpadGatewayConfig
import config.MicroserviceAppConfig._
import connectors.testdata.ExchangeObjects.{ DataGenerationResponse, TestGroupResponse, TestResponse }
import model.ApplicationStatus._
import model.OnlineTestCommands.OnlineTestApplication
import model.command.testdata.GeneratorConfig
import model.persisted.phase3tests.{ LaunchpadTest, LaunchpadTestCallbacks, Phase3TestGroup }
import org.joda.time.DateTime
import play.api.mvc.RequestHeader
import repositories._
import repositories.onlinetesting.Phase3TestRepository
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Phase3TestsInvitedStatusGenerator extends Phase3TestsInvitedStatusGenerator {
  override val previousStatusGenerator = Phase2TestsResultsReceivedStatusGenerator
  override val p3Repository = phase3TestRepository
  override val p3TestService = Phase3TestService
  override val gatewayConfig = launchpadGatewayConfig
}

trait Phase3TestsInvitedStatusGenerator extends ConstructiveGenerator {
  val p3Repository: Phase3TestRepository
  val p3TestService: Phase3TestService
  val gatewayConfig: LaunchpadGatewayConfig

  def generate(generationId: Int, generatorConfig: GeneratorConfig)
              (implicit hc: HeaderCarrier, rh: RequestHeader): Future[DataGenerationResponse] = {

    val launchpad = LaunchpadTest(
      interviewId = 12345,
      usedForResults = true,
      testUrl = "http:///www.fake.url",
      token = UUID.randomUUID().toString,
      candidateId = UUID.randomUUID().toString,
      customCandidateId = "FSCND-123",
      invitationDate = generatorConfig.phase3TestData.flatMap(_.start)
        .getOrElse(DateTime.now()),
      startedDateTime = generatorConfig.phase3TestData.flatMap(_.start),
      completedDateTime = generatorConfig.phase3TestData.flatMap(_.completion),
      callbacks = LaunchpadTestCallbacks()
    )

    val phase3TestGroup = Phase3TestGroup(
      expirationDate = generatorConfig.phase3TestData.flatMap(_.expiry).getOrElse(DateTime.now().plusDays(7)),
      tests = List(launchpad)
    )
    
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      phase3TestApplication = OnlineTestApplication(
        candidateInPreviousStatus.applicationId.get,
        PHASE3_TESTS,
        candidateInPreviousStatus.userId,
        guaranteedInterview = false,
        needsOnlineAdjustments = false,
        needsAtVenueAdjustments = false,
        generatorConfig.personalData.getPreferredName,
        candidateInPreviousStatus.lastName,
        None,
        None
      )
      _ <- p3Repository.insertOrUpdateTestGroup(candidateInPreviousStatus.applicationId.get, phase3TestGroup)
      testGroup <- p3Repository.getTestGroup(phase3TestApplication.applicationId)
    } yield {
      val phase3TestGroupResponse = TestResponse(testId = launchpad.interviewId, token = launchpad.token,
        testUrl = testGroup.get.tests.find(_.usedForResults).get.testUrl)

      candidateInPreviousStatus.copy(
        phase3TestGroup = Some(TestGroupResponse(List(phase3TestGroupResponse)))
      )
    }
  }
}
