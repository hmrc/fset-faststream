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

package services.campaignmanagement

import factories.UUIDFactory
import model.Phase1TestExamples._
import model.Phase2TestExamples._
import model.command.SetTScoreRequest
import model.exchange.campaignmanagement.{AfterDeadlineSignupCode, AfterDeadlineSignupCodeUnused}
import model.persisted.{CampaignManagementAfterDeadlineCode, Phase1TestProfile, Phase2TestGroup}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import repositories.application.GeneralApplicationRepository
import repositories.campaignmanagement.CampaignManagementAfterDeadlineSignupCodeRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.onlinetesting._
import repositories.{MediaRepository, QuestionnaireRepository}
import services.BaseServiceSpec
import testkit.MockitoImplicits._

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext.Implicits.global

class CampaignManagementServiceSpec extends BaseServiceSpec {

  "afterDeadlineSignupCodeUnusedAndValid" should {
    "return true with an expiry if code is unused and unexpired" in new TestFixture {
      val expiryTime = DateTime.now

      when(mockAfterDeadlineCodeRepository.findUnusedValidCode("1234")
      ).thenReturnAsync(Some(CampaignManagementAfterDeadlineCode("1234", "userId1", expiryTime, None)))

      val response = service.afterDeadlineSignupCodeUnusedAndValid("1234").futureValue
      response mustBe AfterDeadlineSignupCodeUnused(unused = true, Some(expiryTime))
    }

    "return false without an expiry if code is used or expired"  in new TestFixture {
      val expiryTime = DateTime.now

      when(mockAfterDeadlineCodeRepository.findUnusedValidCode("1234")
      ).thenReturnAsync(None)

      val response = service.afterDeadlineSignupCodeUnusedAndValid("1234").futureValue
      response mustBe AfterDeadlineSignupCodeUnused(unused = false, None)
    }
  }

  "generateAfterDeadlineSignupCode" should {
    "save and return a new signup code" in new TestFixture {
      when(mockAfterDeadlineCodeRepository.save(any[CampaignManagementAfterDeadlineCode]()))
        .thenReturnAsync()
      when(mockUuidFactory.generateUUID()).thenReturn("1234")

      val response = service.generateAfterDeadlineSignupCode("userId1", 48).futureValue

      response mustBe AfterDeadlineSignupCode("1234")
    }
  }

  "setPhase1TScore" should {
    "handle not finding a test profile" in new TestFixture {
      when(mockPhase1TestRepository.getTestGroup(any[String])).thenReturnAsync(None)

      val request = SetTScoreRequest(applicationId = "appId", inventoryId = None, phase = "PHASE1", tScore = 20.0)
      val response = service.setPhase1TScore(request)

      val exception = response.failed.futureValue
      exception mustBe an[IllegalStateException]
    }

    "handle finding a test profile that contains fewer than the full set of active tests" in new TestFixture {
      val phase1TestProfile = Phase1TestProfile(expirationDate = OffsetDateTime.now(),
                                    tests = List(firstPsiTest, secondPsiTest, thirdPsiTest),
                                    evaluation = None)

      when(mockPhase1TestRepository.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))

      val request = SetTScoreRequest(applicationId = "appId", inventoryId = None, phase = phase1, tScore = 20.0)
      val response = service.setPhase1TScore(request)

      val exception = response.failed.futureValue
      exception mustBe an[IllegalStateException]
    }

    "handle finding a test profile that contains the full set of active tests but missing one test result" in new TestFixture {
      val phase1TestProfile = Phase1TestProfile(expirationDate = OffsetDateTime.now(),
                                    tests = List(firstPsiTest, secondPsiTest, thirdPsiTest, fourthPsiTest.copy(testResult = None)),
                                    evaluation = None)

      when(mockPhase1TestRepository.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))

      val request = SetTScoreRequest(applicationId = "appId", inventoryId = None, phase = phase1, tScore = 20.0)
      val response = service.setPhase1TScore(request)

