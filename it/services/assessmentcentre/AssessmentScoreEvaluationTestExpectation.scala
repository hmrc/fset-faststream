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
                                                     makingEffectiveDecisionsAverage: Option[Double],
                                                     workingTogetherDevelopingSelfAndOthersAverage: Option[Double],
                                                     communicatingAndInfluencingAverage: Option[Double],
                                                     seeingTheBigPictureAverage: Option[Double],
                                                     overallScore: Option[Double],
                                                     schemesEvaluation: Option[String]
) {

  def competencyAverage: Option[CompetencyAverageResult] = {
    val allResults = List(makingEffectiveDecisionsAverage, workingTogetherDevelopingSelfAndOthersAverage,
      communicatingAndInfluencingAverage, seeingTheBigPictureAverage, overallScore)

    val data = s"makingEffectiveDecisionsAverage=$makingEffectiveDecisionsAverage, " +
      s"workingTogetherDevelopingSelfAndOthersAverage=$workingTogetherDevelopingSelfAndOthersAverage, " +
      s"communicatingAndInfluencingAverage=$communicatingAndInfluencingAverage, " +
      s"seeingTheBigPictureAverage=$seeingTheBigPictureAverage, " +
      s"overallScore=$overallScore"
    require(allResults.forall(_.isDefined) || allResults.forall(_.isEmpty), s"all competencies or none of them must be defined - $data")

    if (allResults.forall(_.isDefined)) {
      Some(CompetencyAverageResult(
        makingEffectiveDecisionsAverage.get,
        workingTogetherDevelopingSelfAndOthersAverage.get,
        communicatingAndInfluencingAverage.get,
        seeingTheBigPictureAverage.get,
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
