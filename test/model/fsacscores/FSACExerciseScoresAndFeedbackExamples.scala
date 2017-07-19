package model.fsacscores

import model.UniqueIdentifier
import org.joda.time.DateTime


object FSACExerciseScoresAndFeedbackExamples {
  val Example1 = getExample(1)
  val Example2 = getExample(2)
  val Example3 = getExample(3)

  private def getExample(baseValue: Double): FSACExerciseScoresAndFeedback = {
    val strategicScore = Some(baseValue + 0.1)
    val analysisScore = Some(baseValue + 0.1)
    val leadingScore = Some(baseValue + 0.2)
    val buildingScore = Some(baseValue + 0.3)
    val otherScore = Some(baseValue + 0.4)

    FSACExerciseScoresAndFeedback(
      Some(StrategicApproachToObjectivesScores(strategicScore, strategicScore, strategicScore, strategicScore, strategicScore)),
      Some(AnalysisAndDecisionMakingScores(analysisScore, analysisScore, analysisScore, analysisScore, analysisScore)),
      Some(LeadingAndCommunicatingScores(leadingScore, leadingScore, leadingScore, leadingScore, leadingScore)),
      Some(BuildingProductiveRelationshipsAndDevelopingCapabilityScores(buildingScore, buildingScore,
        buildingScore,buildingScore,buildingScore,buildingScore,buildingScore)),
      Some("feedback1"), Some("feedback2"), Some("feedback3"), Some("feedback4"),
      otherScore, otherScore, otherScore, otherScore, Some(DateTime.now),
      Some("version1")
    )
  }
}
