/*
 * Copyright 2019 HM Revenue & Customs
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

package models.page

import connectors.exchange.candidatescores.{AssessmentScoresAllExercises, CompetencyAverageResult}

case class ExerciseFeedback(exerciseName: String, competencyFeedback: Seq[CompetencyFeedback])
case class CompetencyFeedback(competencyName: String, feedback: String)

case class AssessmentFeedbackPage(
  exerciseFeedbackData: Seq[ExerciseFeedback],
  finalFeedback: String,
  evaluatedAverageResults: CompetencyAverageResult,
  candidateName: String
) {
  def formatScore(score: Double): String = "%.2f".format(score)
}

case object AssessmentFeedbackPage {
  def apply(assessmentScores: AssessmentScoresAllExercises, evaluatedAverageResults: CompetencyAverageResult,
    candidateName: String): AssessmentFeedbackPage = {
    val analysisExercise = ExerciseFeedback("Analysis exercise",
      Seq(
        CompetencyFeedback("Strategic Approach to Objectives",
          assessmentScores.analysisExercise.flatMap{ s => s.strategicApproachToObjectivesFeedback}.getOrElse("")),
        CompetencyFeedback("Analysis and Decision-making",
          assessmentScores.analysisExercise.flatMap{ s => s.analysisAndDecisionMakingFeedback}.getOrElse("")),
        CompetencyFeedback("Leading and Communicating",
          assessmentScores.analysisExercise.flatMap{ s => s.leadingAndCommunicatingFeedback}.getOrElse(""))
      )
    )
    val groupExercise = ExerciseFeedback("Group exercise",
      Seq(
        CompetencyFeedback("Analysis and Decision-making",
          assessmentScores.groupExercise.flatMap{ s => s.analysisAndDecisionMakingFeedback}.getOrElse("")),
        CompetencyFeedback("Building Productive Relationships",
          assessmentScores.groupExercise.flatMap{ s => s.buildingProductiveRelationshipsFeedback}.getOrElse("")),
        CompetencyFeedback("Leading and Communicating",
          assessmentScores.groupExercise.flatMap{ s => s.leadingAndCommunicatingFeedback}.getOrElse(""))
      )
    )
    val leadershipExercise = ExerciseFeedback("Leadership exercise",
      Seq(
        CompetencyFeedback("Building Productive Relationships",
          assessmentScores.leadershipExercise.flatMap{ s => s.buildingProductiveRelationshipsFeedback}.getOrElse("")),
        CompetencyFeedback("Leading and Communicating",
          assessmentScores.leadershipExercise.flatMap{ s => s.leadingAndCommunicatingFeedback}.getOrElse("")),
        CompetencyFeedback("Strategic Approach to Objectives",
          assessmentScores.leadershipExercise.flatMap{ s => s.strategicApproachToObjectivesFeedback}.getOrElse(""))
      )
    )
    val finalFeedback = assessmentScores.finalFeedback.map{ s => s.feedback}.getOrElse("")
    AssessmentFeedbackPage(Seq(analysisExercise, groupExercise, leadershipExercise), finalFeedback, evaluatedAverageResults, candidateName)
  }
}
