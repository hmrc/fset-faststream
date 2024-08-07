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

package model.persisted

import model.EvaluationResults.Green
import model.{ApplicationRoute, ApplicationStatus, Phase1TestProfileExamples, SelectedSchemesExamples}

import java.time.OffsetDateTime

object ApplicationPhase1EvaluationExamples {

  def faststreamApplication(implicit now: OffsetDateTime) =
    ApplicationReadyForEvaluation("app1", ApplicationStatus.PHASE1_TESTS,
      ApplicationRoute.Faststream, isGis = false, activePsiTests = Phase1TestProfileExamples.psiProfile.activeTests,
      activeLaunchpadTest = None, prevPhaseEvaluation = None, SelectedSchemesExamples.twoSchemes,
      List(
        SchemeEvaluationResult(SelectedSchemesExamples.scheme1, Green.toString),
        SchemeEvaluationResult(SelectedSchemesExamples.scheme2, Green.toString)
      )
    )

  def edipApplication(implicit now: OffsetDateTime) =
    ApplicationReadyForEvaluation("app1", ApplicationStatus.PHASE1_TESTS,
      ApplicationRoute.Edip, isGis = false, activePsiTests = Phase1TestProfileExamples.psiProfile.activeTests,
      activeLaunchpadTest = None, prevPhaseEvaluation = None, SelectedSchemesExamples.edipScheme,
      List(
        SchemeEvaluationResult(SelectedSchemesExamples.Edip, Green.toString),
      )
    )

  def sdipFaststreamApplication(implicit now: OffsetDateTime) =
    ApplicationReadyForEvaluation("app1", ApplicationStatus.PHASE1_TESTS,
      ApplicationRoute.SdipFaststream, isGis = false, activePsiTests = Phase1TestProfileExamples.psiProfile.activeTests,
      activeLaunchpadTest = None, prevPhaseEvaluation = None, SelectedSchemesExamples.sdipFsSchemes,
      List(
        SchemeEvaluationResult(SelectedSchemesExamples.scheme1, Green.toString),
        SchemeEvaluationResult(SelectedSchemesExamples.scheme2, Green.toString),
        SchemeEvaluationResult(SelectedSchemesExamples.Sdip, Green.toString)
      )
    )
}
