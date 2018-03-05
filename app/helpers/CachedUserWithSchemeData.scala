/*
 * Copyright 2018 HM Revenue & Customs
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

import connectors.exchange.{
  SchemeEvaluationResult,
  SchemeEvaluationResultWithFailureDetails,
  SelectedSchemes
}
import connectors.exchange.referencedata.{Scheme, SiftRequirement}
import models._
import play.api.Logger
import security.RoleUtils

case class CurrentSchemeStatus(
    scheme: Scheme,
    status: SchemeStatus.Status,
    failedAtStage: Option[String]
)

class CachedUserWithSchemeData(
    val user: CachedUser,
    val application: ApplicationData,
    val schemePreferences: SelectedSchemes,
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

  private val rawSchemeStatusAllGreen = rawSchemesStatus.map(schemeResult =>
    SchemeEvaluationResult(schemeResult.schemeId, SchemeStatus.Green.toString))

  lazy val greenAndAmberSchemesForDisplay: Seq[CurrentSchemeStatus] =
    currentSchemesStatus
      .filter(schemeStatus =>
        schemePreferences.schemes.contains(schemeStatus.scheme.id.value))
      .filter(schemeStatus =>
        schemeStatus.status == SchemeStatus.Green || schemeStatus.status == SchemeStatus.Amber)

  lazy val failedSchemesForDisplay: Seq[CurrentSchemeStatus] =
    currentSchemesStatus
      .filter(schemeStatus =>
        schemePreferences.schemes.contains(schemeStatus.scheme.id.value))
      .filter(_.status == SchemeStatus.Red)
      .map { schemeStatus => // NOTE: This is a temporary fix for fset-1914!
        if (schemeStatus.scheme.id == Scheme.GESDSId && schemeStatus.failedAtStage.isEmpty) {
          schemeStatus.copy(failedAtStage = Some("final selection board"))
        } else {
          schemeStatus
        }
      }

  lazy val withdrawnSchemes = currentSchemesStatus.collect {
    case s if s.status == SchemeStatus.Withdrawn => s.scheme
  }

  lazy val successfulSchemes =
    currentSchemesStatus.filter(_.status == SchemeStatus.Green)

  lazy val schemesForSiftForms = successfulSchemes.collect {
    case s if s.scheme.siftRequirement.contains(SiftRequirement.FORM) =>
      s.scheme
  }

  lazy val numberOfSuccessfulSchemesForDisplay =
    greenAndAmberSchemesForDisplay.size
  lazy val numberOfFailedSchemesForDisplay = failedSchemesForDisplay.size
  lazy val numberOfWithdrawnSchemes = withdrawnSchemes.size

  lazy val hasFormRequirement = successfulSchemes.exists(
    _.scheme.siftRequirement.contains(SiftRequirement.FORM))
  lazy val hasNumericRequirement = successfulSchemes.exists(
    _.scheme.siftRequirement.contains(SiftRequirement.NUMERIC_TEST))
  lazy val isNumericOnly = !hasFormRequirement && hasNumericRequirement
  lazy val requiresAssessmentCentre = !RoleUtils.isSdip(toCachedData) && !RoleUtils
    .isEdip(toCachedData)

  def toCachedData: CachedData = CachedData(this.user, Some(this.application))

}

object CachedUserWithSchemeData {
  def apply(user: CachedUser,
            application: ApplicationData,
            schemePreferences: SelectedSchemes,
            allSchemes: Seq[Scheme],
            phase3Evaluation: Option[Seq[SchemeEvaluationResult]],
            siftEvaluation: Option[Seq[SchemeEvaluationResult]],
            rawSchemesStatus: Seq[SchemeEvaluationResultWithFailureDetails])
    : CachedUserWithSchemeData =
    new CachedUserWithSchemeData(user,
                                 application,
                                 schemePreferences,
                                 allSchemes,
                                 phase3Evaluation,
                                 siftEvaluation,
                                 rawSchemesStatus)
}
