/*
 * Copyright 2020 HM Revenue & Customs
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

import scala.util.Random

object QuestionnaireReportItemExamples {
  val NoParentOccupation1 = QuestionnaireReportItem(gender = Some("Male"), sexualOrientation = Some("Heterosexual/straight"),
    ethnicity = Some("Irish"), isEnglishYourFirstLanguage = Some("Yes"), parentEmploymentStatus = None, parentOccupation = None,
    parentEmployedOrSelf = None, parentCompanySize = None, lowerSocioEconomicBackground = Some("No"), socioEconomicScore = "SE-1",
    university = Some("W76-WIN"))
  val NoParentOccupation2 = QuestionnaireReportItem(gender = Some("Female"), sexualOrientation = Some("Bisexual"),
    ethnicity = Some("Other White background"), isEnglishYourFirstLanguage = Some("Yes"), parentEmploymentStatus = None, parentOccupation = None,
    parentEmployedOrSelf = None, parentCompanySize = None, lowerSocioEconomicBackground = Some("No"), socioEconomicScore = "SE-2",
    university = Some("O33-OXF"))

  val questionnaire1 = newQuestionnaire
  val questionnaire2 = newQuestionnaire

  def newQuestionnaire =
    QuestionnaireReportItem(someRnd("Gender"), someRnd("Orientation"), someRnd("Ethnicity"),
      isEnglishYourFirstLanguage = Some("Yes"),
      someRnd("EmploymentStatus"), someRnd("Occupation"), someRnd("(Self)Employed"), someRnd("CompanySize"),
      someRnd("SocioEconomicBackground"), rnd("SES"), someRnd("university"))

  def someRnd(prefix: String): Option[String] = Some(rnd(prefix))
  def rnd(prefix: String) = s"$prefix-${Random.nextInt(100)}"
}
