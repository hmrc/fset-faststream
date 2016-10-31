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

package model.command

import java.util.UUID

object ProgressResponseExamples {
  val Initial = ProgressResponse(applicationId = UUID.randomUUID().toString,
    personalDetails = false,
    schemePreferences = false,
    partnerGraduateProgrammes = false,
    assistanceDetails = false,
    preview = false,
    questionnaire = Nil,
    submitted = false,
    withdrawn = false,
    phase1ProgressResponse = Phase1ProgressResponse(
      phase1TestsInvited = false,
      phase1TestsStarted = false,
      phase1TestsCompleted = false,
      phase1TestsExpired = false,
      phase1TestsResultsReady = false,
      phase1TestsResultsReceived = false,
      phase1TestsPassed = false,
      phase1TestsFailed = false
    ),
    phase2ProgressResponse = Phase2ProgressResponse(phase2TestsInvited = false,
      phase2TestsStarted = false,
      phase2TestsCompleted = false,
      phase2TestsExpired = false,
      phase2TestsResultsReceived = false
    ),
    phase3ProgressResponse = Phase3ProgressResponse(phase3TestsInvited = false,
      phase3TestsStarted = false,
      phase3TestsCompleted = false,
      phase3TestsExpired = false,
      phase3TestsResultsReceived = false
    ),
    failedToAttend = false
  )
  val InProgress = Initial.copy(personalDetails = true)
  val InPersonalDetails = Initial.copy(personalDetails = true)
  val InSchemePreferences = InPersonalDetails.copy(schemePreferences = true)
  val InPartnerGraduateProgrammes = InSchemePreferences.copy(partnerGraduateProgrammes = true)
  val InAssistanceDetails = InPartnerGraduateProgrammes.copy(assistanceDetails = true)
  val InQuestionnaire = InAssistanceDetails.copy(questionnaire = List("start_questionnaire", "diversity_questionnaire",
    "education_questionnaire", "occupation_questionnaire"))
  val InPreview = InQuestionnaire.copy(preview = true)
  val InSubmit = InPreview.copy(submitted = true)
}
