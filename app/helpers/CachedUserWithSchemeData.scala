/*
 * Copyright 2017 HM Revenue & Customs
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

package helpers

import connectors.exchange.{ SchemeEvaluationResult, SchemeEvaluationResultWithFailureDetails }
import connectors.exchange.referencedata.{ Scheme, SiftRequirement }
import models._
import security.RoleUtils

case class CurrentSchemeStatus(
  scheme: Scheme,
  status: SchemeStatus.Status,
  failedAtStage: Option[String]
)

class CachedUserWithSchemeData(
  val user: CachedUser,
  val application: ApplicationData,
  val allSchemes: Seq[Scheme],
  val phase3Evaluation: Option[Seq[SchemeEvaluationResult]],
  val siftEvaluation: Option[Seq[SchemeEvaluationResult]],
  val rawSchemesStatus: Seq[SchemeEvaluationResultWithFailureDetails]
) {

  lazy val currentSchemesStatus = rawSchemesStatus flatMap { schemeResult =>
    allSchemes.find(_.id == schemeResult.schemeId).map { scheme =>
      val status = SchemeStatus.Status(schemeResult.result)
      CurrentSchemeStatus(scheme, status, schemeResult.failedAt)
    }
  }

  private def formatEvaluationResultsToCurrentSchemeStatuses(evaluationResults: Seq[SchemeEvaluationResult]) = {
    evaluationResults.flatMap { schemeResult =>
      allSchemes.find(_.id == schemeResult.schemeId).map { scheme =>
        val schemeStatus = SchemeStatus.Status(schemeResult.result)
        val failedAt = if (schemeStatus == SchemeStatus.Red) {
          currentSchemesStatus.find(_.scheme == scheme).map(_.failedAtStage).getOrElse(None)
        } else {
          None
        }
        CurrentSchemeStatus(scheme, schemeStatus, failedAt)
      }
    }
  }

  private def filterWithdrawnAndFailed(schemesList: Seq[SchemeEvaluationResult]): Seq[SchemeEvaluationResult] = {
    schemesList.filterNot(schemeResult => withdrawnSchemes.exists(_.id == schemeResult.schemeId))
      .filterNot(schemeResult => failedSchemesForDisplay.exists(_.scheme.id == schemeResult.schemeId))
  }

  private def filterWithdrawn(schemesList: Seq[SchemeEvaluationResult]): Seq[SchemeEvaluationResult] = {
    schemesList.filterNot(schemeResult => withdrawnSchemes.exists(_.id == schemeResult.schemeId))
  }

  private val ambersInCurrentSchemeStatus = currentSchemesStatus.filterNot(_.scheme.id == Scheme.SdipId).exists(_.status == SchemeStatus.Amber)

  private val assessmentCentreInProgress = application.progress.assessmentCentre.scoresAccepted &&
    !application.progress.assessmentCentre.failed &&
    !application.progress.assessmentCentre.passed

  private val siftInProgress = !application.progress.assessmentCentre.allocationConfirmed &&
    !application.progress.assessmentCentre.allocationUnconfirmed &&
    !application.progress.assessmentCentre.awaitingAllocation &&
    application.progress.siftProgress.siftCompleted &&
    !application.progress.siftProgress.failedAtSift

  private val greensAtSiftOpt = siftEvaluation.map(siftEval => siftEval.filter(_.result == SchemeStatus.Green.toString))
  private val redsAtSiftOpt = siftEvaluation.map(siftEval => siftEval.filter(_.result == SchemeStatus.Red.toString))

  private val greensAtPhase3Opt = phase3Evaluation.map(phase3Eval => phase3Eval.filter(_.result == SchemeStatus.Green.toString))
  private val redsAtPhase3Opt = phase3Evaluation.map(phase3Eval => phase3Eval.filter(_.result == SchemeStatus.Red.toString))

  private val rawSchemeStatusAllGreen = rawSchemesStatus.map(schemeResult =>
    SchemeEvaluationResult(schemeResult.schemeId, SchemeStatus.Green.toString)
  )

  lazy val successfulSchemesForDisplay: Seq[CurrentSchemeStatus] = {

    def filterAndFormat(evaluationResults: Seq[SchemeEvaluationResult]): Seq[CurrentSchemeStatus] = {
      val filteredEval = filterWithdrawnAndFailed(evaluationResults)
      formatEvaluationResultsToCurrentSchemeStatuses(filteredEval)
    }

    // If any ambers exist this candidate is being evaluated for the next stage
    if (ambersInCurrentSchemeStatus && assessmentCentreInProgress) {
      // In AC show SIFT or VIDEO results (or green if fast pass)
      val lastNonAmberEval = greensAtSiftOpt.orElse(greensAtPhase3Opt).getOrElse(rawSchemeStatusAllGreen)
      filterAndFormat(lastNonAmberEval)
    } else if (siftInProgress) {
      // In SIFT show video results (or green if fast pass)
      val lastEval = greensAtPhase3Opt.getOrElse(rawSchemeStatusAllGreen)
      filterAndFormat(lastEval)
    } else {
      currentSchemesStatus.filter(_.status == SchemeStatus.Green)
    }
  }

  lazy val failedSchemesForDisplay: Seq[CurrentSchemeStatus] = {

    def filterAndFormat(evaluationResults: Seq[SchemeEvaluationResult]) = {
      val filteredEval = filterWithdrawn(evaluationResults)
      formatEvaluationResultsToCurrentSchemeStatuses(filteredEval)
    }

    if (ambersInCurrentSchemeStatus && assessmentCentreInProgress) {
      // In AC show SIFT or VIDEO failures (or assume no failures if fast pass)
      val lastNonAmberEval = redsAtSiftOpt.orElse(redsAtPhase3Opt).getOrElse(Nil)
      filterAndFormat(lastNonAmberEval)
    } else if (siftInProgress) {
      // In SIFT show video failures (or assume no failures if fast pass)
      val lastEval = redsAtPhase3Opt.getOrElse(Nil)
      filterAndFormat(lastEval)
    } else {
      currentSchemesStatus.filter(_.status == SchemeStatus.Red)
    }
  }

  lazy val withdrawnSchemes = currentSchemesStatus.collect { case s if s.status == SchemeStatus.Withdrawn => s.scheme }

  lazy val successfulSchemes = currentSchemesStatus.filter(_.status == SchemeStatus.Green)

  lazy val schemesForSiftForms = successfulSchemes.collect {
    case s if s.scheme.siftRequirement.contains(SiftRequirement.FORM) => s.scheme
  }

  lazy val numberOfSuccessfulSchemesForDisplay = successfulSchemesForDisplay.size
  lazy val numberOfFailedSchemesForDisplay = failedSchemesForDisplay.size
  lazy val numberOfWithdrawnSchemes = withdrawnSchemes.size

  lazy val hasFormRequirement = successfulSchemes.exists(_.scheme.siftRequirement.contains(SiftRequirement.FORM))
  lazy val hasNumericRequirement = successfulSchemes.exists(_.scheme.siftRequirement.contains(SiftRequirement.NUMERIC_TEST))
  lazy val isNumericOnly = !hasFormRequirement && hasNumericRequirement
  lazy val requiresAssessmentCentre = !(RoleUtils.isSdip(toCachedData) || RoleUtils.isEdip(toCachedData))

  def toCachedData: CachedData = CachedData(this.user, Some(this.application))

}

object CachedUserWithSchemeData {
  def apply(
    user: CachedUser,
    application: ApplicationData,
    allSchemes: Seq[Scheme],
    phase3Evaluation: Option[Seq[SchemeEvaluationResult]],
    siftEvaluation: Option[Seq[SchemeEvaluationResult]],
    rawSchemesStatus: Seq[SchemeEvaluationResultWithFailureDetails]): CachedUserWithSchemeData =
    new CachedUserWithSchemeData(user, application, allSchemes, phase3Evaluation, siftEvaluation, rawSchemesStatus)
}
