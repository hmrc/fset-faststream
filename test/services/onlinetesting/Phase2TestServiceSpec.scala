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

package services.onlinetesting

import akka.actor.ActorSystem
import config._
import connectors.{ CSREmailClient, CubiksGatewayClient }
import factories.{DateTimeFactory, UUIDFactory}
import model.ApplicationStatus
import model.OnlineTestCommands.OnlineTestApplication
import org.scalatest.mock.MockitoSugar
import org.scalatest.PrivateMethodTester
import org.scalatestplus.play.PlaySpec
import repositories.ContactDetailsRepository
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.Phase2TestRepository
import services.AuditService
import services.events.EventService

class Phase2TestServiceSpec extends PlaySpec with MockitoSugar with PrivateMethodTester {

  "some tests" should {
    "pass" in {
      pending
    }
  }

  "Filter candidates" should {
    "return the first candidate that needs adjustments" in new Phase2TestServiceFixture {
      val filterCandidatesTester = PrivateMethod[List[OnlineTestApplication]]('filterCandidates)
      val input = List(onlineTestApplication, onlineTestApplication, onlineTestApplication.copy(needsAdjustments = true),
        onlineTestApplication.copy(needsAdjustments = true)
      )

      val result = phase2TestService invokePrivate filterCandidatesTester(input)

      result.size mustBe 1
      result.head mustBe onlineTestApplication.copy(needsAdjustments = true)
    }
  }

  trait Phase2TestServiceFixture {

    val cubiksGtewayMock = mock[CubiksGatewayClient]
    val gatewayConfigMock =  CubiksGatewayConfig(
      "",
      Phase1TestsConfig(expiryTimeInDays = 7,
        scheduleIds = Map("sjq" -> 16196, "bq" -> 16194),
        List("sjq", "bq"),
        List("sjq")
      ),
      competenceAssessment = CubiksGatewayStandardAssessment(31, 32),
      situationalAssessment = CubiksGatewayStandardAssessment(41, 42),
      phase2Tests = Phase2TestsConfig(expiryTimeInDays = 7, scheduleName = "e-tray", scheduleId = 123, assessmentId = 158),
      reportConfig = ReportConfig(1, 2, "en-GB"),
      candidateAppUrl = "http://localhost:9284",
      emailDomain = "test.com"
    )

    val appRepositoryMock = mock[GeneralApplicationRepository]
    val cdRepositoryMock = mock[ContactDetailsRepository]
    val otRepositoryMock = mock[Phase2TestRepository]
    val cubiksGatewayClientMock = mock[CubiksGatewayClient]
    val emailClientMock = mock[CSREmailClient]
    var auditServiceMock = mock[AuditService]
    val tokenFactoryMock = mock[UUIDFactory]
    val onlineTestInvitationDateFactoryMock = mock[DateTimeFactory]
    val eventServiceMock = mock[EventService]


    val onlineTestApplication = OnlineTestApplication(applicationId = "appId",
      applicationStatus = ApplicationStatus.SUBMITTED,
      userId = "userId",
      guaranteedInterview = false,
      needsAdjustments = false,
      preferredName = "Optimus Prime",
      timeAdjustments = None
    )

    val phase2TestService = new Phase2TestService {
      val appRepository = appRepositoryMock
      val cdRepository = cdRepositoryMock
      val phase2TestRepo = otRepositoryMock
      val cubiksGatewayClient = cubiksGatewayClientMock
      val emailClient = emailClientMock
      val auditService = auditServiceMock
      val tokenFactory = tokenFactoryMock
      val dateTimeFactory = onlineTestInvitationDateFactoryMock
      val gatewayConfig = gatewayConfigMock
      val eventService = eventServiceMock
      val actor = ActorSystem()
    }
  }
}
