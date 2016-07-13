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

trait AssessmentScoreCalculator {

  def countScores(scores: CandidateScoresAndFeedback): CompetencyAverageResult = {
    val leadingAndCommunicating = average(scores.leadingAndCommunicating)
    val collaboratingAndPartnering = average(scores.collaboratingAndPartnering)
    val deliveringAtPace = average(scores.deliveringAtPace)
    val makingEffectiveDecisions = average(scores.makingEffectiveDecisions)
    val changingAndImproving = average(scores.changingAndImproving)
    val buildingCapabilityForAll = average(scores.buildingCapabilityForAll)

    val motivationFit = scores.motivationFit.sum

    val overallScores = List(leadingAndCommunicating, collaboratingAndPartnering,
      deliveringAtPace, makingEffectiveDecisions, changingAndImproving,
      buildingCapabilityForAll, motivationFit).map(BigDecimal(_)).sum.toDouble

    CompetencyAverageResult(leadingAndCommunicating, collaboratingAndPartnering, deliveringAtPace, makingEffectiveDecisions,
      changingAndImproving, buildingCapabilityForAll, motivationFit, overallScores)
  }

  private def average(scores: CandidateScores) = scores.sum / scores.length

  private def countOverallScore(scores: CompetencyAverageResult): Double =
    (scores.scoresWithWeightOne.map(BigDecimal(_)).sum + scores.scoresWithWeightTwo.map(BigDecimal(_)).sum).toDouble
}
