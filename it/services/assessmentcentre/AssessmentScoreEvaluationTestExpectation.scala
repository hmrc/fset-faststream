package services.assessmentcentre

import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.CompetencyAverageResult
import model.persisted.SchemeEvaluationResult
import model.{ ProgressStatuses, SchemeId }

case class AssessmentScoreEvaluationTestExpectation(
  applicationStatus: Option[ApplicationStatus],
  progressStatus: Option[ProgressStatuses.ProgressStatus],
  passmarkVersion: Option[String],
  passedMinimumCompetencyLevel: Option[Boolean],
  analysisAndDecisionMakingAverage: Option[Double],
  buildingProductiveRelationshipsAverage: Option[Double],
  leadingAndCommunicatingAverage: Option[Double],
  strategicApproachToObjectivesAverage: Option[Double],
  overallScore: Option[Double],
  schemesEvaluation: Option[String]
) {

  def competencyAverage: Option[CompetencyAverageResult] = {
    val allResults = List(analysisAndDecisionMakingAverage, buildingProductiveRelationshipsAverage,
      leadingAndCommunicatingAverage, strategicApproachToObjectivesAverage, overallScore)

    require(allResults.forall(_.isDefined) || allResults.forall(_.isEmpty), "all competencies or none of them must be defined")

    if (allResults.forall(_.isDefined)) {
      Some(CompetencyAverageResult(
        analysisAndDecisionMakingAverage.get,
        buildingProductiveRelationshipsAverage.get,
        leadingAndCommunicatingAverage.get,
        strategicApproachToObjectivesAverage.get,
        overallScore.get))
    } else {
      None
    }
  }

  def allSchemesEvaluationExpectations: Option[List[SchemeEvaluationResult]] =
    schemesEvaluation.map { s =>
      s.split("\\|").map { schemeAndResult =>
        val Array(scheme, result) = schemeAndResult.split(":")
        SchemeEvaluationResult(SchemeId(scheme), result)
      }.toList
    }
}
