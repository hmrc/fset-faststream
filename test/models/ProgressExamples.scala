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

package models

import connectors.exchange.{ AssessmentCentre, AssessmentScores }

object ProgressExamples {
  val InitialProgress = Progress(false, false, false, false, false, false, false, false, false, false, false,
    OnlineTestProgress(false, false, false, false, false, false, false, false, false, false),
    false,
    AssessmentScores(false, false),
    AssessmentCentre(false, false, false)
  )
  val PersonalDetailsProgress = InitialProgress.copy(personalDetails = true)
  val SchemePreferencesProgress = PersonalDetailsProgress.copy(schemePreferences = true)
  val PartnerGraduateProgrammesProgress = SchemePreferencesProgress.copy(partnerGraduateProgrammes = true)
  val AssistanceDetailsProgress = PartnerGraduateProgrammesProgress.copy(assistanceDetails = true)
  val QuestionnaireProgress = AssistanceDetailsProgress.copy(startedQuestionnaire = true, diversityQuestionnaire = true,
    educationQuestionnaire = true ,occupationQuestionnaire = true)
  val PreviewProgress = QuestionnaireProgress.copy(preview = true)
  val SubmitProgress = PreviewProgress.copy(submitted = true)
}
