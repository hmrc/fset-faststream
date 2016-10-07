package services.onlinetesting

import _root_.config.CubiksGatewayConfig
import _root_.services.BaseServiceSpec
import model.exchange.passmarksettings.Phase1PassMarkSettingsExamples
import model.persisted.ApplicationPhase1EvaluationExamples
import org.mockito.Mockito._
import repositories._
import repositories.onlinetesting.Phase1EvaluationRepository

import scala.concurrent.Future

class EvaluatePhase1ResultServiceSpec extends BaseServiceSpec {
  "next candidate ready for evaluation" should {
    "return none if passmark is not set" in new TestFixture {
      when(mockPhase1PMSRepository.getLatestVersion).thenReturn(Future.successful(None))
      val result = service.nextCandidateReadyForEvaluation.futureValue
      result mustBe None
    }

    "return none if application for evaluation does not exist" in new TestFixture {
      val passmark = Phase1PassMarkSettingsExamples.passmark
      val application = ApplicationPhase1EvaluationExamples.application

      when(mockPhase1PMSRepository.getLatestVersion).thenReturn(Future.successful(Some(passmark)))
      when(mockPhase1EvaluationRepository
        .nextApplicationReadyForPhase1ResultEvaluation(passmark.version))
        .thenReturn(Future.successful(Some(application)))

      val result = service.nextCandidateReadyForEvaluation.futureValue

      result mustBe Some((application, passmark))
    }
  }

  trait TestFixture {
    val mockPhase1EvaluationRepository = mock[Phase1EvaluationRepository]
    val mockCubiksGatewayConfig = mock[CubiksGatewayConfig]
    val mockPhase1PMSRepository = mock[Phase1PassMarkSettingsRepository]

    val service = new EvaluatePhase1ResultService {
      val phase1EvaluationRepository = mockPhase1EvaluationRepository
      val gatewayConfig = mockCubiksGatewayConfig
      val phase1PMSRepository = mockPhase1PMSRepository
    }
  }

}
