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

package scheduler.onlinetesting

import model._
import model.exchange.passmarksettings.{ Phase2PassMarkSettings, Phase2PassMarkSettingsExamples }
import model.persisted.ApplicationReadyForEvaluation2
import org.joda.time.{ DateTime, DateTimeZone }
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json.Format
import play.modules.reactivemongo.ReactiveMongoComponent
import testkit.UnitWithAppSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.concurrent.{ ExecutionContext, Future }

class EvaluatePhase2ResultJobSpec extends UnitWithAppSpec {
  implicit val now: DateTime = DateTime.now().withZone(DateTimeZone.UTC)

  "Scheduler execution" should {
    "evaluate applications ready for evaluation" in new TestFixture {
      when(mockEvaluateService2.nextCandidatesReadyForEvaluation(any[Int])(any[Format[Phase2PassMarkSettings]]))
        .thenReturn(Future.successful(Some((apps.toList, passmark))))

      scheduler.tryExecute().futureValue

      assertAllApplicationsWereEvaluated(apps)
    }

    "evaluate all applications even when some of them fail" in new TestFixture {
      when(mockEvaluateService2.nextCandidatesReadyForEvaluation(any[Int])(any[Format[Phase2PassMarkSettings]]))
        .thenReturn(Future.successful(Some((apps.toList, passmark))))
      when(mockEvaluateService2.evaluate(apps(0), passmark)).thenReturn(Future.failed(new IllegalStateException("first application fails")))
      when(mockEvaluateService2.evaluate(apps(5), passmark)).thenReturn(Future.failed(new Exception("fifth application fails")))

      val exception = scheduler.tryExecute().failed.futureValue

      assertAllApplicationsWereEvaluated(apps)
      exception mustBe a[IllegalStateException]
    }

    "do not evaluate candidate if none of them are ready to evaluate" in new TestFixture {
      when(mockEvaluateService2.nextCandidatesReadyForEvaluation(any[Int])(any[Format[Phase2PassMarkSettings]]))
        .thenReturn(Future.successful(None))
      scheduler.tryExecute().futureValue

      verify(mockEvaluateService2, never).evaluate(any[ApplicationReadyForEvaluation2], any[Phase2PassMarkSettings])
    }
  }

  trait TestFixture {
    val mockEvaluateService2 = mock[EvaluateOnlineTestResultService2[Phase2PassMarkSettings]]
    val profile2 = Phase2TestProfileExamples.profile2
    val schemes = SelectedSchemesExamples.TwoSchemes
    val passmark = Phase2PassMarkSettingsExamples.passmark

    val apps = 1 to 10 map { id =>
      ApplicationReadyForEvaluation2(s"app$id", ApplicationStatus.PHASE2_TESTS, ApplicationRoute.Faststream, isGis = false,
        profile2.activeTests, None, None, schemes)
    }

    apps.foreach { app =>
      when(mockEvaluateService2.evaluate(app, passmark)).thenReturn(Future.successful(()))
    }

    lazy val scheduler = new EvaluateOnlineTestResultJob[Phase2PassMarkSettings] {
      val phase = Phase.PHASE2
//      val evaluateService = mockEvaluateService
      val evaluateService2 = mockEvaluateService2
      override val mongoComponent = mock[ReactiveMongoComponent]
      override lazy val batchSize = 1
      override val lockId = "1"
      override val forceLockReleaseAfter: Duration = mock[Duration]
      override implicit val ec: ExecutionContext = mock[ExecutionContext]
      override val name = "test"
      override val initialDelay: FiniteDuration = mock[FiniteDuration]
      override val interval: FiniteDuration = mock[FiniteDuration]
      // Must be a def instead of val as val is called immediately, whereas the def is only called when needed
      def config = ???
    }

    def assertAllApplicationsWereEvaluated(apps: Seq[ApplicationReadyForEvaluation2]) = apps foreach { app =>
      verify(mockEvaluateService2).evaluate(app, passmark)
    }
  }
}
