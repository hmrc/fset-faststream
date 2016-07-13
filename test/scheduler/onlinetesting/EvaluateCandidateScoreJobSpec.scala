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

import connectors.PassMarkExchangeObjects.Settings
import model.OnlineTestCommands.CandidateScoresWithPreferencesAndPassmarkSettings
import model.PersistedObjects.CandidateTestReport
import model.{ApplicationStatuses, LocationPreference, Preferences}
import org.joda.time.DateTime
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.WithApplication
import services.onlinetesting.OnlineTestPassmarkService
import testkit.ShortTimeout

import scala.concurrent.{ExecutionContext, Future}

class EvaluateCandidateScoreJobSpec extends PlaySpec with MockitoSugar with ScalaFutures with ShortTimeout {
  implicit val ec: ExecutionContext = ExecutionContext.global
  import EvaluateCandidateScoreJobSpec._

  val serviceMock = mock[OnlineTestPassmarkService]

  object TestableEvaluateCandidateScoreJob extends EvaluateCandidateScoreJob {
    val passmarkService = serviceMock
  }

  "evaluate candidate score job" should {
    "evaluate the score successfully for ONLINE_TEST_COMPLETED" in new WithApplication {
      when(serviceMock.nextCandidateScoreReadyForEvaluation).thenReturn(
        Future.successful(Some(OnlineTestCompletedCandidateScore))
      )
      when(serviceMock.evaluateCandidateScore(OnlineTestCompletedCandidateScore)).thenReturn(Future.successful(()))

      TestableEvaluateCandidateScoreJob.tryExecute().futureValue

      verify(serviceMock).evaluateCandidateScore(OnlineTestCompletedCandidateScore)
    }

    "evaluate the score without changing the application status for ASSESSMENT_SCORES_ACCEPTED" in new WithApplication {
      when(serviceMock.nextCandidateScoreReadyForEvaluation).thenReturn(
        Future.successful(Some(AssessmentScoresAcceptedCandidateScore))
      )
      when(serviceMock.evaluateCandidateScoreWithoutChangingApplicationStatus(AssessmentScoresAcceptedCandidateScore))
        .thenReturn(Future.successful(()))

      TestableEvaluateCandidateScoreJob.tryExecute().futureValue

      verify(serviceMock).evaluateCandidateScoreWithoutChangingApplicationStatus(AssessmentScoresAcceptedCandidateScore)
    }
  }
}

object EvaluateCandidateScoreJobSpec {

  val OnlineTestCompletedCandidateScore = CandidateScoresWithPreferencesAndPassmarkSettings(
    Settings(List(), "verison", DateTime.now(), "user", "version1"),
    Preferences(LocationPreference("region", "location", "framework", None)),
    CandidateTestReport("appId", "type"),
    ApplicationStatuses.OnlineTestCompleted
  )

  val AssessmentScoresAcceptedCandidateScore = OnlineTestCompletedCandidateScore.copy(
    applicationStatus = ApplicationStatuses.AssessmentScoresAccepted
  )
}
