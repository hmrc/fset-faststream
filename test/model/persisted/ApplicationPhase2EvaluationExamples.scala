/*
 * Copyright 2019 HM Revenue & Customs
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

import model.{ApplicationStatus, Phase1TestProfileExamples, SelectedSchemesExamples, _}
import org.joda.time.DateTime

object ApplicationPhase2EvaluationExamples {
  def faststreamApplication(implicit now: DateTime) = ApplicationReadyForEvaluation("app1", ApplicationStatus.PHASE2_TESTS,
    ApplicationRoute.Faststream, isGis = false, activeCubiksTests = Phase1TestProfileExamples.profile.activeTests,
    activeLaunchpadTest = None, prevPhaseEvaluation = None, SelectedSchemesExamples.TwoSchemes)

  def faststreamPsiApplication(implicit now: DateTime) = ApplicationReadyForEvaluation2("app1", ApplicationStatus.PHASE2_TESTS,
    ApplicationRoute.Faststream, isGis = false, activePsiTests = Phase2TestProfileExamples.profile2.activeTests,
    activeLaunchpadTest = None, prevPhaseEvaluation = None, SelectedSchemesExamples.TwoSchemes)

  def sdipFaststreamPsiApplication(implicit now: DateTime) = ApplicationReadyForEvaluation2("app1", ApplicationStatus.PHASE2_TESTS,
    ApplicationRoute.SdipFaststream, isGis = false, activePsiTests = Phase2TestProfileExamples.profile2.activeTests,
    activeLaunchpadTest = None, prevPhaseEvaluation = None, SelectedSchemesExamples.TwoSchemes)
}
