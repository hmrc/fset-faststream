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

package model.command

import java.util.UUID

object ProgressResponseExamples {
  val Initial = ProgressResponse(
    applicationId = UUID.randomUUID().toString,
    questionnaire = Nil,
    phase1ProgressResponse = Phase1ProgressResponse(),
    phase2ProgressResponse = Phase2ProgressResponse(),
    phase3ProgressResponse = Phase3ProgressResponse()
  )

  val InProgress: ProgressResponse = Initial.copy(personalDetails = true)
  val InPersonalDetails: ProgressResponse = Initial.copy(personalDetails = true)
  val InSchemePreferences: ProgressResponse = InPersonalDetails.copy(schemePreferences = true)
  val InPartnerGraduateProgrammes: ProgressResponse = InSchemePreferences.copy(partnerGraduateProgrammes = true)
  val InAssistanceDetails: ProgressResponse = InPartnerGraduateProgrammes.copy(assistanceDetails = true)
  val InQuestionnaire: ProgressResponse = InAssistanceDetails.copy(questionnaire = List("start_questionnaire", "diversity_questionnaire",
    "education_questionnaire", "occupation_questionnaire"))
  val InPreview: ProgressResponse = InQuestionnaire.copy(preview = true)
  val InSubmit: ProgressResponse = InPreview.copy(submitted = true)
}
