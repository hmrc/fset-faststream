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

import _root_.config.CubiksGatewayConfig
import _root_.services.BaseServiceSpec
import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.Green
import model.OnlineTestCommands.Phase1Test
import model.SchemeType.SchemeType
import model.exchange.passmarksettings.{ Phase1PassMarkSettings, Phase1PassMarkSettingsExamples }
import model.persisted.{ ApplicationPhase1EvaluationExamples, PassmarkEvaluation, SchemeEvaluationResult, TestResult }
import model.{ ApplicationStatus, Phase1TestExamples, Phase1TestProfileExamples, SchemeType }
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import repositories._
import repositories.onlinetesting.Phase1EvaluationRepository
import services.onlinetesting.phase1.Phase1TestEvaluation

import scala.concurrent.Future

class EvaluatePhase1ResultServiceSpec extends BaseServiceSpec {

  "next candidate ready for evaluation" should {
    "return none if passmark is not set" in new TestFixture {
      when(mockPhase1PMSRepository.getLatestVersion).thenReturn(Future.successful(None))
      val result = service.nextCandidateReadyForEvaluation.futureValue
      result mustBe None
    }

    "return none if application for evaluation does not exist" in new TestFixture {
      val application = ApplicationPhase1EvaluationExamples.application

      when(mockPhase1PMSRepository.getLatestVersion).thenReturn(Future.successful(Some(PassmarkSettings)))
      when(mockPhase1EvaluationRepository
        .nextApplicationReadyForPhase1ResultEvaluation(PassmarkVersion))
        .thenReturn(Future.successful(Some(application)))

      val result = service.nextCandidateReadyForEvaluation.futureValue

      result mustBe Some((application, PassmarkSettings))
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

  trait TestFixture {
    val PassmarkSettings = Phase1PassMarkSettingsExamples.passmark
    val AppId = ApplicationPhase1EvaluationExamples.application.applicationId
    val PassmarkVersion = PassmarkSettings.version
    val SjqId = 1
    val BqId = 2
    val EvaluateForNonGis = List(SchemeEvaluationResult(SchemeType.DigitalAndTechnology, Green.toString))
    val ExpectedPassmarkEvaluation = PassmarkEvaluation(PassmarkVersion, EvaluateForNonGis)

    val mockPhase1EvaluationRepository = mock[Phase1EvaluationRepository]
    val mockCubiksGatewayConfig = mock[CubiksGatewayConfig]
    val mockPhase1PMSRepository = mock[Phase1PassMarkSettingsRepository]

    when(mockPhase1EvaluationRepository.savePassmarkEvaluation(eqTo(AppId), any[PassmarkEvaluation], any[Option[ApplicationStatus]]))
      .thenReturn(Future.successful())

    val service = new EvaluatePhase1ResultService with StubbedPhase1TestEvaluation {
      val phase1EvaluationRepository = mockPhase1EvaluationRepository
      val gatewayConfig = mockCubiksGatewayConfig
      val phase1PMSRepository = mockPhase1PMSRepository

      override def sjq = SjqId

      override def bq = BqId
    }

    def createAppWithTestGroup(tests: List[Phase1Test]) = {
      val phase1 = Phase1TestProfileExamples.profile.copy(tests = tests)
      ApplicationPhase1EvaluationExamples.application.copy(phase1 = phase1)
    }

    def createGisAppWithTestGroup(tests: List[Phase1Test]) = {
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
