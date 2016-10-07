package services.onlinetesting.phase1

import model.EvaluationResults._
import model.Phase1TestExamples._
import model.SchemeType._
import model.exchange.passmarksettings._
import model.persisted.SchemeEvaluationResult
import org.scalatest.prop.TableDrivenPropertyChecks
import services.BaseServiceSpec

class Phase1TestEvaluationSpec extends BaseServiceSpec with TableDrivenPropertyChecks {
  val evaluation = new Phase1TestEvaluation {}

  val CurrentPassmarkWithAmbers = Phase1PassMarkSettingsExamples.passmark.copy(schemes = List(
    passmark(Commercial, sjqFail = 20.0, sjqPass = 80.0, bqFail = 10.0, bqPass = 90.0)
  ))
  val CurrentPassmarkWithoutAmbers = Phase1PassMarkSettingsExamples.passmark.copy(schemes = List(
    passmark(Generalist, sjqFail = 45.0, sjqPass = 45.0, bqFail = 45.0, bqPass = 45.0)
  ))

  // format: OFF
  // None in 'bq result' means GIS application
  val Phase1EvaluationData = Table(
    ("schemes",           "sjq result",            "bq result",               "result"),
    (List(Commercial),    10.0,                     Some(90.0),                List(Red)),
    (List(Commercial),    20.0,                     Some(90.0),                List(Red)),
    (List(Commercial),    30.0,                     Some(10.0),                List(Red)),
    (List(Commercial),    0.0,                      Some(0.0),                 List(Red)),
    (List(Commercial),    20.01,                    Some(100.0),               List(Amber)),
    (List(Commercial),    100.0,                    Some(10.00001),            List(Amber)),
    (List(Commercial),    80.0,                     Some(90.0),                List(Green)),
    (List(Commercial),    100.0,                    Some(100.0),               List(Green)),
    (List(Commercial),    20.0,                     None,                      List(Red)),
    (List(Commercial),    25.0,                     None,                      List(Amber)),
    (List(Commercial),    80.0,                     None,                      List(Green)),
    (List(Commercial),    85.0,                     None,                      List(Green))
  )


  val Phase1EvaluationDataWithoutAmbers = Table(
    ("schemes",           "sjq result",            "bq result",               "result"),
    (List(Generalist),    45.0,                     None,                      List(Green)),
    (List(Generalist),    45.0,                     Some(45.0),                List(Green)),
    (List(Generalist),    10.0,                     Some(90.0),                List(Red)),
    (List(Generalist),    20.0,                     Some(90.0),                List(Red)),
    (List(Generalist),    30.0,                     Some(10.0),                List(Red)),
    (List(Generalist),    0.0,                      Some(0.0),                 List(Red)),
    (List(Generalist),    20.01,                    Some(100.0),               List(Red)),
    (List(Generalist),    100.0,                    Some(10.00001),            List(Red)),
    (List(Generalist),    80.0,                     Some(90.0),                List(Green)),
    (List(Generalist),    100.0,                    Some(100.0),               List(Green)),
    (List(Generalist),    20.0,                     None,                      List(Red)),
    (List(Generalist),    25.0,                     None,                      List(Red)),
    (List(Generalist),    80.0,                     None,                      List(Green)),
    (List(Generalist),    85.0,                     None,                      List(Green))
  )
  // format: ON

  "evaluate phase1 tests" should {
    "evaluate schemes for Passmark with AMBER gap" in {
      forAll (Phase1EvaluationData) { (schemes: List[SchemeType], sjqResult, bqResultOpt: Option[Double], expected: List[Result]) =>
        val result = bqResultOpt match {
          case Some(bqResult) =>
            evaluation.evaluateForNonGis(schemes, createTestResult(sjqResult), createTestResult(bqResult), CurrentPassmarkWithAmbers)
          case None =>
            evaluation.evaluateForGis(schemes, createTestResult(sjqResult), CurrentPassmarkWithAmbers)
        }

        result mustBe normalize(schemes, expected)
      }
    }

    "evaluate schemes for Passmark without AMBER gap" in {
      forAll (Phase1EvaluationDataWithoutAmbers) { (schemes: List[SchemeType], sjqResult, bqResultOpt: Option[Double], expected: List[Result]) =>
        val result = bqResultOpt match {
          case Some(bqResult) =>
            evaluation.evaluateForNonGis(schemes, createTestResult(sjqResult), createTestResult(bqResult), CurrentPassmarkWithoutAmbers)
          case None =>
            evaluation.evaluateForGis(schemes, createTestResult(sjqResult), CurrentPassmarkWithoutAmbers)
        }

        result mustBe normalize(schemes, expected)
      }
    }
  }

  def normalize(schemes: List[SchemeType], expected: List[Result]) = {
    schemes.zip(expected).map { case (s, r) => SchemeEvaluationResult(s, r.toString) }
  }

  def passmark(s: SchemeType, sjqFail: Double, sjqPass: Double, bqFail: Double, bqPass: Double) = {
    Phase1PassMark(s, Phase1PassMarkThresholds(PassMarkThreshold(sjqFail, sjqPass), PassMarkThreshold(bqFail, bqPass)))
  }
}
