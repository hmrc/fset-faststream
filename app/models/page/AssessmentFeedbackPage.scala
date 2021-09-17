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

package models.page

import connectors.exchange.candidatescores.{ AssessmentScoresAllExercises, CompetencyAverageResult }

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
  val seeingTheBigPictureCompetency = "Seeing the Big Picture/Changing and Improving"
  val makingEffectiveDecisionsCompetency = "Making Effective Decisions"
  val communicatingAndInfluencingCompetency = "Communicating and Influencing"
  val workingTogetherDevelopingSelfAndOthersCompetency = "Working Together/Developing Self and Others"

  def apply(assessmentScores: AssessmentScoresAllExercises, evaluatedAverageResults: CompetencyAverageResult,
    candidateName: String): AssessmentFeedbackPage = {
    val analysisExercise = ExerciseFeedback("Written scenario",
      Seq(
        CompetencyFeedback(seeingTheBigPictureCompetency,
          assessmentScores.writtenExercise.flatMap{ s => s.seeingTheBigPictureFeedback}.getOrElse("")),
        CompetencyFeedback(makingEffectiveDecisionsCompetency,
          assessmentScores.writtenExercise.flatMap{ s => s.makingEffectiveDecisionsFeedback}.getOrElse("")),
        CompetencyFeedback(communicatingAndInfluencingCompetency,
          assessmentScores.writtenExercise.flatMap{ s => s.communicatingAndInfluencingFeedback}.getOrElse(""))
      )
    )
    val groupExercise = ExerciseFeedback("Team scenario",
      Seq(
        CompetencyFeedback(makingEffectiveDecisionsCompetency,
          assessmentScores.teamExercise.flatMap{ s => s.makingEffectiveDecisionsFeedback}.getOrElse("")),
        CompetencyFeedback(workingTogetherDevelopingSelfAndOthersCompetency,
          assessmentScores.teamExercise.flatMap{ s => s.workingTogetherDevelopingSelfAndOthersFeedback}.getOrElse("")),
        CompetencyFeedback(communicatingAndInfluencingCompetency,
          assessmentScores.teamExercise.flatMap{ s => s.communicatingAndInfluencingFeedback}.getOrElse(""))
      )
    )
    val leadershipExercise = ExerciseFeedback("Leadership scenario",
      Seq(
        CompetencyFeedback(workingTogetherDevelopingSelfAndOthersCompetency,
          assessmentScores.leadershipExercise.flatMap{ s => s.workingTogetherDevelopingSelfAndOthersFeedback}.getOrElse("")),
        CompetencyFeedback(communicatingAndInfluencingCompetency,
          assessmentScores.leadershipExercise.flatMap{ s => s.communicatingAndInfluencingFeedback}.getOrElse("")),
        CompetencyFeedback(seeingTheBigPictureCompetency,
          assessmentScores.leadershipExercise.flatMap{ s => s.seeingTheBigPictureFeedback}.getOrElse(""))
      )
    )
    val finalFeedback = assessmentScores.finalFeedback.map { s => s.feedback }.getOrElse("")
    AssessmentFeedbackPage(Seq(analysisExercise, groupExercise, leadershipExercise), finalFeedback, evaluatedAverageResults, candidateName)
  }
}
