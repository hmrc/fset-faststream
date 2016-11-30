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

import config.CubiksGatewayConfig
import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.Green
import model.SchemeType.SchemeType
import model.exchange.passmarksettings.{ Phase1PassMarkSettings, Phase1PassMarkSettingsExamples }
import model.persisted._
import model.{ ApplicationStatus, Phase1TestExamples, Phase1TestProfileExamples, SchemeType }
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import repositories.onlinetesting.OnlineTestEvaluationRepository
import repositories.passmarksettings.Phase1PassMarkSettingsMongoRepository
import services.BaseServiceSpec
import services.onlinetesting.phase1.Phase1TestEvaluation

import scala.concurrent.Future

class EvaluatePhase1ResultServiceSpec extends BaseServiceSpec {

  "next candidate ready for evaluation" should {
    "return none if passmark is not set" in new TestFixture {
      when(mockPhase1PMSRepository.getLatestVersion).thenReturn(Future.successful(None))
      val result = service.nextCandidatesReadyForEvaluation(1).futureValue
      result mustBe None
    }

    "return none if application for evaluation does not exist" in new TestFixture {
      val application = ApplicationPhase1EvaluationExamples.application

      when(mockPhase1PMSRepository.getLatestVersion).thenReturn(Future.successful(Some(PassmarkSettings)))
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

      verify(mockPhase1EvaluationRepository).savePassmarkEvaluation(AppId, ExpectedPassmarkEvaluation,
        Some(ApplicationStatus.PHASE1_TESTS_PASSED))
    }

    "save evaluated result and do not update the application status for PHASE1_TESTS_PASSED" in new TestFixture {
      val application = createAppWithTestGroup(twoTests).copy(applicationStatus = ApplicationStatus.PHASE1_TESTS_PASSED)

      service.evaluate(application, PassmarkSettings).futureValue

      verify(mockPhase1EvaluationRepository).savePassmarkEvaluation(AppId, ExpectedPassmarkEvaluation, None)
    }

    "save evaluated result and do not update the application status for PHASE2_TESTS" in new TestFixture {
      val application = createAppWithTestGroup(twoTests).copy(applicationStatus = ApplicationStatus.PHASE2_TESTS)

      service.evaluate(application, PassmarkSettings).futureValue

      verify(mockPhase1EvaluationRepository).savePassmarkEvaluation(AppId, ExpectedPassmarkEvaluation, None)
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
        Some(ApplicationStatus.PHASE1_TESTS_PASSED))
    }
  }

  trait TestFixture {
    val PassmarkSettings = Phase1PassMarkSettingsExamples.passmark
    val AppId = ApplicationPhase1EvaluationExamples.application.applicationId
    val PassmarkVersion = PassmarkSettings.version
    val SjqId = 16196
    val BqId = 16194
    val EvaluateForNonGis = List(SchemeEvaluationResult(SchemeType.DigitalAndTechnology, Green.toString))
    val ExpectedPassmarkEvaluation = PassmarkEvaluation(PassmarkVersion, None, EvaluateForNonGis)

    val mockPhase1EvaluationRepository = mock[OnlineTestEvaluationRepository[ApplicationReadyForEvaluation]]
    val mockCubiksGatewayConfig = mock[CubiksGatewayConfig]
    val mockPhase1PMSRepository = mock[Phase1PassMarkSettingsMongoRepository]

    when(mockPhase1EvaluationRepository.savePassmarkEvaluation(eqTo(AppId), any[PassmarkEvaluation], any[Option[ApplicationStatus]]))
      .thenReturn(Future.successful(()))

    val service = new EvaluatePhase1ResultService with StubbedPhase1TestEvaluation {
      val phase1EvaluationRepository = mockPhase1EvaluationRepository
      val gatewayConfig = mockCubiksGatewayConfig
      val passMarkSettingsRepo = mockPhase1PMSRepository

      override def sjq = SjqId

      override def bq = BqId
    }

    val edipSkipEvaluationService = new EvaluatePhase1ResultService {
      val phase1EvaluationRepository = mockPhase1EvaluationRepository
      val gatewayConfig = mockCubiksGatewayConfig
      val passMarkSettingsRepo = mockPhase1PMSRepository

      override def sjq = SjqId

      override def bq = BqId

      override def evaluateForNonGis(schemes: List[SchemeType], sjqTestResult: TestResult,bqTestResult: TestResult,
                                     passmark: Phase1PassMarkSettings): List[SchemeEvaluationResult] = {
        Nil
      }
    }

    def createAppWithTestGroup(tests: List[CubiksTest]) = {
      val phase1 = Phase1TestProfileExamples.profile.copy(tests = tests)
      ApplicationPhase1EvaluationExamples.application.copy(activeTests = phase1.activeTests)
    }

    def createGisAppWithTestGroup(tests: List[CubiksTest]) = {
      createAppWithTestGroup(tests).copy(isGis = true)
    }

    trait StubbedPhase1TestEvaluation extends Phase1TestEvaluation {
      override def evaluateForNonGis(schemes: List[SchemeType], sjqTestResult: TestResult,bqTestResult: TestResult,
                                     passmark: Phase1PassMarkSettings): List[SchemeEvaluationResult] = {
        EvaluateForNonGis
      }
    }

  }
}
