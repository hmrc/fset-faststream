/*
 * Copyright 2021 HM Revenue & Customs
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

package services.onlinetesting.phase3

import config._
import factories.UUIDFactory
import model.EvaluationResults.Green
import model.Phase3TestProfileExamples._
import model.ProgressStatuses.ProgressStatus
import model._
import model.exchange.passmarksettings.Phase3PassMarkSettingsExamples
import model.persisted._
import model.persisted.phase3tests.{ LaunchpadTest, LaunchpadTestCallbacks }
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.OnlineTestEvaluationRepository
import repositories.passmarksettings.Phase3PassMarkSettingsMongoRepository
import services.BaseServiceSpec

import scala.concurrent.Future

class EvaluatePhase3ResultServiceSpec extends BaseServiceSpec {

  "evaluate candidate" should {
    "throw an exception if there are no active tests" in new TestFixture {
      val thrown = intercept[IllegalArgumentException] {
        val application = createApplication(None).copy(applicationStatus = ApplicationStatus.PHASE2_TESTS_PASSED)
        service.evaluate(application, passmarkSettings).futureValue
      }
      thrown.getMessage mustBe "requirement failed: Active launchpad test not found"
    }

    "throw an exception if there is no previous phase evaluation" in new TestFixture {
      val thrown = intercept[IllegalArgumentException] {
        val application = createApplication(Some(launchPadTest)).copy(applicationStatus = ApplicationStatus.PHASE2_TESTS_PASSED)
        service.evaluate(application, passmarkSettings).futureValue
      }
      thrown.getMessage mustBe "requirement failed: Phase2 results required to evaluate Phase3"
    }

    "evaluate the expected schemes when processing a faststream candidate" in new TestFixture {
      val application = createApplication(
        Some(launchPadTest.copy(callbacks = LaunchpadTestCallbacks(reviewed = List(sampleReviewedCallback(Some(30.0))))))
      ).copy(applicationStatus = ApplicationStatus.PHASE2_TESTS_PASSED, prevPhaseEvaluation = previousPhaseEvaluation)
      service.evaluate(application, passmarkSettings).futureValue

      val applicationIdCaptor = ArgumentCaptor.forClass(classOf[String])
      val passmarkEvaluationCaptor = ArgumentCaptor.forClass(classOf[PassmarkEvaluation])
      val progressStatusCaptor = ArgumentCaptor.forClass(classOf[Option[ProgressStatus]])

      verify(mockPhase3EvaluationRepository).savePassmarkEvaluation(applicationIdCaptor.capture, passmarkEvaluationCaptor.capture,
        progressStatusCaptor.capture)

      applicationIdCaptor.getValue.toString mustBe appId
      val expected = List(SchemeEvaluationResult(
        SchemeId(digitalDataTechnologyAndCyber), Green.toString),
        SchemeEvaluationResult(SchemeId(commercial), Green.toString)
      )
      passmarkEvaluationCaptor.getValue.result mustBe expected
      progressStatusCaptor.getValue mustBe None
    }

    "include sdip evaluation read from current scheme status when saving evaluation for sdip faststream candidate" in new TestFixture {
      val application = createSdipFaststreamApplication(
        Some(launchPadTest.copy(callbacks = LaunchpadTestCallbacks(reviewed = List(sampleReviewedCallback(Some(30.0))))))
      ).copy(applicationStatus = ApplicationStatus.PHASE2_TESTS_PASSED, prevPhaseEvaluation = previousPhaseEvaluation)

      when(mockApplicationRepository.getCurrentSchemeStatus(eqTo(appId))).thenReturn(
        Future.successful(Seq(SchemeEvaluationResult(SchemeId(sdip), Green.toString))))

      service.evaluate(application, passmarkSettings).futureValue

      val applicationIdCaptor = ArgumentCaptor.forClass(classOf[String])
      val passmarkEvaluationCaptor = ArgumentCaptor.forClass(classOf[PassmarkEvaluation])
      val progressStatusCaptor = ArgumentCaptor.forClass(classOf[Option[ProgressStatus]])

      verify(mockPhase3EvaluationRepository).savePassmarkEvaluation(applicationIdCaptor.capture, passmarkEvaluationCaptor.capture,
        progressStatusCaptor.capture)

      applicationIdCaptor.getValue.toString mustBe appId
      val expectedEvaluation = List(SchemeEvaluationResult(
        SchemeId(digitalDataTechnologyAndCyber),Green.toString),
        SchemeEvaluationResult(SchemeId(commercial), Green.toString),
        SchemeEvaluationResult(SchemeId(sdip), Green.toString)
      )
      passmarkEvaluationCaptor.getValue.result mustBe expectedEvaluation
      progressStatusCaptor.getValue mustBe None
    }
  }

  trait TestFixture {
    val appId = ApplicationPhase1EvaluationExamples.faststreamApplication.applicationId
    val sdip = "Sdip"
    val commercial = "Commercial"
    val digitalDataTechnologyAndCyber = "DigitalDataTechnologyAndCyber"
    val passmarkSettings = Phase3PassMarkSettingsExamples.passMarkSettings(List(
      (SchemeId(commercial), 10.0, 20.0),
      (SchemeId(digitalDataTechnologyAndCyber), 10.0, 20.0)
    ))

    val mockPhase3EvaluationRepository = mock[OnlineTestEvaluationRepository]
    val mockPhase3PassMarkSettingsRepository = mock[Phase3PassMarkSettingsMongoRepository]

    when(mockPhase3EvaluationRepository.savePassmarkEvaluation(eqTo(appId), any[PassmarkEvaluation], any[Option[ProgressStatus]]))
      .thenReturn(Future.successful(()))

    val mockApplicationRepository = mock[GeneralApplicationRepository]
    when(mockApplicationRepository.getCurrentSchemeStatus(eqTo(appId))).thenReturn(Future.successful(Nil))

    val previousPhaseEvaluation = Some(
      PassmarkEvaluation(
        passmarkVersion = "v2",
        previousPhasePassMarkVersion = Some("v1"),
        result = List(SchemeEvaluationResult(SchemeId(commercial), Green.toString),
          SchemeEvaluationResult(SchemeId(digitalDataTechnologyAndCyber), Green.toString)),
        resultVersion = "res-v2",
        previousPhaseResultVersion = Some("res-v1"))
    )

    val mocklaunchpadGWConfig = mock[LaunchpadGatewayConfig]
    when(mocklaunchpadGWConfig.phase3Tests).thenReturn(
      Phase3TestsConfig(timeToExpireInDays = 5,
        invigilatedTimeToExpireInDays = 10,
        gracePeriodInSecs = 0,
        candidateCompletionRedirectUrl = "http://someurl",
        interviewsByAdjustmentPercentage = Map.empty[String, Int],
        evaluationWaitTimeAfterResultsReceivedInHours = 0,
        verifyAllScoresArePresent = true)
    )

    val mockAppConfig = mock[MicroserviceAppConfig]
    when(mockAppConfig.launchpadGatewayConfig).thenReturn(mocklaunchpadGWConfig)

    val service = new EvaluatePhase3ResultService(
      mockPhase3EvaluationRepository,
      mockPhase3PassMarkSettingsRepository,
      mockApplicationRepository,
      mockAppConfig,
      UUIDFactory
    ) {
      override val phase = Phase.PHASE2 //TODO:fix is p2 correct here?
    }

    def createApplication(test: Option[LaunchpadTest]) = {
      ApplicationPhase2EvaluationExamples.faststreamApplication.copy(activeLaunchpadTest = test)
    }

    def createSdipFaststreamApplication(test: Option[LaunchpadTest]) = {
      ApplicationPhase2EvaluationExamples.sdipFaststreamApplication.copy(activeLaunchpadTest = test)
    }
  }
}
