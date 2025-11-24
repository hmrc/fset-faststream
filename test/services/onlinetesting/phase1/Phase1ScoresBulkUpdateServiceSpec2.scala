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

package services.onlinetesting.phase1

import model.Phase1TestExamples.{firstPsiTest, secondPsiTest}
import model.command.{Phase1ScoreUpdateRequest, Phase1ScoreUpdateResponse}
import model.persisted.Phase1TestProfile
import model.{ApplicationRoute, ApplicationStatus, Candidate}
import org.mockito.ArgumentMatchers.{eq as eqTo, *}
import org.mockito.Mockito.*
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.Phase1TestRepository
import services.BaseServiceSpec
import testkit.MockitoImplicits.*

import java.time.{LocalDate, OffsetDateTime}
import scala.concurrent.ExecutionContext.Implicits.global

class Phase1ScoresBulkUpdateServiceSpec2 extends BaseServiceSpec {

  "Bulk update of candidates scores" should {
    "update the scores successfully" in new TestFixture {
      when(mockApplicationRepository.find(any[String])).thenReturnAsync(Some(candidate))

      val phase1TestProfile = Phase1TestProfile(expirationDate = OffsetDateTime.now, tests = List(firstPsiTest, secondPsiTest))
      when(mockPhase1TestRepository.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))

      when(mockPhase1TestRepository.updateTestGroup(any[String], any[Phase1TestProfile])).thenReturnAsync()

      val updates = Seq(
        Phase1ScoreUpdateRequest(
          applicationId = "appId1",
          inventoryId = "inventoryId1",
          orderId = "orderId1",
          tScore = 66.0,
          rawScore = 40.0
        )
      )

      val result = service.updatePhase1Scores(updates).futureValue
      val expected = Seq(
        Phase1ScoreUpdateResponse(
          "appId1", "inventoryId1", "orderId1", tScore = 66.0, rawScore = 40.0, status = "Successfully updated"
        )
      )

      result mustBe expected
    }

    "handle an applicationId that can't be found" in new TestFixture {
      when(mockApplicationRepository.find(any[String])).thenReturnAsync(None)

      when(mockPhase1TestRepository.getTestGroup(any[String])).thenReturnAsync(None)

      val updates = Seq(
        Phase1ScoreUpdateRequest(
          applicationId = "appId1",
          inventoryId = "inventoryId1",
          orderId = "orderId1",
          tScore = 66.0,
          rawScore = 40.0
        )
      )

      val result = service.updatePhase1Scores(updates).futureValue
      val expected = Seq(
        Phase1ScoreUpdateResponse(
          "appId1", "inventoryId1", "orderId1", tScore = 66.0, rawScore = 40.0, status = "No application found for appId1"
        )
      )

      result mustBe expected
    }

    "handle an inventoryId that can't be found" in new TestFixture {
      when(mockApplicationRepository.find(any[String])).thenReturnAsync(Some(candidate))

      val phase1TestProfile = Phase1TestProfile(expirationDate = OffsetDateTime.now, tests = List(firstPsiTest.copy(inventoryId = "boom"), secondPsiTest))
      when(mockPhase1TestRepository.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))

      when(mockPhase1TestRepository.updateTestGroup(any[String], any[Phase1TestProfile])).thenReturnAsync()

      val updates = Seq(
        Phase1ScoreUpdateRequest(
          applicationId = "appId1",
          inventoryId = "inventoryId1",
          orderId = "orderId1",
          tScore = 66.0,
          rawScore = 40.0
        )
      )

      val result = service.updatePhase1Scores(updates).futureValue
      val expected = Seq(
        Phase1ScoreUpdateResponse(
          "appId1", "inventoryId1", "orderId1", tScore = 66.0, rawScore = 40.0,
          status = "No test found for inventoryId=inventoryId1 and orderId=orderId1 or missing test result"
        )
      )

      result mustBe expected
    }

    "handle an orderId that can't be found" in new TestFixture {
      when(mockApplicationRepository.find(any[String])).thenReturnAsync(Some(candidate))

      val phase1TestProfile = Phase1TestProfile(expirationDate = OffsetDateTime.now, tests = List(firstPsiTest.copy(orderId = "boom"), secondPsiTest))
      when(mockPhase1TestRepository.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))

      when(mockPhase1TestRepository.updateTestGroup(any[String], any[Phase1TestProfile])).thenReturnAsync()

      val updates = Seq(
        Phase1ScoreUpdateRequest(
          applicationId = "appId1",
          inventoryId = "inventoryId1",
          orderId = "orderId1",
          tScore = 66.0,
          rawScore = 40.0
        )
      )

      val result = service.updatePhase1Scores(updates).futureValue
      val expected = Seq(
        Phase1ScoreUpdateResponse(
          "appId1", "inventoryId1", "orderId1", tScore = 66.0, rawScore = 40.0,
          status = "No test found for inventoryId=inventoryId1 and orderId=orderId1 or missing test result"
        )
      )

      result mustBe expected
    }

    "handle attempting to update a test that doesn't have a test result" in new TestFixture {
      when(mockApplicationRepository.find(any[String])).thenReturnAsync(Some(candidate))

      val phase1TestProfile = Phase1TestProfile(expirationDate = OffsetDateTime.now, tests = List(firstPsiTest.copy(testResult = None)))
      when(mockPhase1TestRepository.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))

      when(mockPhase1TestRepository.updateTestGroup(any[String], any[Phase1TestProfile])).thenReturnAsync()

      val updates = Seq(
        Phase1ScoreUpdateRequest(
          applicationId = "appId1",
          inventoryId = "inventoryId1",
          orderId = "orderId1",
          tScore = 66.0,
          rawScore = 40.0
        )
      )

      val result = service.updatePhase1Scores(updates).futureValue
      val expected = Seq(
        Phase1ScoreUpdateResponse(
          "appId1", "inventoryId1", "orderId1", tScore = 66.0, rawScore = 40.0,
          status = "No test found for inventoryId=inventoryId1 and orderId=orderId1 or missing test result"
        )
      )

      result mustBe expected
    }
  }

  trait TestFixture {
    val mockApplicationRepository = mock[GeneralApplicationRepository]
    val mockPhase1TestRepository = mock[Phase1TestRepository]
    val candidate = Candidate("userId", Some("appId1"), testAccountId = None,
      email = None, firstName = Some("George"), lastName = Some("Jetson"),
      preferredName = Some("George"), dateOfBirth = Some(LocalDate.of(1986, 5, 1)),
      address = None, postCode = None, country = None,
      applicationRoute = Some(ApplicationRoute.Faststream),
      applicationStatus = Some(ApplicationStatus.PHASE1_TESTS)
    )

    val service = new Phase1ScoresBulkUpdateService2(mockApplicationRepository, mockPhase1TestRepository)
  }
}
