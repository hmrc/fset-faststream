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

package services.onlinetesting.phase2

import config.{MicroserviceAppConfig, OnlineTestsGatewayConfig, Phase2TestsConfig, PsiTestIds}
import factories.UUIDFactory
import model.EvaluationResults.{Amber, Green}
import model.ProgressStatuses.ProgressStatus
import model._
import model.exchange.passmarksettings.Phase2PassMarkSettingsExamples
import model.persisted._
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.OnlineTestEvaluationRepository
import repositories.passmarksettings.Phase2PassMarkSettingsMongoRepository
import services.BaseServiceSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class EvaluatePhase2ResultServiceSpec extends BaseServiceSpec with Schemes {

  "evaluate candidate" should {

    import Phase2TestExamples._

    val twoTests = List(fifthPsiTest, sixthPsiTest)

    "throw an exception if there are no active tests" in new TestFixture {
      val thrown = intercept[IllegalArgumentException] {
        val application = createAppWithTestGroup(Nil).copy(applicationStatus = ApplicationStatus.PHASE1_TESTS_PASSED)
        service.evaluate(application, passmarkSettings).futureValue
      }
      thrown.getMessage startsWith "requirement failed: Allowed active number of tests for phase2 is 2 - found 0"
    }

    "throw an exception if there is no previous phase evaluation" in new TestFixture {
      val thrown = intercept[IllegalArgumentException] {
        val application = createAppWithTestGroup(twoTests).copy(applicationStatus = ApplicationStatus.PHASE1_TESTS_PASSED)
        service.evaluate(application, passmarkSettings).futureValue
      }
      thrown.getMessage mustBe "requirement failed: Phase1 results are required before we can evaluate phase2"
    }

    "evaluate the expected schemes when processing a faststream candidate" in new TestFixture {
      val application = createAppWithTestGroup(twoTests).copy(
        applicationStatus = ApplicationStatus.PHASE1_TESTS_PASSED, prevPhaseEvaluation = previousPhaseEvaluation)

      service.evaluate(application, passmarkSettings).futureValue

      val applicationIdCaptor = ArgumentCaptor.forClass(classOf[String])
      val passmarkEvaluationCaptor = ArgumentCaptor.forClass(classOf[PassmarkEvaluation])
      val progressStatusCaptor = ArgumentCaptor.forClass(classOf[Option[ProgressStatus]])
      val cssCaptor = ArgumentCaptor.forClass(classOf[Seq[SchemeEvaluationResult]])

      verify(mockPhase2EvaluationRepository).savePassmarkEvaluation(applicationIdCaptor.capture, passmarkEvaluationCaptor.capture,
        progressStatusCaptor.capture, cssCaptor.capture)(any[ExecutionContext])

      applicationIdCaptor.getValue.toString mustBe appId
      val expected = List(SchemeEvaluationResult(
        DigitalDataTechnologyAndCyber, Amber.toString),
        SchemeEvaluationResult(Commercial, Amber.toString)
      )
      passmarkEvaluationCaptor.getValue.result mustBe expected
      progressStatusCaptor.getValue mustBe None
      cssCaptor.getValue mustBe Seq(
        SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Amber.toString),
        SchemeEvaluationResult(Commercial, Amber.toString)
      )
    }

    "include sdip evaluation read from current scheme status when saving evaluation for sdip faststream candidate" in new TestFixture {
      val application = createSdipFaststreamAppWithTestGroup(twoTests).copy(
        applicationStatus = ApplicationStatus.PHASE1_TESTS_PASSED, prevPhaseEvaluation = previousPhaseEvaluation)

      when(mockApplicationRepository.getCurrentSchemeStatus(eqTo(appId))).thenReturn(
        Future.successful(Seq(SchemeEvaluationResult(Sdip, Amber.toString))))

      service.evaluate(application, passmarkSettings).futureValue

      val applicationIdCaptor = ArgumentCaptor.forClass(classOf[String])
      val passmarkEvaluationCaptor = ArgumentCaptor.forClass(classOf[PassmarkEvaluation])
      val progressStatusCaptor = ArgumentCaptor.forClass(classOf[Option[ProgressStatus]])
      val cssCaptor = ArgumentCaptor.forClass(classOf[Seq[SchemeEvaluationResult]])

      verify(mockPhase2EvaluationRepository).savePassmarkEvaluation(applicationIdCaptor.capture, passmarkEvaluationCaptor.capture,
        progressStatusCaptor.capture, cssCaptor.capture)(any[ExecutionContext])

      applicationIdCaptor.getValue.toString mustBe appId
      val expected = List(SchemeEvaluationResult(
        DigitalDataTechnologyAndCyber, Amber.toString),
        SchemeEvaluationResult(Commercial, Amber.toString),
        SchemeEvaluationResult(Sdip, Amber.toString)
      )
      passmarkEvaluationCaptor.getValue.result mustBe expected
      progressStatusCaptor.getValue mustBe None
      cssCaptor.getValue must contain theSameElementsAs Seq(
        SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Amber.toString),
        SchemeEvaluationResult(Commercial, Amber.toString),
        SchemeEvaluationResult(Sdip, Amber.toString)
      )
    }
  }

  trait TestFixture {
    val appId = ApplicationPhase1EvaluationExamples.faststreamApplication.applicationId
    val passmarkSettings = Phase2PassMarkSettingsExamples.passMarkSettings(List(
      (Commercial,                    10.0, 20.0),
      (DigitalDataTechnologyAndCyber, 10.0, 20.0)
    ))

    val mockPhase2EvaluationRepository = mock[OnlineTestEvaluationRepository]
    val mockPhase2PassMarkSettingsRepository = mock[Phase2PassMarkSettingsMongoRepository]

    when(mockPhase2EvaluationRepository.savePassmarkEvaluation(
      eqTo(appId), any[PassmarkEvaluation], any[Option[ProgressStatus]], any[Seq[SchemeEvaluationResult]])(
      any[ExecutionContext]))
      .thenReturn(Future.successful(()))

    val mockApplicationRepository = mock[GeneralApplicationRepository]
    when(mockApplicationRepository.getCurrentSchemeStatus(eqTo(appId))).thenReturn(Future.successful(Nil))

    val previousPhaseEvaluation = Some(
      PassmarkEvaluation(
        passmarkVersion = "v2",
        previousPhasePassMarkVersion = Some("v1"),
        result = List(SchemeEvaluationResult(Commercial, Green.toString),
          SchemeEvaluationResult(DigitalDataTechnologyAndCyber, Green.toString)),
        resultVersion = "res-v2",
        previousPhaseResultVersion = Some("res-v1"))
    )

    val mockOnlineTestsGatewayConfig = mock[OnlineTestsGatewayConfig]
    val mockAppConfig = mock[MicroserviceAppConfig]
    when(mockAppConfig.onlineTestsGatewayConfig).thenReturn(mockOnlineTestsGatewayConfig)

    def testIds(idx: Int): PsiTestIds =
      PsiTestIds(s"inventoryId$idx", s"assessmentId$idx", s"reportId$idx", s"normId$idx")

    val tests = Map[String, PsiTestIds](
      "test1" -> testIds(5),
      "test2" -> testIds(6)
    )

    val mockPhase2TestConfig = Phase2TestsConfig(
      expiryTimeInDays = 5, expiryTimeInDaysForInvigilatedETray = 10, gracePeriodInSecs = 0, testRegistrationDelayInSecs = 1,
      tests, List("test1", "test2")
    )
    when(mockOnlineTestsGatewayConfig.phase2Tests).thenReturn(mockPhase2TestConfig)

    val service = new EvaluatePhase2ResultService(
      mockPhase2EvaluationRepository,
      mockPhase2PassMarkSettingsRepository,
      mockApplicationRepository,
      mockAppConfig,
      UUIDFactory
    )

    def createAppWithTestGroup(tests: List[PsiTest]) = {
      val phase2 = Phase2TestProfileExamples.profile.copy(tests = tests)
      ApplicationPhase1EvaluationExamples.faststreamApplication.copy(activePsiTests = phase2.activeTests)
    }

    def createSdipFaststreamAppWithTestGroup(tests: List[PsiTest]) = {
      val phase2 = Phase2TestProfileExamples.profile.copy(tests = tests)
      ApplicationPhase1EvaluationExamples.sdipFaststreamApplication.copy(activePsiTests = phase2.activeTests)
    }
  }
}
