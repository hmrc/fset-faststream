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

import connectors.exchange.ProgressResponse

object ProgressResponseExamples {
  val Initial = ProgressResponse(UniqueIdentifier(UUID.randomUUID().toString), false, false, false, false, Nil, false, false, false,
    false, false, false, false, false, false, false, false, false, false)
  val InProgress = Initial.copy(personalDetails = true)
  val InPersonalDetails = Initial.copy(personalDetails = true)
  val InFrameworkDetails = InPersonalDetails.copy(schemePreferences = true)
  val InAssistanceDetails = InFrameworkDetails.copy(assistanceDetails = true)
  val InQuestionnaire = InAssistanceDetails.copy(questionnaire = List("start_questionnaire", "diversity_questionnaire",
    "education_questionnaire", "occupation_questionnaire"))
  val InPreview = InQuestionnaire.copy(preview = true)
}
