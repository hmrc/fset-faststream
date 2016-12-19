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
import services.applicationassessment.ApplicationAssessmentService
import testkit.{ ShortTimeout, UnitWithAppSpec }

import scala.concurrent.{ ExecutionContext, Future }

class EvaluateAssessmentScoreJobSpec extends UnitWithAppSpec with ShortTimeout {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val applicationAssessmentServiceMock = mock[ApplicationAssessmentService]
  val competencyConfig = AssessmentEvaluationMinimumCompetencyLevel(enabled = false, None, None)

  object TestableEvaluateAssessmentScoreJob extends EvaluateAssessmentScoreJob {
    val applicationAssessmentService = applicationAssessmentServiceMock
    val config = new EvaluateAssessmentScoreJobConfig {
      override val minimumCompetencyLevelConfig = competencyConfig
    }
  }

  "application assessment service" should {
    "find a candidate and evaluate the score successfully" in {
      val candidateScore = AssessmentPassmarkPreferencesAndScores(
        AssessmentCentrePassMarkSettingsResponse(List(), None),
        Preferences(LocationPreference("region", "location", "firstFramework", None)),
        CandidateScoresAndFeedback("appId", None, assessmentIncomplete = false)
      )
      when(applicationAssessmentServiceMock.nextAssessmentCandidateScoreReadyForEvaluation).thenReturn(
        Future.successful(Some(candidateScore))
      )
      when(applicationAssessmentServiceMock.evaluateAssessmentCandidateScore(candidateScore, competencyConfig)).thenReturn(
        Future.successful(())
      )

      TestableEvaluateAssessmentScoreJob.tryExecute().futureValue

      verify(applicationAssessmentServiceMock).evaluateAssessmentCandidateScore(candidateScore, competencyConfig)
    }
  }
}
