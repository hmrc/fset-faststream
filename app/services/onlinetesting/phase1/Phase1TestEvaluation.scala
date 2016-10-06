package services.onlinetesting.phase1

import model.EvaluationResults.{ Amber, Green, Red, Result }
import model.SchemeType._
import model.persisted.TestResult

trait Phase1TestEvaluation {

  def evaluateForGis(schemes: List[SchemeType], sjqTestResult: TestResult, passmark: Any) = {
    schemes map { scheme =>
      scheme -> evaluateResultsForExercise(sjqTestResult, scheme, passmark)
    }
  }

  def evaluateForNonGis(schemes: List[SchemeType], sjqTestResult: TestResult, bqTestResult: TestResult, passmark: Any) = {
    schemes map { scheme =>
      val sjqResult:  = evaluateResultsForExercise(sjqTestResult, scheme, passmark)
      val bqResult = evaluateResultsForExercise(bqTestResult, scheme, passmark)
      // TODO do the math here
      val schemeResult = (sjqResult, bqResult) match {
        case (Red, _) => Red
        case (_, Red) => Red
        case (Amber, _) => Amber
        case (_, Amber) => Amber
        case (Green, Green) => Green
      }

      scheme -> schemeResult
    }
  }

  private def evaluateResultsForExercise(testResult: TestResult, scheme: SchemeType, passmarkSettings: Any): Result = {
    val tScore = testResult.tScore.get
    // TODO Integrate with Passmark
    val failmark = 20.0
    val passmark = 80.0
    determineResult(tScore, failmark, passmark)
  }

  private def determineResult(tScore: Double, failmark: Double, passmark: Double): Result = {
    def determineResultWithoutAmbers = {
      if (tScore <= failmark) {
        Red
      } else if (tScore >= passmark) {
        Green
      } else {
        Amber
      }
    }

    def determineResultWithAmbers = {
      if (tScore >= passmark) {
        Green
      } else {
        Red
      }
    }

    val isAmberGapPresent = failmark < passmark
    if (isAmberGapPresent) {
      determineResultWithAmbers
    } else {
      determineResultWithoutAmbers
    }
  }
}
