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

package services.onlinetesting

import model.ApplicationRoute._
import model.{ ApplicationRoute, ApplicationStatus }
import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.{ Result, _ }
import model.Phase.Phase
import model.Phase._
import model.ProgressStatuses.ProgressStatus
import model.ProgressStatuses._
import model.persisted.SchemeEvaluationResult

// scalastyle:off cyclomatic.complexity
trait ApplicationStatusCalculator {

  case class UnimplementedApplicationRouteException(m: String) extends Exception(m)

  type CalcStep = PartialFunction[(Phase, ApplicationStatus), Option[ProgressStatus]]

  def determineApplicationStatus(applicationRoute: ApplicationRoute,
    originalApplicationStatus: ApplicationStatus,
    evaluatedSchemes: List[SchemeEvaluationResult],
    phase: Phase
  ): Option[ProgressStatus] = {

    val results = evaluatedSchemes.map(s => Result(s.result))
    require(results.nonEmpty, "Results not found")

    val phase1: CalcStep = { case (PHASE1, ApplicationStatus.PHASE1_TESTS) => processResults(results, PHASE1_TESTS_PASSED, PHASE1_TESTS_FAILED) }

    val phase1Amber: CalcStep = {
      case (PHASE1, ApplicationStatus.PHASE1_TESTS | ApplicationStatus.PHASE1_TESTS_PASSED_WITH_AMBER)
        if results.contains(Amber) => Some(PHASE1_TESTS_PASSED_WITH_AMBER)

      case (PHASE1, ApplicationStatus.PHASE1_TESTS | ApplicationStatus.PHASE1_TESTS_PASSED_WITH_AMBER) =>
        processResults(results, PHASE1_TESTS_PASSED, PHASE1_TESTS_FAILED)
    }

    val phase2: CalcStep = { case (PHASE2, ApplicationStatus.PHASE2_TESTS) => processResults(results, PHASE2_TESTS_PASSED, PHASE2_TESTS_FAILED) }

    val phase3WithAmber: CalcStep = {
      case (PHASE3, ApplicationStatus.PHASE3_TESTS | ApplicationStatus.PHASE3_TESTS_PASSED_WITH_AMBER)
        if results.contains(Amber) && results.contains(Green) => Some(PHASE3_TESTS_PASSED_WITH_AMBER)

      case (PHASE3, ApplicationStatus.PHASE3_TESTS | ApplicationStatus.PHASE3_TESTS_PASSED_WITH_AMBER) =>
        processResults(results, PHASE3_TESTS_PASSED, PHASE3_TESTS_FAILED)
    }

    val default: CalcStep = { case _ => None }

    applicationRoute match {
      case ApplicationRoute.Faststream => (phase1 orElse phase2 orElse phase3WithAmber orElse default)(phase -> originalApplicationStatus)

      case ApplicationRoute.Edip => (phase1Amber orElse default)(phase -> originalApplicationStatus)

      case ApplicationRoute.Sdip => (phase1 orElse default)(phase -> originalApplicationStatus)

      case _ => throw UnimplementedApplicationRouteException(s"Score evaluation for application route $applicationRoute is not implemented yet.")
    }
  }
  // scalastyle:on cyclomatic.complexity

  def processResults(results: List[Result], pass: ProgressStatus, fail: ProgressStatus): Option[ProgressStatus] = {
    if (results.forall(_ == Red)) {
      Some(fail)
    } else if (results.contains(Green)) {
      Some(pass)
    } else {
      None
    }
  }
}
