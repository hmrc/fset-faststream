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

package scheduler.assessment

import config.AssessmentEvaluationMinimumCompetencyLevel
import model.AssessmentEvaluationCommands.AssessmentPassmarkPreferencesAndScores
import model.CandidateScoresCommands.CandidateScoresAndFeedback
import model.Commands.AssessmentCentrePassMarkSettingsResponse
import model.{ LocationPreference, Preferences }
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.WithApplication
import services.applicationassessment.ApplicationAssessmentService
import testkit.ShortTimeout

import scala.concurrent.{ ExecutionContext, Future }

class EvaluateAssessmentScoreJobSpec extends PlaySpec with MockitoSugar with ScalaFutures with ShortTimeout {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val applicationAssessmentServiceMock = mock[ApplicationAssessmentService]
  val config = AssessmentEvaluationMinimumCompetencyLevel(false, None, None)

  object TestableEvaluateAssessmentScoreJob extends EvaluateAssessmentScoreJob {
    val applicationAssessmentService = applicationAssessmentServiceMock
    override val minimumCompetencyLevelConfig = config
  }

  "application assessment service" should {
    "find a candidate and evaluate the score successfully" in new WithApplication {
      val candidateScore = AssessmentPassmarkPreferencesAndScores(
        AssessmentCentrePassMarkSettingsResponse(List(), None),
        Preferences(LocationPreference("region", "location", "firstFramework", None)),
        CandidateScoresAndFeedback("appId", None, false)
      )
      when(applicationAssessmentServiceMock.nextAssessmentCandidateScoreReadyForEvaluation).thenReturn(
        Future.successful(Some(candidateScore))
      )
      when(applicationAssessmentServiceMock.evaluateAssessmentCandidateScore(candidateScore, config)).thenReturn(
        Future.successful(())
      )

      TestableEvaluateAssessmentScoreJob.tryExecute().futureValue

      verify(applicationAssessmentServiceMock).evaluateAssessmentCandidateScore(candidateScore, config)
    }
  }
}
