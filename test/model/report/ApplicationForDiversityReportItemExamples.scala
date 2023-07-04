/*
 * Copyright 2023 HM Revenue & Customs
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
import model.{ApplicationRoute, EvaluationResults, Schemes}

object ApplicationForDiversityReportItemExamples extends Schemes {

  val Example1 = ApplicationForDiversityReportItem(Some("phase1_tests_completed"), ApplicationRoute.Faststream,
    List(DiplomaticAndDevelopment, Commercial), disability = Some("No"), gis = Some(false),
    onlineAdjustments = Some("No"), assessmentCentreAdjustments = Some("No"),
    Some(CivilServiceExperienceDetailsReportItem(
      isCivilServant = Some("Yes"), isEDIP = Some("Yes"), edipYear = Some("2018"), isSDIP = Some("Yes"), sdipYear = Some("2019"),
      otherInternship = Some("Yes"), otherInternshipName = Some("Name"), otherInternshipYear = Some("2020"),
      fastPassCertificate = Some("1234567")
    )),
    currentSchemeStatus = List(SchemeEvaluationResult(DiplomaticAndDevelopment, EvaluationResults.Green.toString),
      SchemeEvaluationResult(Commercial, EvaluationResults.Green.toString)))

  val Example2 = ApplicationForDiversityReportItem(Some("submitted"), ApplicationRoute.Faststream,
    List(DiplomaticAndDevelopmentEconomics, Commercial, GovernmentCommunicationService, Property),
    disability = Some("Yes"), gis = Some(true), onlineAdjustments = Some("Yes"), assessmentCentreAdjustments = Some("No"),
    Some(CivilServiceExperienceDetailsReportItem(
      isCivilServant = Some("Yes"), isEDIP = Some("No"), edipYear = None, isSDIP = Some("No"), sdipYear = None,
      otherInternship = Some("No"), otherInternshipName = None, otherInternshipYear = None, fastPassCertificate = Some("fastPass-101")
    )),
    currentSchemeStatus = List(SchemeEvaluationResult(DiplomaticAndDevelopment, EvaluationResults.Green.toString),
      SchemeEvaluationResult(Commercial, EvaluationResults.Green.toString),
      SchemeEvaluationResult(GovernmentCommunicationService, EvaluationResults.Green.toString),
      SchemeEvaluationResult(Property, EvaluationResults.Green.toString)))
}
