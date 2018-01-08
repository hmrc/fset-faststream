/*
 * Copyright 2018 HM Revenue & Customs
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

package model.report

import model.persisted.SchemeEvaluationResult
import model.{ApplicationRoute, EvaluationResults, SchemeId}

object ApplicationForDiversityReportItemExamples {

  val Example1 = ApplicationForDiversityReportItem(Some("phase1_tests_completed"), ApplicationRoute.Faststream,
      List(SchemeId("DiplomaticService"), SchemeId("Commercial")), Some("No"), Some(false), Some("No"), Some("No"),
      Some(CivilServiceExperienceDetailsReportItem(Some("Yes"), Some("Yes"), Some("No"), Some("No"), Some("No"), Some(""))),
    List(SchemeEvaluationResult(SchemeId("DiplomaticService"), EvaluationResults.Green.toString),
      SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)))

  val Example2 =
    ApplicationForDiversityReportItem(Some("submitted"), ApplicationRoute.Faststream,
      List(SchemeId("DiplomaticServiceEconomics"), SchemeId("Commercial"), SchemeId("GovernmentCommunicationService"),
        SchemeId("European")), Some("Yes"), Some(true), Some("Yes"), Some("No"),
      Some(CivilServiceExperienceDetailsReportItem(Some("Yes"), Some("Yes"), Some("No"), Some("Yes"), Some("No"),
        Some("fastPass-101"))),
      List(SchemeEvaluationResult(SchemeId("DiplomaticService"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("GovernmentCommunicationService"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("European"), EvaluationResults.Green.toString)))
}
