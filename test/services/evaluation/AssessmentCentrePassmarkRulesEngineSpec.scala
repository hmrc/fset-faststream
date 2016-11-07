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

package services.evaluation

import config.AssessmentEvaluationMinimumCompetencyLevel
import model.AssessmentEvaluationCommands.AssessmentPassmarkPreferencesAndScores
import model.CandidateScoresCommands.{ CandidateScores, CandidateScoresAndFeedback }
import model.Commands.AssessmentCentrePassMarkSettingsResponse
import model.EvaluationResults._
import model.PassmarkPersistedObjects.{ AssessmentCentrePassMarkInfo, AssessmentCentrePassMarkScheme, PassMarkSchemeThreshold }
import model.{ Alternatives, LocationPreference, Preferences }
import org.joda.time.DateTime
import org.scalatest.MustMatchers
import org.scalatestplus.play.PlaySpec

class AssessmentCentrePassmarkRulesEngineSpec extends PlaySpec with MustMatchers {

  val rulesEngine = AssessmentCentrePassmarkRulesEngine

  "Assessment Centre Passmark Rules engine evaluation" should {
    val PassmarkSettings = AssessmentCentrePassMarkSettingsResponse(List(
      AssessmentCentrePassMarkScheme("Business", Some(PassMarkSchemeThreshold(1.0, 32.0))),
      AssessmentCentrePassMarkScheme("Commercial", Some(PassMarkSchemeThreshold(5.0, 30.0))),
      AssessmentCentrePassMarkScheme("Digital and technology", Some(PassMarkSchemeThreshold(27.0, 30.0))),
      AssessmentCentrePassMarkScheme("Finance", Some(PassMarkSchemeThreshold(12.0, 19.0))),
      AssessmentCentrePassMarkScheme("Project delivery", Some(PassMarkSchemeThreshold(23.0, 30.0)))
    ), Some(AssessmentCentrePassMarkInfo("1", DateTime.now, "user")))

    val CandidateScoresWithFeedback = CandidateScoresAndFeedback("app1", Some(true), assessmentIncomplete = false,
      CandidateScores(Some(2.1), Some(3.4), Some(3.3)),
      CandidateScores(None, Some(2.0), Some(3.0)),
      CandidateScores(Some(4.0), None, Some(3.0)),
      CandidateScores(None, Some(3.0), Some(4.0)),
      CandidateScores(Some(4.0), None, Some(4.0)),
      CandidateScores(Some(4.0), Some(4.0), None),
      CandidateScores(Some(2.0), Some(4.0), None))
    val preferences = Preferences(
      LocationPreference("London", "London", "Business", Some("Commercial")),
      Some(LocationPreference("London", "Reading", "Finance", Some("Project delivery"))),
      alternatives = Some(Alternatives(location = true, framework = true))
    )

    "evalute to passedMinimumCompetencyLevel=false when minimum competency level is enabled and not met" in {
      val config = AssessmentEvaluationMinimumCompetencyLevel(enabled = true, Some(2.0), Some(4.0))
      val scores = CandidateScoresWithFeedback.copy(collaboratingAndPartnering = CandidateScores(None, Some(1.0), Some(2.0)))
      val candidateScore = AssessmentPassmarkPreferencesAndScores(PassmarkSettings, preferences, scores)

      val result = rulesEngine.evaluate(candidateScore, config)

      result must be(AssessmentRuleCategoryResult(Some(false), None, None, None, None, None, None, None))
    }

    "evalute to passedMinimumCompetencyLevel=true and evaluate preferred locations" in {
      val config = AssessmentEvaluationMinimumCompetencyLevel(enabled = true, Some(2.0), Some(4.0))
      val scores = CandidateScoresWithFeedback
      val candidateScore = AssessmentPassmarkPreferencesAndScores(PassmarkSettings, preferences, scores)

      val result = rulesEngine.evaluate(candidateScore, config)

      val ExpectedSchemeEvaluation = List(
        PerSchemeEvaluation("Business", Amber),
        PerSchemeEvaluation("Commercial", Amber),
        PerSchemeEvaluation("Digital and technology", Red),
        PerSchemeEvaluation("Finance", Green),
        PerSchemeEvaluation("Project delivery", Amber)
      )

      val expectedResult = AssessmentRuleCategoryResult(Some(true), Some(Amber), Some(Amber), Some(Green), Some(Amber), Some(Red),
        Some(CompetencyAverageResult(2.9333333333333336, 2.5, 3.5, 3.5, 4.0, 4.0, 6.0, 26.433333333333334)), Some(ExpectedSchemeEvaluation))

      result.passedMinimumCompetencyLevel must be(expectedResult.passedMinimumCompetencyLevel)
      result.location1Scheme1 must be(expectedResult.location1Scheme1)
      result.location1Scheme2 must be(expectedResult.location1Scheme2)
      result.location2Scheme1 must be(expectedResult.location2Scheme1)
      result.location2Scheme2 must be(expectedResult.location2Scheme2)
      result.alternativeScheme must be(expectedResult.alternativeScheme)
      result.competencyAverageResult must be(expectedResult.competencyAverageResult)
      result.schemesEvaluation must be(expectedResult.schemesEvaluation)
    }
  }
}
