/*
 * Copyright 2020 HM Revenue & Customs
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
import model.{ ApplicationRoute, ApplicationStatus, SchemeId }
import model.ApplicationStatus.ApplicationStatus
import model.EvaluationResults.{ Result, _ }
import model.Phase.Phase
import model.Phase._
import model.ProgressStatuses.ProgressStatus
import model.ProgressStatuses._
import model.persisted.SchemeEvaluationResult

trait ApplicationStatusCalculator {

  private def faststreamCalc(phase: Phase, originalAppStatus: ApplicationStatus,
    results: List[Result]): Option[ProgressStatus] = (phase, originalAppStatus) match {
    case (PHASE1, ApplicationStatus.PHASE1_TESTS) => processResults(results, PHASE1_TESTS_PASSED, PHASE1_TESTS_FAILED)
    case (PHASE2, ApplicationStatus.PHASE2_TESTS) => processResults(results, PHASE2_TESTS_PASSED, PHASE2_TESTS_FAILED)
    case (PHASE3, ApplicationStatus.PHASE3_TESTS | ApplicationStatus.PHASE3_TESTS_PASSED_WITH_AMBER)
        if results.contains(Amber) && results.contains(Green) => Some(PHASE3_TESTS_PASSED_WITH_AMBER)

    case (PHASE3, ApplicationStatus.PHASE3_TESTS | ApplicationStatus.PHASE3_TESTS_PASSED_WITH_AMBER) =>
        processResults(results, PHASE3_TESTS_PASSED, PHASE3_TESTS_FAILED)

    case _ => None
  }

  //scalastyle:off cyclomatic.complexity
  private def sdipFaststreamCalc(phase: Phase, originalAppStatus: ApplicationStatus,
    evaluatedSchemes: List[SchemeEvaluationResult]): Option[ProgressStatus] = {
    val sdip = "Sdip"
    val fsResults = evaluatedSchemes.filterNot(_.schemeId == SchemeId(sdip)).map(s => Result(s.result))
    val fsOverallResult = faststreamCalc(phase, originalAppStatus, fsResults)

    def toResultWhenPhase1SdipFaststreamNotFailed(sdipResult: model.EvaluationResults.Result) =
      sdipResult match {
        case Amber => Some(PHASE1_TESTS_FAILED_SDIP_AMBER)
        case Green => Some(PHASE1_TESTS_FAILED_SDIP_GREEN)
        case _ => Some(PHASE1_TESTS_PASSED)
      }

    def toResultWhenPhase2SdipFaststreamNotFailed(sdipResult: model.EvaluationResults.Result) =
      sdipResult match {
        case Amber => Some(PHASE2_TESTS_FAILED_SDIP_AMBER)
        case Green => Some(PHASE2_TESTS_FAILED_SDIP_GREEN)
        case _ => Some(PHASE2_TESTS_PASSED)
      }

    def toResultWhenPhase3SdipFaststreamNotFailed(sdipResult: model.EvaluationResults.Result) =
      sdipResult match {
        case Amber => Some(PHASE3_TESTS_FAILED_SDIP_AMBER)
        case Green => Some(PHASE3_TESTS_FAILED_SDIP_GREEN)
        case _ => Some(PHASE3_TESTS_PASSED)
      }

    val sdipResult = evaluatedSchemes.filter(_.schemeId == SchemeId(sdip)).map(s => Result(s.result)).head
    sdipResult match {
      case Amber | Green if fsOverallResult.contains(PHASE1_TESTS_FAILED) => toResultWhenPhase1SdipFaststreamNotFailed(sdipResult)
      case Amber | Green if fsOverallResult.contains(PHASE2_TESTS_FAILED) => toResultWhenPhase2SdipFaststreamNotFailed(sdipResult)
      case Amber | Green if fsOverallResult.contains(PHASE3_TESTS_FAILED) => toResultWhenPhase3SdipFaststreamNotFailed(sdipResult)
      case Amber if fsOverallResult.contains(PHASE3_TESTS_PASSED) => Some(PHASE3_TESTS_PASSED_WITH_AMBER)
      case Green if fsOverallResult.isEmpty && phase == PHASE3 => Some(PHASE3_TESTS_PASSED_WITH_AMBER)
      case _ => fsOverallResult
    }
  }
  //scalastyle:on

  private def edipSdipCalc(phase: Phase, originalAppStatus: ApplicationStatus,
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
