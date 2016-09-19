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

import connectors.exchange.{ AssessmentCentre, AssessmentScores, ProgressResponse }
import play.api.libs.json.Json

import scala.language.implicitConversions

case class Progress(personalDetails: Boolean,
  schemePreferences: Boolean,
  partnerGraduateProgrammes: Boolean,
  assistanceDetails: Boolean,
  preview: Boolean,
  startedQuestionnaire: Boolean,
  diversityQuestionnaire: Boolean,
  educationQuestionnaire: Boolean,
  occupationQuestionnaire: Boolean,
  submitted: Boolean,
  withdrawn: Boolean = false,
  phase1TestsInvited: Boolean = false,
  phase1TestsStarted: Boolean = false,
  phase1TestsCompleted: Boolean = false,
  phase1TestsExpired: Boolean = false,
  phase1TestsResultsReceived: Boolean = false,
  failedToAttend: Boolean = false,
  assessmentScores: AssessmentScores,
  assessmentCentre: AssessmentCentre
)

object Progress {
  implicit val assessmentScoresFormat = Json.format[AssessmentScores]
  implicit val assessmentCentreFormat = Json.format[AssessmentCentre]
  implicit val progressFormat = Json.format[Progress]

  implicit def fromProgressRespToAppProgress(progressResponse: ProgressResponse): Progress =
    Progress(
      personalDetails = progressResponse.personalDetails,
      schemePreferences = progressResponse.schemePreferences,
      partnerGraduateProgrammes = progressResponse.partnerGraduateProgrammes,
      assistanceDetails = progressResponse.assistanceDetails,
      preview = progressResponse.preview,
      startedQuestionnaire = progressResponse.questionnaire.contains("start_questionnaire"),
      diversityQuestionnaire = progressResponse.questionnaire.contains("diversity_questionnaire"),
      educationQuestionnaire = progressResponse.questionnaire.contains("education_questionnaire"),
      occupationQuestionnaire = progressResponse.questionnaire.contains("occupation_questionnaire"),
      submitted = progressResponse.submitted,
      withdrawn = progressResponse.withdrawn,
      phase1TestsInvited = progressResponse.onlineTest.onlineTestInvited,
      phase1TestsStarted  = progressResponse.onlineTest.onlineTestStarted,
      phase1TestsCompleted = progressResponse.onlineTest.onlineTestCompleted,
      phase1TestsExpired= progressResponse.onlineTest.onlineTestExpired,
      phase1TestsResultsReceived = false, // TODO replace once retrieve is implemented
      failedToAttend = progressResponse.failedToAttend,
      assessmentScores = progressResponse.assessmentScores,
      assessmentCentre = progressResponse.assessmentCentre
    )
}
