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

package scheduler.onlinetesting

import model.EvaluationResults.Green
import model.exchange.passmarksettings.{Phase1PassMarkSettingsExamples, Phase1PassMarkSettingsPersistence}
import model.persisted.{ApplicationReadyForEvaluation, SchemeEvaluationResult}
import model._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json.Format
import uk.gov.hmrc.mongo.MongoComponent
import testkit.UnitWithAppSpec

import java.time.{OffsetDateTime, ZoneId}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}

class EvaluatePhase1ResultJobSpec extends UnitWithAppSpec {
  implicit val now: OffsetDateTime = OffsetDateTime.now(ZoneId.of("UTC"))

  "Scheduler execution" should {
    "evaluate applications ready for evaluation" in new TestFixture {
      when(mockEvaluateService.nextCandidatesReadyForEvaluation(any[Int])(any[Format[Phase1PassMarkSettingsPersistence]], any[ExecutionContext]))
        .thenReturn(Future.successful(Some((apps.toList, passmark))))

      scheduler.tryExecute().futureValue

      assertAllApplicationsWereEvaluated(apps)
    }

    "evaluate all applications even when some of them fail" in new TestFixture {
      when(mockEvaluateService.nextCandidatesReadyForEvaluation(any[Int])(any[Format[Phase1PassMarkSettingsPersistence]], any[ExecutionContext]))
        .thenReturn(Future.successful(Some((apps.toList, passmark))))
      when(mockEvaluateService.evaluate(apps(0), passmark)).thenReturn(Future.failed(new IllegalStateException("first application fails")))
      when(mockEvaluateService.evaluate(apps(5), passmark)).thenReturn(Future.failed(new Exception("fifth application fails")))

      val exception = scheduler.tryExecute().failed.futureValue

      assertAllApplicationsWereEvaluated(apps)
      exception mustBe a[IllegalStateException]
    }

    "do not evaluate candidate if none of them are ready to evaluate" in new TestFixture {
      when(mockEvaluateService.nextCandidatesReadyForEvaluation(any[Int])(any[Format[Phase1PassMarkSettingsPersistence]], any[ExecutionContext]))
        .thenReturn(Future.successful(None))
      scheduler.tryExecute().futureValue

      verify(mockEvaluateService, never).evaluate(any[ApplicationReadyForEvaluation], any[Phase1PassMarkSettingsPersistence])
    }
  }

  trait TestFixture {
    val mockEvaluateService = mock[EvaluateOnlineTestResultService[Phase1PassMarkSettingsPersistence]]
    val profile = Phase1TestProfileExamples.psiProfile
    val schemes = SelectedSchemesExamples.twoSchemes
    val css = List(
      SchemeEvaluationResult(SelectedSchemesExamples.scheme1, Green.toString),
      SchemeEvaluationResult(SelectedSchemesExamples.scheme2, Green.toString)
    )
    val passmark = Phase1PassMarkSettingsExamples.passmark

    val apps = (1 to 10 map { id =>
      ApplicationReadyForEvaluation(s"app$id", ApplicationStatus.PHASE1_TESTS, ApplicationRoute.Faststream, isGis = false,
        profile.activeTests, None, None, schemes, css)
    }) ++ (1 to 10 map { id =>
      ApplicationReadyForEvaluation(s"edipAppId$id", ApplicationStatus.PHASE1_TESTS, ApplicationRoute.Edip, isGis = false,
        profile.activeTests, None, None, schemes, css)
    })

    apps.foreach { app =>
      when(mockEvaluateService.evaluate(app, passmark)).thenReturn(Future.successful(()))
    }

    lazy val scheduler = new EvaluateOnlineTestResultJob[Phase1PassMarkSettingsPersistence] {
      def config = ???
      val phase = Phase.PHASE1
      val evaluateService = mockEvaluateService
      override val mongoComponent = mock[MongoComponent]
      override lazy val batchSize = 1
      override val lockId: String = "1"
      override val forceLockReleaseAfter: Duration = mock[Duration]
      override implicit val ec: ExecutionContext = mock[ExecutionContext]
      override val name: String = "test"
      override val initialDelay: FiniteDuration = mock[FiniteDuration]
      override val interval: FiniteDuration = mock[FiniteDuration]
    }

    def assertAllApplicationsWereEvaluated(apps: Seq[ApplicationReadyForEvaluation]) = apps foreach { app =>
      verify(mockEvaluateService).evaluate(app, passmark)
    }
  }
}
