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

import model.CandidateScoresCommands.{ CandidateScores, CandidateScoresAndFeedback }
import model.EvaluationResults.CompetencyAverageResult
import org.scalatest.MustMatchers
import org.scalatestplus.play.PlaySpec

class AssessmentScoreCalculatorSpec extends PlaySpec with MustMatchers {

  val calculator = new AssessmentScoreCalculator {}

  "Assessment Score Calculator" should {
    "count scores" in {
      val Scores = CandidateScoresAndFeedback("appId", None, assessmentIncomplete = false,
        CandidateScores(Some(4), Some(4), Some(4)), //12     4.00

        CandidateScores(Some(3.25), None, Some(3.98)), //7.23   3.615
        CandidateScores(Some(3.05), None, Some(2.98)), //6.03   3.015
        CandidateScores(None, Some(3.12), Some(3.66)), //6.78   3.39
        CandidateScores(Some(3.1), None, Some(3.09)), //6.19   3.095
        CandidateScores(Some(3.98), Some(3.99), None), //7.97   3.985

        CandidateScores(Some(2.99), Some(2.76), None) //5.75   5.75 (doubled)
      //total: 26.85
      )

      val result = calculator.countScores(Scores)

      result must be(CompetencyAverageResult(4.00, 3.615, 3.015, 3.39, 3.095, 3.985, 5.75, 26.85))
    }
  }
}
