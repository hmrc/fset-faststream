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
import connectors.{ ApplicationClient, ReferenceDataClient, SiftClient }
import connectors.exchange.referencedata.{ Scheme, SiftRequirement }
import connectors.exchange.sift.SiftAnswersStatus
import models._
import security.RoleUtils
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

case class CurrentSchemeStatus(
  scheme: Scheme,
  status: SchemeStatus.Status,
  failedAtStage: Option[String]
)

class CachedUserWithSchemeData(
  val user: CachedUser,
  val application: ApplicationData,
  val allSchemes: Seq[Scheme],
  val siftEvaluation: Option[Seq[SchemeEvaluationResult]],
  val phase3Evaluation: Option[Seq[SchemeEvaluationResult]],
  val rawSchemesStatus: Seq[SchemeEvaluationResultWithFailureDetails]
) {

  lazy val currentSchemesStatus = rawSchemesStatus flatMap { schemeResult =>
      allSchemes.find(_.id == schemeResult.schemeId).map { scheme =>
        val status = SchemeStatus.Status(schemeResult.result)
        CurrentSchemeStatus(scheme, status, schemeResult.failedAt)
      }
    }

  lazy val successfulSchemes = {
    // If any ambers exist this candidate is being evaluated for the next stage
    // Check withdrawals
    if (currentSchemesStatus.exists(_.status == SchemeStatus.Amber)) {
      // In AC show their SIFT or VIDEO results (or green if fast pass)
      if (application.progress.assessmentCentre.scoresAccepted && !application.progress.assessmentCentre.failed
        && !application.progress.assessmentCentre.passed) {
        siftEvaluation.map(siftEval => siftEval.filter(_.result == SchemeStatus.Green.toString)).orElse {
          phase3Evaluation.map(phase3Eval => phase3Eval.filter(_.result == SchemeStatus.Green.toString)).orElse(None)
        }

      } else if (application.progress.siftProgress.siftCompleted && !application.progress.siftProgress.failedAtSift) {
        currentSchemesStatus.filter(_.status == SchemeStatus.Green)
      }
    } else {
      currentSchemesStatus.filter(_.status == SchemeStatus.Green)
    }
  }

  // TODO: Same as successful schemes, show old failures if in progress SIFT or AC
  // TODO: Check withdrawals
  // TODO: Import failedAt from CSS
  lazy val failedSchemes = currentSchemesStatus.filter(_.status == SchemeStatus.Red)

  // TODO: Merge withdrawals from CSS over the top of an old status, if necessary
  lazy val withdrawnSchemes = currentSchemesStatus.collect { case s if s.status == SchemeStatus.Withdrawn => s.scheme}

  lazy val schemesForSiftForms = successfulSchemes.collect {
    case s if s.scheme.siftRequirement.contains(SiftRequirement.FORM) => s.scheme
  }

  lazy val noSuccessfulSchemes = successfulSchemes.size
  lazy val noFailedSchemes = failedSchemes.size
  lazy val noWithdrawnSchemes = withdrawnSchemes.size

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
    rawSchemesStatus: Seq[SchemeEvaluationResultWithFailureDetails]): CachedUserWithSchemeData =
      new CachedUserWithSchemeData(user, application, allSchemes, rawSchemesStatus
  )
}
