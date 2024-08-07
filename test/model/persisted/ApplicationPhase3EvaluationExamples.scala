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

package persisted

import model.EvaluationResults.Green
import model.persisted.{ApplicationReadyForEvaluation, SchemeEvaluationResult}
import model.{ApplicationStatus, SelectedSchemesExamples, _}

import java.time.OffsetDateTime

object ApplicationPhase3EvaluationExamples {
  def faststreamPsiApplication(implicit now: OffsetDateTime) = ApplicationReadyForEvaluation("app1", ApplicationStatus.PHASE3_TESTS,
    ApplicationRoute.Faststream, isGis = false, activePsiTests = Phase2TestProfileExamples.profile.activeTests,
    activeLaunchpadTest = None, prevPhaseEvaluation = None, SelectedSchemesExamples.twoSchemes,
    List(
      SchemeEvaluationResult(SelectedSchemesExamples.scheme1, Green.toString),
      SchemeEvaluationResult(SelectedSchemesExamples.scheme2, Green.toString)
    )
  )

  def sdipFaststreamPsiApplication(implicit now: OffsetDateTime) = ApplicationReadyForEvaluation("app1", ApplicationStatus.PHASE3_TESTS,
    ApplicationRoute.SdipFaststream, isGis = false, activePsiTests = Phase2TestProfileExamples.profile.activeTests,
    activeLaunchpadTest = None, prevPhaseEvaluation = None, SelectedSchemesExamples.twoSchemes,
    List(
      SchemeEvaluationResult(SelectedSchemesExamples.scheme1, Green.toString),
      SchemeEvaluationResult(SelectedSchemesExamples.scheme2, Green.toString)
    )
  )
}
