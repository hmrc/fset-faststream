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

package models

import java.util.UUID

import connectors.exchange._

object ProgressResponseExamples {
  val Initial = ProgressResponse(applicationId = UUID.randomUUID().toString)

  val InProgress = Initial.copy(personalDetails = true)
  val InPersonalDetails = Initial.copy(personalDetails = true)
  val InSchemePreferencesDetails = InPersonalDetails.copy(schemePreferences = true)
  val InAssistanceDetails = InSchemePreferencesDetails.copy(assistanceDetails = true)
  val InQuestionnaire = InAssistanceDetails.copy(questionnaire = List("start_questionnaire", "diversity_questionnaire",
    "education_questionnaire", "occupation_questionnaire"))
  val InPreview = InQuestionnaire.copy(preview = true)
  val Submitted = InPreview.copy(submitted = true)
  val WithdrawnAfterSubmitted = Submitted.copy(withdrawn = true)

  val phase1TestsPassed = Submitted.copy(phase1ProgressResponse = Phase1ProgressResponse(
    phase1TestsInvited = true,
    phase1TestsFirstReminder = true,
    phase1TestsSecondReminder = true,
    phase1TestsStarted = true,
    phase1TestsCompleted = true,
    phase1TestsResultsReady = true,
    phase1TestsResultsReceived = true,
    phase1TestsPassed = true
  ))
  val phase2TestsPassed = phase1TestsPassed.copy(phase2ProgressResponse = Phase2ProgressResponse(
    phase2TestsInvited = true,
    phase2TestsFirstReminder = true,
    phase2TestsSecondReminder = true,
    phase2TestsStarted = true,
    phase2TestsCompleted = true,
    phase2TestsResultsReady = true,
    phase2TestsResultsReceived = true,
    phase2TestsPassed = true
  ))

  val phase3TestsPassed = phase2TestsPassed.copy(phase3ProgressResponse = Phase3ProgressResponse(
    phase3TestsInvited = true,
    phase3TestsFirstReminder = true,
    phase3TestsSecondReminder = true,
    phase3TestsStarted = true,
    phase3TestsCompleted = true,
    phase3TestsResultsReceived = true,
    phase3TestsPassed = true
  ))

  val siftEntered = phase3TestsPassed.copy(siftProgressResponse = SiftProgressResponse(
    siftEntered = true
  ))

  val siftExpired = phase3TestsPassed.copy(siftProgressResponse = SiftProgressResponse(
    siftEntered = true,
    siftExpired = true
  ))
}
