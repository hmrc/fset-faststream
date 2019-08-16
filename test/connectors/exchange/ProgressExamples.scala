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

package connectors.exchange

import models._

object ProgressExamples {
  val InitialProgress = Progress()

  val SubmittedProgress = Progress(
    personalDetails = true,
    schemePreferences = true,
    preview = true,
    startedQuestionnaire = true,
    diversityQuestionnaire = true,
    educationQuestionnaire = true,
    occupationQuestionnaire = true,
    submitted = true
  )

  val Phase1TestsInvitedNotStarted = Progress(
    phase1TestProgress = Phase1TestProgress(phase1TestsInvited = true)
  )

  val Phase1TestsStarted = SubmittedProgress.copy(
    phase1TestProgress = Phase1TestProgress(phase1TestsInvited = true, phase1TestsStarted = true)
  )

  val Phase1TestsPassed = Phase1TestsStarted.copy(
    phase1TestProgress = Phase1TestProgress(phase1TestsCompleted = true, phase1TestsPassed = true)
  )

  val Phase1TestsFailed = SubmittedProgress.copy(
    phase1TestProgress = Phase1TestProgress(phase1TestsInvited = true, phase1TestsStarted = true, phase1TestsFailed = true)
  )

  val Phase2TestsPassed = SubmittedProgress.copy(
    phase1TestProgress = Phase1TestProgress(phase1TestsInvited = true, phase1TestsStarted = true,
      phase1TestsCompleted = true, phase1TestsPassed = true),
    phase2TestProgress = Phase2TestProgress(phase2TestsInvited = true, phase2TestsStarted = true,
      phase2TestsCompleted = true, phase2TestsPassed = true)
  )

  val Phase2TestsFailed = SubmittedProgress.copy(
    phase2TestProgress = Phase2TestProgress(phase2TestsInvited = true, phase2TestsStarted = true, phase2TestsFailed = true)
  )

  val Phase3TestsFailed = SubmittedProgress.copy(
    phase3TestProgress = Phase3TestProgress(phase3TestsInvited = true, phase3TestsStarted = true, phase3TestsFailed = true)
  )

  val Phase3TestsFailedCumulative = SubmittedProgress.copy(
    phase1TestProgress = Phase1TestProgress(phase1TestsInvited = true, phase1TestsStarted = true,
      phase1TestsCompleted = true, phase1TestsPassed = true),
    phase2TestProgress = Phase2TestProgress(phase2TestsInvited = true, phase2TestsStarted = true,
      phase2TestsCompleted = true, phase2TestsPassed = true),
    phase3TestProgress = Phase3TestProgress(phase3TestsInvited = true, phase3TestsStarted = true, phase3TestsFailed = true)
  )

  val Phase3TestsPassedCumulative = SubmittedProgress.copy(
    phase1TestProgress = Phase1TestProgress(phase1TestsInvited = true, phase1TestsStarted = true,
      phase1TestsCompleted = true, phase1TestsPassed = true),
    phase2TestProgress = Phase2TestProgress(phase2TestsInvited = true, phase2TestsStarted = true,
      phase2TestsCompleted = true, phase2TestsPassed = true),
    phase3TestProgress = Phase3TestProgress(phase3TestsInvited = true, phase3TestsStarted = true, phase3TestsPassed = true)
  )

  val FullProgress = Progress(
    personalDetails = true,
    schemePreferences = true,
    assistanceDetails = true,
    preview = true,
    startedQuestionnaire = true,
    diversityQuestionnaire = true,
    educationQuestionnaire = true,
    occupationQuestionnaire = true,
    submitted = true,
    phase1TestProgress = Phase1TestProgress(
      phase1TestsInvited = true,
      phase1TestsFirstReminder = true,
      phase1TestsSecondReminder = true,
      phase1TestsStarted = true,
      phase1TestsCompleted = true,
      phase1TestsResultsReady = true,
      phase1TestsResultsReceived = true,
      phase1TestsPassed = true
    ),
     phase2TestProgress = Phase2TestProgress(
      phase2TestsInvited = true,
      phase2TestsFirstReminder = true,
      phase2TestsSecondReminder = true,
      phase2TestsStarted = true,
      phase2TestsCompleted = true,
      phase2TestsResultsReady = true,
      phase2TestsResultsReceived = true,
      phase2TestsPassed = true
    ),
      phase3TestProgress = Phase3TestProgress(
      phase3TestsInvited = true,
      phase3TestsFirstReminder = true,
      phase3TestsSecondReminder = true,
      phase3TestsStarted = true,
      phase3TestsCompleted = true,
      phase3TestsResultsReceived = true,
      phase3TestsPassed = true
    ),
    exported = true, // TODO: redundant
    assessmentCentre = AssessmentCentre(
      scoresEntered = true,
      scoresAccepted = true,
      awaitingReevaluation = true,
      passed = true
    )
  )

  val Phase3TestsPassed = Progress(
    personalDetails = true,
    schemePreferences = true,
    assistanceDetails = true,
    preview = true,
    startedQuestionnaire = true,
    diversityQuestionnaire = true,
    educationQuestionnaire = true,
    occupationQuestionnaire = true,
    submitted = true,
    withdrawn = true,
    phase1TestProgress = Phase1TestProgress(
      phase1TestsInvited = true,
      phase1TestsFirstReminder = true,
      phase1TestsSecondReminder = true,
      phase1TestsStarted = true,
      phase1TestsCompleted = true,
      phase1TestsExpired = true,
      phase1TestsResultsReady = true,
      phase1TestsResultsReceived = true,
      phase1TestsPassed = true,
      phase1TestsFailed = true
    ),
    phase2TestProgress = Phase2TestProgress(
      phase2TestsInvited = true,
      phase2TestsFirstReminder = true,
      phase2TestsSecondReminder = true,
      phase2TestsStarted = true,
      phase2TestsCompleted = true,
      phase2TestsExpired = true,
      phase2TestsResultsReady = true,
      phase2TestsResultsReceived = true,
      phase2TestsPassed = true, phase2TestsFailed = true
    ),
    phase3TestProgress = Phase3TestProgress(phase3TestsInvited = true,
      phase3TestsStarted = true,
      phase3TestsCompleted = true,
      phase3TestsExpired = true,
      phase3TestsResultsReceived = true,
      phase3TestsPassed = true
    ),
    siftProgress = SiftProgress(
      siftEntered = true,
      siftCompleted = true
    ),
    exported = false, // TODO: redundant
    updateExported = false, // TODO: redundant
    assessmentCentre = AssessmentCentre(scoresEntered = true, scoresAccepted = true, awaitingReevaluation = true,
      passed = true)
  )

  val PersonalDetailsProgress = InitialProgress.copy(personalDetails = true)
  val SchemePreferencesProgress = PersonalDetailsProgress.copy(schemePreferences = true)
  val AssistanceDetailsProgress = SchemePreferencesProgress.copy(assistanceDetails = true)
  val QuestionnaireProgress = AssistanceDetailsProgress.copy(startedQuestionnaire = true, diversityQuestionnaire = true,
    educationQuestionnaire = true ,occupationQuestionnaire = true)
  val PreviewProgress = QuestionnaireProgress.copy(preview = true)
  val WithdrawnAfterSubmitProgress = SubmittedProgress.copy(withdrawn = true)
  val ExportedToParityProgress = Phase3TestsPassed.copy(exported = true)
  val UpdateExportedToParityProgress = ExportedToParityProgress.copy(updateExported = true)
  val SiftEntered = Phase3TestsPassed.copy(
    siftProgress = SiftProgress( siftEntered = true )
  )
}