      val exception = response.failed.futureValue
      exception mustBe an[IllegalStateException]
    }

    "successfully process a request when updating the full set of active tests with test results" in new TestFixture {
      val phase1TestProfile = Phase1TestProfile(expirationDate = OffsetDateTime.now(),
                                    tests = List(firstPsiTest, secondPsiTest, thirdPsiTest, fourthPsiTest),
                                    evaluation = None)
      when(mockPhase1TestRepository.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))

      when(mockPhase1TestRepository.insertOrUpdateTestGroup(any[String], any[Phase1TestProfile])).thenReturnAsync()

      val request = SetTScoreRequest(applicationId = "appId", inventoryId = None, phase = phase1, tScore = 20.0)
      val response = service.setPhase1TScore(request).futureValue
      response mustBe unit
    }

    "successfully process a request when updating the full set of active tests with test results for a gis candidate" in new TestFixture {
      val phase1TestProfile = Phase1TestProfile(expirationDate = OffsetDateTime.now(),
        tests = List(firstPsiTest, fourthPsiTest), evaluation = None)
      when(mockApplicationRepository.gisByApplication(any[String])).thenReturnAsync(true)
      when(mockPhase1TestRepository.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))

      when(mockPhase1TestRepository.insertOrUpdateTestGroup(any[String], any[Phase1TestProfile])).thenReturnAsync()

      val request = SetTScoreRequest(applicationId = "appId", inventoryId = None, phase = phase1, tScore = 20.0)
      val response = service.setPhase1TScore(request).futureValue
      response mustBe unit
    }

    "throw an exception when updating tests for a gis candidate and the number of tests is not as expected" in new TestFixture {
      val phase1TestProfile = Phase1TestProfile(expirationDate = OffsetDateTime.now(),
        tests = List(firstPsiTest, secondPsiTest, thirdPsiTest, fourthPsiTest), evaluation = None)
      when(mockApplicationRepository.gisByApplication(any[String])).thenReturnAsync(true)
      when(mockPhase1TestRepository.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))

      when(mockPhase1TestRepository.insertOrUpdateTestGroup(any[String], any[Phase1TestProfile])).thenReturnAsync()

      val request = SetTScoreRequest(applicationId = "appId", inventoryId = None, phase = phase1, tScore = 20.0)
      val exception = service.setPhase1TScore(request).failed.futureValue
      exception mustBe an[IllegalStateException]
    }

    "successfully process a request when updating a single active test" in new TestFixture {
      val phase1TestProfile = Phase1TestProfile(expirationDate = OffsetDateTime.now(),
                                    tests = List(firstPsiTest, secondPsiTest, thirdPsiTest, fourthPsiTest),
                                    evaluation = None)
      when(mockPhase1TestRepository.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))

      when(mockPhase1TestRepository.insertOrUpdateTestGroup(any[String], any[Phase1TestProfile])).thenReturnAsync()

      val request = SetTScoreRequest(applicationId = "appId", inventoryId = Some("inventoryId4"), phase = phase1, tScore = 20.0)
      val response = service.setPhase1TScore(request).futureValue
      response mustBe unit
    }

    "handle an incorrect inventory id when processing a request to updating a single active test" in new TestFixture {
      val phase1TestProfile = Phase1TestProfile(expirationDate = OffsetDateTime.now(),
                                    tests = List(firstPsiTest, secondPsiTest, thirdPsiTest, fourthPsiTest),
                                    evaluation = None)
      when(mockPhase1TestRepository.getTestGroup(any[String])).thenReturnAsync(Some(phase1TestProfile))

      when(mockPhase1TestRepository.insertOrUpdateTestGroup(any[String], any[Phase1TestProfile])).thenReturnAsync()

      val request = SetTScoreRequest(applicationId = "appId", inventoryId = Some("will-not-find"), phase = phase1, tScore = 20.0)
      val exception = service.setPhase1TScore(request).failed.futureValue
      exception mustBe a[Exception]
    }
  }

  "setPhase2TScore" should {
    "handle not finding a test profile" in new TestFixture {
      when(mockPhase2TestRepository.getTestGroup(any[String])).thenReturnAsync(None)

      val request = SetTScoreRequest(applicationId = "appId", inventoryId = None, phase = phase2, tScore = 20.0)
      val response = service.setPhase2TScore(request)

      val exception = response.failed.futureValue
      exception mustBe an[IllegalStateException]
    }

    "handle finding a test profile that contains fewer than the full set of active tests" in new TestFixture {
      val phase2TestProfile = Phase2TestGroup(expirationDate = OffsetDateTime.now(),
                                    tests = List(fifthPsiTest),
                                    evaluation = None)

      when(mockPhase2TestRepository.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestProfile))

      val request = SetTScoreRequest(applicationId = "appId", inventoryId = None, phase = phase2, tScore = 20.0)
      val response = service.setPhase2TScore(request)

      val exception = response.failed.futureValue
      exception mustBe an[IllegalStateException]
    }

    "handle finding a test profile that contains the full set of active tests but missing one test result" in new TestFixture {
      val phase2TestProfile = Phase2TestGroup(expirationDate = OffsetDateTime.now(),
                                    tests = List(fifthPsiTest, sixthPsiTest.copy(testResult = None)),
                                    evaluation = None)

      when(mockPhase2TestRepository.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestProfile))

      val request = SetTScoreRequest(applicationId = "appId", inventoryId = None, phase = phase2, tScore = 20.0)
      val response = service.setPhase2TScore(request)

      val exception = response.failed.futureValue
      exception mustBe an[IllegalStateException]
    }

    "successfully process a request when updating the full set of active tests with test results" in new TestFixture {
      val phase2TestProfile = Phase2TestGroup(expirationDate = OffsetDateTime.now(),
                                    tests = List(fifthPsiTest, sixthPsiTest),
                                    evaluation = None)
      when(mockPhase2TestRepository.getTestGroup(any[String])).thenReturnAsync(Some(phase2TestProfile))

      when(mockPhase2TestRepository.insertOrUpdateTestGroup(any[String], any[Phase2TestGroup])).thenReturnAsync()

      val request = SetTScoreRequest(applicationId = "appId", inventoryId = None, phase = phase2, tScore = 20.0)
      val response = service.setPhase2TScore(request).futureValue
      response mustBe unit
    }
  }

  trait TestFixture  {
    val mockAfterDeadlineCodeRepository = mock[CampaignManagementAfterDeadlineSignupCodeRepository]
    val mockUuidFactory = mock[UUIDFactory]
    val mockApplicationRepository = mock[GeneralApplicationRepository]
    val mockPhase1TestRepository = mock[Phase1TestRepository]
    val mockPhase2TestRepository = mock[Phase2TestRepository]
    val mockQuestionnaireRepository = mock[QuestionnaireRepository]
    val mockMediaRepository = mock[MediaRepository]
    val mockContactDetailsRepository = mock[ContactDetailsRepository]

    val service = new CampaignManagementService(
      mockAfterDeadlineCodeRepository,
      mockUuidFactory,
      mockApplicationRepository,
      mockPhase1TestRepository,
      mockPhase2TestRepository,
      mockQuestionnaireRepository,
      mockMediaRepository,
      mockContactDetailsRepository
    )

    val phase1 = "PHASE1"
    val phase2 = "PHASE2"

    when(mockApplicationRepository.gisByApplication(any[String])).thenReturnAsync(false)
  }
}
