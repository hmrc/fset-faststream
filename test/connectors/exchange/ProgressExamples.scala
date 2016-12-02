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

package connectors.exchange

import models.{ Phase1TestProgress, Phase2TestProgress, Phase3TestProgress, Progress }

object ProgressExamples {
  val InitialProgress = Progress(false, false, false, false, false, false, false, false, false, false, false,
    phase1TestProgress = Phase1TestProgress(false, false, false, false, false, false,
      false, false, false, false),
    phase2TestProgress = Phase2TestProgress(false, false, false, false, false, false,
      false, false, false, false),
    phase3TestProgress = Phase3TestProgress(phase3TestsInvited = false,
      phase3TestsStarted = false,
      phase3TestsCompleted = false,
      phase3TestsExpired = false,
      phase3TestsResultsReceived = false
    ),
    false,
    AssessmentScores(false, false),
    AssessmentCentre(false, false, false)
  )
  val FullProgress = Progress(true, true, true, true, true, true, true, true, true, true, true,
    phase1TestProgress = Phase1TestProgress(true, true, true, true, true, true,
      true, true, true, true),
    phase2TestProgress = Phase2TestProgress(true, true, true, true, true, true,
      true, true, true, true),
    phase3TestProgress = Phase3TestProgress(phase3TestsInvited = true,
      phase3TestsStarted = true,
      phase3TestsCompleted = true,
      phase3TestsExpired = true,
      phase3TestsResultsReceived = true
    ),
    true,
    AssessmentScores(true, true),
    AssessmentCentre(true, true, true)
  )
  val Phase3TestsPassed = Progress(true, true, true, true, true, true, true, true, true, true, true,
    phase1TestProgress = Phase1TestProgress(true, true, true, true, true, true,
      true, true, true, true),
    phase2TestProgress = Phase2TestProgress(true, true, true, true, true, true,
      true, true, true, true),
    phase3TestProgress = Phase3TestProgress(phase3TestsInvited = true,
      phase3TestsStarted = true,
      phase3TestsCompleted = true,
      phase3TestsExpired = true,
      phase3TestsResultsReceived = true,
      phase3TestsPassed = true
    ),
    true,
    AssessmentScores(true, true),
    AssessmentCentre(true, true, true)
  )
  val PersonalDetailsProgress = InitialProgress.copy(personalDetails = true)
  val SchemePreferencesProgress = PersonalDetailsProgress.copy(schemePreferences = true)
  val PartnerGraduateProgrammesProgress = SchemePreferencesProgress.copy(partnerGraduateProgrammes = true)
  val AssistanceDetailsProgress = PartnerGraduateProgrammesProgress.copy(assistanceDetails = true)
  val QuestionnaireProgress = AssistanceDetailsProgress.copy(startedQuestionnaire = true, diversityQuestionnaire = true,
    educationQuestionnaire = true ,occupationQuestionnaire = true)
  val PreviewProgress = QuestionnaireProgress.copy(preview = true)
  val SubmitProgress = PreviewProgress.copy(submitted = true)
  val WithdrawnAfterSubmitProgress = SubmitProgress.copy(withdrawn = true)
}
