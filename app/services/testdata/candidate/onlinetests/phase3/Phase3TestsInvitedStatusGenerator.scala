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

package services.testdata.candidate.onlinetests.phase3

import java.util.UUID

import _root_.services.onlinetesting.phase3.Phase3TestService
import _root_.services.testdata.candidate.ConstructiveGenerator
import _root_.services.testdata.candidate.onlinetests.Phase2TestsPassedStatusGenerator
import config.LaunchpadGatewayConfig
import config.MicroserviceAppConfig._
import model.ApplicationStatus._
import model.OnlineTestCommands.OnlineTestApplication
import model.exchange.testdata.CreateCandidateResponse.{CreateCandidateResponse, TestGroupResponse, TestResponse}
import model.persisted.phase3tests.{LaunchpadTest, LaunchpadTestCallbacks, Phase3TestGroup}
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import org.joda.time.DateTime
import play.api.mvc.RequestHeader
import repositories._
import repositories.onlinetesting.Phase3TestRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Phase3TestsInvitedStatusGenerator extends Phase3TestsInvitedStatusGenerator {
  override val previousStatusGenerator = Phase2TestsPassedStatusGenerator
  override val p3Repository = phase3TestRepository
  override val p3TestService = Phase3TestService
  override val gatewayConfig = launchpadGatewayConfig
}

trait Phase3TestsInvitedStatusGenerator extends ConstructiveGenerator {
  val p3Repository: Phase3TestRepository
  val p3TestService: Phase3TestService
  val gatewayConfig: LaunchpadGatewayConfig

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse] = {
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
        // TODO: This is not set in code, so it will always generate a random useless value, anyway it is not used yet
        candidateInPreviousStatus.testAccountId.getOrElse("TODOTestAccountId"),
        guaranteedInterview = false,
        needsOnlineAdjustments = false, needsAtVenueAdjustments = false,
        preferredName = generatorConfig.personalData.getPreferredName,
        lastName = candidateInPreviousStatus.lastName,
        eTrayAdjustments = None,
        videoInterviewAdjustments = None
      )
      _ <- p3Repository.insertOrUpdateTestGroup(candidateInPreviousStatus.applicationId.get, phase3TestGroup)
      testGroup <- p3Repository.getTestGroup(phase3TestApplication.applicationId)
    } yield {
      val phase3TestGroupResponse = TestResponse(testId = launchpad.interviewId, testType = "video", token = launchpad.token,
        testUrl = testGroup.get.tests.find(_.usedForResults).get.testUrl)
      candidateInPreviousStatus.copy(
        phase3TestGroup = Some(TestGroupResponse(List(phase3TestGroupResponse), None))
      )
    }
  }
}
