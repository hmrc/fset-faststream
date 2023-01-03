/*
 * Copyright 2023 HM Revenue & Customs
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

package model

//import model.OnlineTestCommands.TestResult
import org.scalatest.prop.TableDrivenPropertyChecks
import testkit.UnitSpec

class CandidateTestReportSpec extends UnitSpec with TableDrivenPropertyChecks {
//  import CandidateTestReportSpec._
/*
  // format: OFF
  // t means TestResult in the format: t(tScore, raw, percentile, sten)
  val TestResultCasesNonGIS = Table(
    ("competency",           "numerical",            "verbal",               "situational",          "isValid"),
    (t(ok, ok, ok, ok),      t(ok, ok, ok, ok),      t(ok, ok, ok, ok),      t(ok, ok, ok, ok),       VALID),
    (t(ok, ok, ok, ok),      t(ok, ok, ok, ok),      t(ok, ok, ok, ok),      None,                    INVALID),
    (t(ok, ok, ok, ok),      t(ok, ok, ok, ok),      None,                   t(ok, ok, ok, ok),       INVALID),
    (t(ok, ok, ok, ok),      None,                   t(ok, ok, ok, ok),      t(ok, ok, ok, ok),       INVALID),
    (None,                   t(ok, ok, ok, ok),      t(ok, ok, ok, ok),      t(ok, ok, ok, ok),       INVALID),
    (t(missing, ok, ok, ok), t(ok, ok, ok, ok),      t(ok, ok, ok, ok),      t(ok, ok, ok, ok),       INVALID),
    (t(ok, missing, ok, ok), t(ok, ok, ok, ok),      t(ok, ok, ok, ok),      t(ok, ok, ok, ok),       VALID),
    (t(ok, ok, missing, ok), t(ok, ok, ok, ok),      t(ok, ok, ok, ok),      t(ok, ok, ok, ok),       VALID),
    (t(ok, ok, ok, missing), t(ok, ok, ok, ok),      t(ok, ok, ok, ok),      t(ok, ok, ok, ok),       VALID),
    (t(ok, ok, ok, ok),      t(missing, ok, ok, ok), t(ok, ok, ok, ok),      t(ok, ok, ok, ok),       INVALID),
    (t(ok, ok, ok, ok),      t(ok, missing, ok, ok), t(ok, ok, ok, ok),      t(ok, ok, ok, ok),       INVALID),
    (t(ok, ok, ok, ok),      t(ok, ok, missing, ok), t(ok, ok, ok, ok),      t(ok, ok, ok, ok),       INVALID),
    (t(ok, ok, ok, ok),      t(ok, ok, ok, missing), t(ok, ok, ok, ok),      t(ok, ok, ok, ok),       VALID),
    (t(ok, ok, ok, ok),      t(ok, ok, ok, ok),      t(missing, ok, ok, ok), t(ok, ok, ok, ok),       INVALID),
    (t(ok, ok, ok, ok),      t(ok, ok, ok, ok),      t(ok, missing, ok, ok), t(ok, ok, ok, ok),       INVALID),
    (t(ok, ok, ok, ok),      t(ok, ok, ok, ok),      t(ok, ok, missing, ok), t(ok, ok, ok, ok),       INVALID),
    (t(ok, ok, ok, ok),      t(ok, ok, ok, ok),      t(ok, ok, ok, missing), t(ok, ok, ok, ok),       VALID),
    (t(ok, ok, ok, ok),      t(ok, ok, ok, ok),      t(ok, ok, ok, ok),      t(missing, ok, ok, ok),  INVALID),
    (t(ok, ok, ok, ok),      t(ok, ok, ok, ok),      t(ok, ok, ok, ok),      t(ok, missing, ok, ok),  INVALID),
    (t(ok, ok, ok, ok),      t(ok, ok, ok, ok),      t(ok, ok, ok, ok),      t(ok, ok, missing, ok),  INVALID),
    (t(ok, ok, ok, ok),      t(ok, ok, ok, ok),      t(ok, ok, ok, ok),      t(ok, ok, ok, missing),  INVALID)
    )

  val TestResultCasesGIS = Table(
    ("competency",           "situational",          "isValid"),
    (t(ok, ok, ok, ok),      t(ok, ok, ok, ok),       VALID),
    (t(ok, ok, ok, ok),      None,                    INVALID),
    (None,                   t(ok, ok, ok, ok),       INVALID),
    (t(missing, ok, ok, ok), t(ok, ok, ok, ok),       INVALID),
    (t(ok, missing, ok, ok), t(ok, ok, ok, ok),       VALID),
    (t(ok, ok, missing, ok), t(ok, ok, ok, ok),       VALID),
    (t(ok, ok, ok, missing), t(ok, ok, ok, ok),       VALID),
    (t(ok, ok, ok, ok),      t(missing, ok, ok, ok),  INVALID),
    (t(ok, ok, ok, ok),      t(ok, missing, ok, ok),  INVALID),
    (t(ok, ok, ok, ok),      t(ok, ok, missing, ok),  INVALID),
    (t(ok, ok, ok, ok),      t(ok, ok, ok, missing),  INVALID)
    )
  // format: ON

  "Candidate Test Report" should {
    "evaluate correctly non GIS results" in {
      forAll(TestResultCasesNonGIS) { (competency: Option[TestResult], numerical: Option[TestResult], verbal: Option[TestResult],
        situational: Option[TestResult], isValid: Status) =>
        val candidate = CandidateTestReport("unused", "unused", competency, numerical, verbal, situational)
        evaluate(candidate, gis = false) must be(isValid)
      }
    }

    "evaluate correctly GIS results" in {
      forAll(TestResultCasesGIS) { (competency: Option[TestResult], situational: Option[TestResult], isValid: Status) =>
        val candidate = CandidateTestReport("unused", "unused", competency, None, None, situational)
        evaluate(candidate, gis = true) must be(isValid)
      }
    }

    "evaluate as invalid all GIS results in non-GIS evaluation" in {
      forAll(TestResultCasesGIS) { (competency: Option[TestResult], situational: Option[TestResult], _: Status) =>
        val candidate = CandidateTestReport("unused", "unused", competency, None, None, situational)
        evaluate(candidate, gis = false) must be(INVALID)
      }
    }
  }

  def evaluate(candidate: CandidateTestReport, gis: Boolean) = if (candidate.isValid(gis)) {
    VALID
  } else {
    INVALID
  }*/
}
/*
// DSL
object CandidateTestReportSpec {
  def t(tScore: Option[Double], raw: Option[Double], percentile: Option[Double], sten: Option[Double]): Option[TestResult] =
    Some(TestResult("", "", tScore, raw, percentile, sten))
  def ok: Option[Double] = Some(40) // actual tscore value does not impact on the status
  def missing: Option[Double] = None

  sealed trait Status
  case object VALID extends Status
  case object INVALID extends Status
}*/
