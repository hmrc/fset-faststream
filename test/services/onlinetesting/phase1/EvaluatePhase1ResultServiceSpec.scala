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

import config.{MicroserviceAppConfig, OnlineTestsGatewayConfig, Phase1TestsConfig, PsiTestIds}
import factories.UUIDFactory
import model.EvaluationResults.Green
import model.Phase1TestExamples._
import model.ProgressStatuses.ProgressStatus
import model._
import model.exchange.passmarksettings.{Phase1PassMarkSettings, Phase1PassMarkSettingsExamples, Phase1PassMarkSettingsPersistence}
import model.persisted._
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import repositories.onlinetesting.OnlineTestEvaluationRepository
import repositories.passmarksettings.Phase1PassMarkSettingsMongoRepository
import services.BaseServiceSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class EvaluatePhase1ResultServiceSpec extends BaseServiceSpec {

  "next candidate ready for evaluation" should {
    "return none if passmark is not set" in new TestFixture {
      when(mockPhase1PassMarkSettingsRepository.getLatestVersion).thenReturn(Future.successful(None))
      val result = service.nextCandidatesReadyForEvaluation(1).futureValue
      result mustBe None
    }

    "return none if application for evaluation does not exist" in new TestFixture {
      val application = ApplicationPhase1EvaluationExamples.faststreamApplication

      when(mockPhase1PassMarkSettingsRepository.getLatestVersion).thenReturn(Future.successful(Some(PassmarkSettings)))
      when(mockPhase1EvaluationRepository
        .nextApplicationsReadyForEvaluation(eqTo(PassmarkVersion), any[Int])(any[ExecutionContext]))
        .thenReturn(Future.successful(List(application)))

      val result = service.nextCandidatesReadyForEvaluation(1).futureValue

      result mustBe Some((List(application), PassmarkSettings))
    }
  }

  "evaluate candidate" should {
    import Phase1TestExamples._

    "throw an exception if more than 2 tests are active" in new TestFixture {
      val threeTests = twoTests :+ thirdPsiTest
      val application = createAppWithTestGroup(threeTests)

      intercept[IllegalArgumentException] {
        service.evaluate(application, PassmarkSettings).futureValue
      }
    }

    "throw an exception if GIS candidate has four tests" in new TestFixture {
      val application = createGisAppWithTestGroup(twoTests)

      intercept[IllegalStateException] {
        service.evaluate(application, PassmarkSettings).futureValue
      }
    }

    "throw an exception if non-GIS candidate has two tests" in new TestFixture {
      val application = createAppWithTestGroup(twoTests)

      intercept[IllegalStateException] {
        service.evaluate(application, PassmarkSettings).futureValue
      }
    }

    "save evaluated result and update the application status" in new TestFixture {
      val application = createAppWithTestGroup(fourTests).copy(applicationStatus = ApplicationStatus.PHASE1_TESTS)

      service.evaluate(application, PassmarkSettings).futureValue

      val applicationIdCaptor = ArgumentCaptor.forClass(classOf[String])
      val passmarkEvaluationCaptor = ArgumentCaptor.forClass(classOf[PassmarkEvaluation])
      val progressStatusCaptor = ArgumentCaptor.forClass(classOf[Option[ProgressStatus]])

      verify(mockPhase1EvaluationRepository).savePassmarkEvaluation(applicationIdCaptor.capture, passmarkEvaluationCaptor.capture,
        progressStatusCaptor.capture)(any[ExecutionContext])

      applicationIdCaptor.getValue.toString mustBe AppId
      passmarkEvaluationCaptor.getValue.passmarkVersion mustBe PassmarkVersion
      passmarkEvaluationCaptor.getValue.result mustBe EvaluateForNonGis
      passmarkEvaluationCaptor.getValue.resultVersion must not be ""
      progressStatusCaptor.getValue mustBe Some(ProgressStatuses.PHASE1_TESTS_PASSED)
    }

    "save evaluated result and do not update the application status for PHASE1_TESTS_PASSED" in new TestFixture {
      val application = createAppWithTestGroup(fourTests).copy(applicationStatus = ApplicationStatus.PHASE1_TESTS_PASSED)

      service.evaluate(application, PassmarkSettings).futureValue

      val applicationIdCaptor = ArgumentCaptor.forClass(classOf[String])
      val passmarkEvaluationCaptor = ArgumentCaptor.forClass(classOf[PassmarkEvaluation])
      val progressStatusCaptor = ArgumentCaptor.forClass(classOf[Option[ProgressStatus]])

      verify(mockPhase1EvaluationRepository).savePassmarkEvaluation(applicationIdCaptor.capture, passmarkEvaluationCaptor.capture,
        progressStatusCaptor.capture)(any[ExecutionContext])

      applicationIdCaptor.getValue.toString mustBe AppId
      passmarkEvaluationCaptor.getValue.passmarkVersion mustBe PassmarkVersion
      passmarkEvaluationCaptor.getValue.result mustBe EvaluateForNonGis
      passmarkEvaluationCaptor.getValue.resultVersion must not be ""
      progressStatusCaptor.getValue mustBe None
    }

    "save evaluated result and do not update the application status for PHASE2_TESTS" in new TestFixture {
      val application = createAppWithTestGroup(fourTests).copy(applicationStatus = ApplicationStatus.PHASE2_TESTS)

      service.evaluate(application, PassmarkSettings).futureValue

      val applicationIdCaptor = ArgumentCaptor.forClass(classOf[String])
      val passmarkEvaluationCaptor = ArgumentCaptor.forClass(classOf[PassmarkEvaluation])
      val progressStatusCaptor = ArgumentCaptor.forClass(classOf[Option[ProgressStatus]])

      verify(mockPhase1EvaluationRepository).savePassmarkEvaluation(applicationIdCaptor.capture, passmarkEvaluationCaptor.capture,
        progressStatusCaptor.capture)(any[ExecutionContext])

      applicationIdCaptor.getValue.toString mustBe AppId
      passmarkEvaluationCaptor.getValue.passmarkVersion mustBe PassmarkVersion
      passmarkEvaluationCaptor.getValue.result mustBe EvaluateForNonGis
      passmarkEvaluationCaptor.getValue.resultVersion must not be ""
      progressStatusCaptor.getValue mustBe None
    }
  }

  "evaluate edip candidate" should {
    "not save the phase1 test results" in new TestFixture {
      val application = createAppWithTestGroup(fourTests).copy(applicationStatus = ApplicationStatus.PHASE1_TESTS)

      edipSkipEvaluationService.evaluate(application, PassmarkSettings).futureValue

      verify(mockPhase1EvaluationRepository, never()).savePassmarkEvaluation(AppId, ExpectedPassmarkEvaluation,
        Some(ProgressStatuses.PHASE1_TESTS_PASSED))
    }
  }

  trait TestFixture extends Schemes {
    val PassmarkSettings = Phase1PassMarkSettingsExamples.passmark
    val AppId = ApplicationPhase1EvaluationExamples.faststreamApplication.applicationId
    val PassmarkVersion = PassmarkSettings.version
    val EvaluateForNonGis = List(SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Green.toString))
    val ExpectedPassmarkEvaluation = PassmarkEvaluation(PassmarkVersion, None, EvaluateForNonGis, "", None)

//    val mockPhase1EvaluationRepository = mock[Phase1EvaluationMongoRepository]
    val mockPhase1EvaluationRepository = mock[OnlineTestEvaluationRepository]
    val mockPhase1PassMarkSettingsRepository = mock[Phase1PassMarkSettingsMongoRepository]
    val mockAppConfig = mock[MicroserviceAppConfig]
    val mockOnlineTestsGatewayConfig = mock[OnlineTestsGatewayConfig]

    when(mockAppConfig.onlineTestsGatewayConfig).thenReturn(mockOnlineTestsGatewayConfig)

    when(mockPhase1EvaluationRepository
      .savePassmarkEvaluation(eqTo(AppId), any[PassmarkEvaluation], any[Option[ProgressStatus]])(any[ExecutionContext]))
      .thenReturn(Future.successful(()))

    val oneTest = List(firstPsiTest)
    val twoTests = oneTest :+ secondPsiTest
    val fourTests = twoTests :+ thirdPsiTest :+ fourthPsiTest

    def testIds(idx: Int): PsiTestIds =
      PsiTestIds(s"inventory-id-$idx", s"assessment-id-$idx", s"report-id-$idx", s"norm-id-$idx")

    val tests = Map[String, PsiTestIds](
      "test1" -> testIds(1),
      "test2" -> testIds(2),
      "test3" -> testIds(3),
      "test4" -> testIds(4)
    )

    val mockPhase1TestConfig = Phase1TestsConfig(
      expiryTimeInDays = 5, gracePeriodInSecs = 0, testRegistrationDelayInSecs = 1, tests, standard = List("test1", "test2", "test3", "test4"),
      gis = List("test1", "test4")
    )
    when(mockOnlineTestsGatewayConfig.phase1Tests).thenReturn(mockPhase1TestConfig)

    val service = new EvaluatePhase1ResultService(
      mockPhase1EvaluationRepository,
      mockPhase1PassMarkSettingsRepository,
      mockAppConfig,
      UUIDFactory
    ) with StubbedPhase1TestEvaluation {
      override def inventoryIdForTest(testName: String) = {
        testName match {
          case "test1" => "inventoryId1"
          case "test2" => "inventoryId2"
          case "test3" => "inventoryId3"
          case "test4" => "inventoryId4"
          case _ => throw new IllegalStateException(s"Unknown test: $testName")
        }
      }
    }

    val edipSkipEvaluationService = new EvaluatePhase1ResultService(
      mockPhase1EvaluationRepository,
      mockPhase1PassMarkSettingsRepository,
      mockAppConfig,
      UUIDFactory
    ) {
      override def inventoryIdForTest(testName: String) = {
        testName match {
          case "test1" => "inventoryId1"
          case "test2" => "inventoryId2"
          case "test3" => "inventoryId3"
          case "test4" => "inventoryId4"
          case _ => throw new IllegalStateException(s"Unknown test: $testName")
        }
      }
    }

    def createAppWithTestGroup(tests: List[PsiTest]) = {
      val phase1 = Phase1TestProfileExamples.psiProfile.copy(tests = tests)
      ApplicationPhase1EvaluationExamples.faststreamApplication.copy(activePsiTests = phase1.activeTests)
    }

    def createGisAppWithTestGroup(tests: List[PsiTest]) = {
      createAppWithTestGroup(tests).copy(isGis = true)
    }

    trait StubbedPhase1TestEvaluation extends Phase1TestEvaluation {
      override def evaluateForNonGis(schemes: List[SchemeId], test1Result: PsiTestResult, test2Result: PsiTestResult,
                                     test3Result: PsiTestResult, test4Result: PsiTestResult,
                                     passmark: Phase1PassMarkSettingsPersistence): List[SchemeEvaluationResult] = {
        EvaluateForNonGis
      }
    }
  }
}
