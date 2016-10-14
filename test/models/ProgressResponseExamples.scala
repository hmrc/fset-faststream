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

import java.util.UUID

import connectors.exchange.{ Phase1ProgressResponse, ProgressResponse }

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
    Phase1ProgressResponse()
  )
  val InProgress = Initial.copy(personalDetails = true)
  val InPersonalDetails = Initial.copy(personalDetails = true)
  val InSchemePreferencesDetails = InPersonalDetails.copy(schemePreferences = true)
  val InPartnerGraduateProgrammes = InSchemePreferencesDetails.copy(partnerGraduateProgrammes = true)
  val InAssistanceDetails = InPartnerGraduateProgrammes.copy(assistanceDetails = true)
  val InQuestionnaire = InAssistanceDetails.copy(questionnaire = List("start_questionnaire", "diversity_questionnaire",
    "education_questionnaire", "occupation_questionnaire"))
  val InPreview = InQuestionnaire.copy(preview = true)
  val Submitted = InPreview.copy(submitted = true)
  val WithdrawnAfterSubmitted = Submitted.copy(withdrawn = true)
}
