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
import model.{ ApplicationRoute, ApplicationStatus, SchemeType }
import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.{ Result, _ }
import model.Phase.Phase
import model.Phase._
import model.ProgressStatuses.ProgressStatus
import model.ProgressStatuses._
import model.persisted.SchemeEvaluationResult

trait ApplicationStatusCalculator {

  def faststreamCalc(phase: Phase, originalAppStatus: ApplicationStatus,
    results: List[Result]): Option[ProgressStatus] = (phase, originalAppStatus) match {
    case (PHASE1, ApplicationStatus.PHASE1_TESTS) => processResults(results, PHASE1_TESTS_PASSED, PHASE1_TESTS_FAILED)
    case (PHASE2, ApplicationStatus.PHASE2_TESTS) => processResults(results, PHASE2_TESTS_PASSED, PHASE2_TESTS_FAILED)
    case (PHASE3, ApplicationStatus.PHASE3_TESTS | ApplicationStatus.PHASE3_TESTS_PASSED_WITH_AMBER)
        if results.contains(Amber) && results.contains(Green) => Some(PHASE3_TESTS_PASSED_WITH_AMBER)

    case (PHASE3, ApplicationStatus.PHASE3_TESTS | ApplicationStatus.PHASE3_TESTS_PASSED_WITH_AMBER) =>
        processResults(results, PHASE3_TESTS_PASSED, PHASE3_TESTS_FAILED)

    case _ => None
  }

  def sdipFaststreamCalc(phase: Phase, originalAppStatus: ApplicationStatus,
    evaluatedSchemes: List[SchemeEvaluationResult]): Option[ProgressStatus] = {
    val results = evaluatedSchemes.filterNot(_.scheme == SchemeType.Sdip).map(s => Result(s.result))
    faststreamCalc(phase, originalAppStatus, results)
  }

  def edipSdipCalc(phase: Phase, originalAppStatus: ApplicationStatus,
    results: List[Result]): Option[ProgressStatus] = (phase, originalAppStatus) match {
    case (PHASE1, ApplicationStatus.PHASE1_TESTS) => processResults(results, PHASE1_TESTS_PASSED, PHASE1_TESTS_FAILED)
    case _ => None
  }

  def determineApplicationStatus(applicationRoute: ApplicationRoute,
    originalApplicationStatus: ApplicationStatus,
    evaluatedSchemes: List[SchemeEvaluationResult],
    phase: Phase
  ): Option[ProgressStatus] = {

    val results = evaluatedSchemes.map(s => Result(s.result))
    require(results.nonEmpty, "Results not found")

    applicationRoute match {
      case ApplicationRoute.Edip | ApplicationRoute.Sdip => edipSdipCalc(phase, originalApplicationStatus, results)
      case ApplicationRoute.SdipFaststream => sdipFaststreamCalc(phase, originalApplicationStatus, evaluatedSchemes)
      case _ => faststreamCalc(phase, originalApplicationStatus, results)
    }
  }

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
