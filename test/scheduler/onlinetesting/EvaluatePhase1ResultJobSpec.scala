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

package scheduler.onlinetesting

import model.exchange.passmarksettings.{ Phase1PassMarkSettings, Phase1PassMarkSettingsExamples }
import model.persisted.ApplicationPhase1ReadyForEvaluation
import model.{ ApplicationStatus, Phase1TestProfileExamples, SelectedSchemesExamples }
import org.joda.time.{ DateTime, DateTimeZone }
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.WithApplication
import services.onlinetesting.EvaluatePhase1ResultService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EvaluatePhase1ResultJobSpec extends PlaySpec with MockitoSugar with ScalaFutures {
  implicit val now: DateTime = DateTime.now().withZone(DateTimeZone.UTC)

  "Scheduler execution" should {
    "evaluate applications ready for evaluation" in new TestFixture {
      when(mockEvaluateService.nextCandidatesReadyForEvaluation(any[Int])).thenReturn(Future.successful(Some(apps.toList, passmark)))

      scheduler.tryExecute().futureValue

      assertAllApplicationsWereEvaluated(apps)
    }

    "evaluate all applications even when some of them fail" in new TestFixture {
      when(mockEvaluateService.nextCandidatesReadyForEvaluation(any[Int])).thenReturn(Future.successful(Some(apps.toList, passmark)))
      when(mockEvaluateService.evaluate(apps(0), passmark)).thenReturn(Future.failed(new IllegalStateException("first application fails")))
      when(mockEvaluateService.evaluate(apps(5), passmark)).thenReturn(Future.failed(new Exception("fifth application fails")))

      val exception = scheduler.tryExecute().failed.futureValue

      assertAllApplicationsWereEvaluated(apps)
      exception mustBe a[IllegalStateException]
    }

    "do not evaluate candidate if none of them are ready to evaluate" in new TestFixture {
      when(mockEvaluateService.nextCandidatesReadyForEvaluation(any[Int])).thenReturn(Future.successful(None))
      scheduler.tryExecute().futureValue

      verify(mockEvaluateService, never).evaluate(any[ApplicationPhase1ReadyForEvaluation], any[Phase1PassMarkSettings])
    }
  }

  trait TestFixture extends WithApplication {
    val mockEvaluateService = mock[EvaluatePhase1ResultService]
    val profile = Phase1TestProfileExamples.profile
    val schemes = SelectedSchemesExamples.TwoSchemes
    val passmark = Phase1PassMarkSettingsExamples.passmark

    val apps = 1 to 10 map { id =>
      ApplicationPhase1ReadyForEvaluation(s"app$id", ApplicationStatus.PHASE1_TESTS, isGis = false, profile, schemes)
    }

    apps.foreach { app =>
      when(mockEvaluateService.evaluate(app, passmark)).thenReturn(Future.successful())
    }

    lazy val scheduler = new EvaluatePhase1ResultJob {
      val evaluateService = mockEvaluateService
    }

    def assertAllApplicationsWereEvaluated(apps: Seq[ApplicationPhase1ReadyForEvaluation]) = apps foreach { app =>
      verify(mockEvaluateService).evaluate(app, passmark)
    }
  }

}
