/*
 * Copyright 2020 HM Revenue & Customs
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

import config.{ MicroserviceAppConfig, OnlineTestsGatewayConfig, Phase1TestsConfig }
import factories.UUIDFactory
import model.EvaluationResults.Green
import model.ProgressStatuses.ProgressStatus
import model._
import model.exchange.passmarksettings.{ Phase1PassMarkSettings, Phase1PassMarkSettingsExamples }
import model.persisted._
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import repositories.onlinetesting.OnlineTestEvaluationRepository
import repositories.passmarksettings.Phase1PassMarkSettingsMongoRepository
import services.BaseServiceSpec

import scala.concurrent.Future

class EvaluatePhase1ResultServiceSpec extends BaseServiceSpec {

  "next candidate ready for evaluation" should {
    "return none if passmark is not set" in new TestFixture {
      when(mockPhase1PassMarkSettingRepository.getLatestVersion).thenReturn(Future.successful(None))
      val result = service.nextCandidatesReadyForEvaluation(1).futureValue
      result mustBe None
    }

    "return none if application for evaluation does not exist" in new TestFixture {
      val application = ApplicationPhase1EvaluationExamples.faststreamApplication

      when(mockPhase1PassMarkSettingRepository.getLatestVersion).thenReturn(Future.successful(Some(PassmarkSettings)))
      when(mockPhase1EvaluationRepository
        .nextApplicationsReadyForEvaluation(eqTo(PassmarkVersion), any[Int]))
        .thenReturn(Future.successful(List(application)))

      val result = service.nextCandidatesReadyForEvaluation(1).futureValue

      result mustBe Some((List(application), PassmarkSettings))
    }
  }

  "evaluate candidate" should {
    import Phase1TestExamples._

    val oneTest = List(firstTest)
    val twoTests = oneTest :+ secondTest

    "throw an exception if more than 2 tests are active" in new TestFixture {
      val threeTests = twoTests :+ thirdTest
      val application = createAppWithTestGroup(threeTests)

      intercept[IllegalArgumentException] {
        service.evaluate(application, PassmarkSettings).futureValue
      }
    }

    "throw an exception if GIS candidate has two tests" in new TestFixture {
      val application = createGisAppWithTestGroup(twoTests)

      intercept[IllegalStateException] {
        service.evaluate(application, PassmarkSettings).futureValue
      }
    }

    "throw an exception if non-GIS candidate has one test" in new TestFixture {
      val application = createAppWithTestGroup(oneTest)

      intercept[IllegalStateException] {
        service.evaluate(application, PassmarkSettings).futureValue
      }
    }

    "save evaluated result and update the application status" in new TestFixture {
      val application = createAppWithTestGroup(twoTests).copy(applicationStatus = ApplicationStatus.PHASE1_TESTS)

      service.evaluate(application, PassmarkSettings).futureValue

      val applicationIdCaptor = ArgumentCaptor.forClass(classOf[String])
      val passmarkEvaluationCaptor = ArgumentCaptor.forClass(classOf[PassmarkEvaluation])
      val progressStatusCaptor = ArgumentCaptor.forClass(classOf[Option[ProgressStatus]])

      verify(mockPhase1EvaluationRepository).savePassmarkEvaluation(applicationIdCaptor.capture, passmarkEvaluationCaptor.capture,
        progressStatusCaptor.capture)

      applicationIdCaptor.getValue.toString mustBe AppId
      passmarkEvaluationCaptor.getValue.passmarkVersion mustBe PassmarkVersion
      passmarkEvaluationCaptor.getValue.result mustBe EvaluateForNonGis
      passmarkEvaluationCaptor.getValue.resultVersion must not be ""
      progressStatusCaptor.getValue mustBe Some(ProgressStatuses.PHASE1_TESTS_PASSED)
    }

    "save evaluated result and do not update the application status for PHASE1_TESTS_PASSED" in new TestFixture {
      val application = createAppWithTestGroup(twoTests).copy(applicationStatus = ApplicationStatus.PHASE1_TESTS_PASSED)

      service.evaluate(application, PassmarkSettings).futureValue

      val applicationIdCaptor = ArgumentCaptor.forClass(classOf[String])
      val passmarkEvaluationCaptor = ArgumentCaptor.forClass(classOf[PassmarkEvaluation])
      val progressStatusCaptor = ArgumentCaptor.forClass(classOf[Option[ProgressStatus]])

      verify(mockPhase1EvaluationRepository).savePassmarkEvaluation(applicationIdCaptor.capture, passmarkEvaluationCaptor.capture,
        progressStatusCaptor.capture)

      applicationIdCaptor.getValue.toString mustBe AppId
      passmarkEvaluationCaptor.getValue.passmarkVersion mustBe PassmarkVersion
      passmarkEvaluationCaptor.getValue.result mustBe EvaluateForNonGis
      passmarkEvaluationCaptor.getValue.resultVersion must not be ""
      progressStatusCaptor.getValue mustBe None
    }

    "save evaluated result and do not update the application status for PHASE2_TESTS" in new TestFixture {
      val application = createAppWithTestGroup(twoTests).copy(applicationStatus = ApplicationStatus.PHASE2_TESTS)

      service.evaluate(application, PassmarkSettings).futureValue

      val applicationIdCaptor = ArgumentCaptor.forClass(classOf[String])
      val passmarkEvaluationCaptor = ArgumentCaptor.forClass(classOf[PassmarkEvaluation])
      val progressStatusCaptor = ArgumentCaptor.forClass(classOf[Option[ProgressStatus]])

      verify(mockPhase1EvaluationRepository).savePassmarkEvaluation(applicationIdCaptor.capture, passmarkEvaluationCaptor.capture,
        progressStatusCaptor.capture)

      applicationIdCaptor.getValue.toString mustBe AppId
      passmarkEvaluationCaptor.getValue.passmarkVersion mustBe PassmarkVersion
      passmarkEvaluationCaptor.getValue.result mustBe EvaluateForNonGis
      passmarkEvaluationCaptor.getValue.resultVersion must not be ""
      progressStatusCaptor.getValue mustBe None
    }
  }

  "evaluate edip candidate" should {
    import Phase1TestExamples._

    val oneTest = List(firstTest)
    val twoTests = oneTest :+ secondTest

    "not save the phase1 test results" in new TestFixture {
      val application = createAppWithTestGroup(twoTests).copy(applicationStatus = ApplicationStatus.PHASE1_TESTS)

      edipSkipEvaluationService.evaluate(application, PassmarkSettings).futureValue

      verify(mockPhase1EvaluationRepository, never()).savePassmarkEvaluation(AppId, ExpectedPassmarkEvaluation,
        Some(ProgressStatuses.PHASE1_TESTS_PASSED))
    }
  }

  trait TestFixture {
    val PassmarkSettings = Phase1PassMarkSettingsExamples.passmark
    val AppId = ApplicationPhase1EvaluationExamples.faststreamApplication.applicationId
    val PassmarkVersion = PassmarkSettings.version
    val SjqId = 16196
    val BqId = 16194
    val EvaluateForNonGis = List(SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), Green.toString))
    val ExpectedPassmarkEvaluation = PassmarkEvaluation(PassmarkVersion, None, EvaluateForNonGis, "", None)

    val mockPhase1EvaluationRepository = mock[OnlineTestEvaluationRepository]
    val mockPhase1PassMarkSettingRepository = mock[Phase1PassMarkSettingsMongoRepository]
    val mockAppConfig = mock[MicroserviceAppConfig]
    val mockOnlineTestsGatewayConfig = mock[OnlineTestsGatewayConfig]
    val mockPhase1TestsConfig = mock[Phase1TestsConfig]
    when(mockPhase1TestsConfig.scheduleIds).thenReturn(Map("sjq" -> SjqId, "bq" -> BqId))

    when(mockAppConfig.onlineTestsGatewayConfig).thenReturn(mockOnlineTestsGatewayConfig)
    when(mockOnlineTestsGatewayConfig.phase1Tests).thenReturn(mockPhase1TestsConfig)

    when(mockPhase1EvaluationRepository.savePassmarkEvaluation(eqTo(AppId), any[PassmarkEvaluation], any[Option[ProgressStatus]]))
      .thenReturn(Future.successful(()))

    val service = new EvaluatePhase1ResultService(
      mockPhase1EvaluationRepository,
      mockPhase1PassMarkSettingRepository,
      mockAppConfig,
      UUIDFactory
    ) with StubbedPhase1TestEvaluation

    val edipSkipEvaluationService = new EvaluatePhase1ResultService(
      mockPhase1EvaluationRepository,
      mockPhase1PassMarkSettingRepository,
      mockAppConfig,
      UUIDFactory
    ) {
      override def evaluateForNonGis(schemes: List[SchemeId], sjqTestResult: TestResult,bqTestResult: TestResult,
                                     passmark: Phase1PassMarkSettings): List[SchemeEvaluationResult] = {
        Nil
      }
    }

    def createAppWithTestGroup(tests: List[CubiksTest]) = {
      val phase1 = Phase1TestProfileExamples.profile.copy(tests = tests)
      ApplicationPhase1EvaluationExamples.faststreamApplication.copy(activeCubiksTests = phase1.activeTests)
    }

    def createGisAppWithTestGroup(tests: List[CubiksTest]) = {
      createAppWithTestGroup(tests).copy(isGis = true)
    }

    trait StubbedPhase1TestEvaluation extends Phase1TestEvaluation {
      override def evaluateForNonGis(schemes: List[SchemeId], sjqTestResult: TestResult,bqTestResult: TestResult,
                                     passmark: Phase1PassMarkSettings): List[SchemeEvaluationResult] = {
        EvaluateForNonGis
      }
    }
  }
}
