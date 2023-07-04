package services.assessmentcentre

import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.{CompetencyAverageResult, ExerciseAverageResult}
import model.persisted.SchemeEvaluationResult
import model.{ProgressStatuses, SchemeId}

case class AssessmentScoreEvaluationTestExpectation(
                                                     applicationStatus: Option[ApplicationStatus],
                                                     progressStatus: Option[ProgressStatuses.ProgressStatus],
                                                     passmarkVersion: Option[String],
                                                     makingEffectiveDecisionsAverage: Option[Double],
                                                     workingTogetherDevelopingSelfAndOthersAverage: Option[Double],
                                                     communicatingAndInfluencingAverage: Option[Double],
                                                     seeingTheBigPictureAverage: Option[Double],
                                                     overallScore: Option[Double],
                                                     writtenExerciseAverage: Option[Double],
                                                     teamExerciseAverage: Option[Double],
                                                     leadershipExerciseAverage: Option[Double],
                                                     exerciseOverallScore: Option[Double],
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
  def exerciseAverage: Option[ExerciseAverageResult] = {
    val allResults = List(writtenExerciseAverage, teamExerciseAverage, leadershipExerciseAverage, exerciseOverallScore)

    val data = s"writtenExerciseAverage=$writtenExerciseAverage, " +
      s"teamExerciseAverage=$teamExerciseAverage, " +
      s"leadershipExerciseAverage=$leadershipExerciseAverage, " +
      s"exerciseOverallScore=$exerciseOverallScore"

    require(allResults.forall(_.isDefined) || allResults.forall(_.isEmpty), s"all competencies or none of them must be defined - $data")

    if (allResults.forall(_.isDefined)) {
      Some(ExerciseAverageResult(
        writtenExerciseAverage.get,
        teamExerciseAverage.get,
        leadershipExerciseAverage.get,
        exerciseOverallScore.get))
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
