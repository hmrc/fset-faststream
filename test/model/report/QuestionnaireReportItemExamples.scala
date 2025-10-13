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

import scala.util.Random

object QuestionnaireReportItemExamples {
  val NoParentOccupation1 = QuestionnaireReportItem(sex = Some("Male"), sexualOrientation = Some("Heterosexual/straight"),
    ethnicity = Some("Irish"), isEnglishYourFirstLanguage = Some("Yes"),
    parentEmploymentStatus = None, parentOccupation = None, parentTypeOfWorkAtAge14 = None, parentEmployedOrSelf = None,
    parentCompanySize = None, parentSuperviseEmployees = None, lowerSocioEconomicBackground = Some("No"), socioEconomicScore = "SE-1",
    university = Some("W76-WIN"), categoryOfDegree = Some("Biosciences"), degreeType = Some("BSc/MSc/Eng"), postgradUniversity = None,
    postgradCategoryOfDegree = None, postgradDegreeType = None)
  val NoParentOccupation2 = QuestionnaireReportItem(sex = Some("Female"), sexualOrientation = Some("Bisexual"),
    ethnicity = Some("Other White background"), isEnglishYourFirstLanguage = Some("Yes"),
    parentEmploymentStatus = None, parentOccupation = None, parentTypeOfWorkAtAge14 = None, parentEmployedOrSelf = None,
    parentCompanySize = None, parentSuperviseEmployees = None, lowerSocioEconomicBackground = Some("No"), socioEconomicScore = "SE-2",
    university = Some("O33-OXF"), categoryOfDegree = Some("Biosciences"), degreeType = Some("BSc/MSc/Eng"), postgradUniversity = None,
    postgradCategoryOfDegree = None, postgradDegreeType = None)

  val questionnaire1 = newQuestionnaire
  val questionnaire2 = newQuestionnaire

  def newQuestionnaire =
    QuestionnaireReportItem(someRnd("Sex"), someRnd("Orientation"), someRnd("Ethnicity"),
      isEnglishYourFirstLanguage = Some("Yes"),
      someRnd("EmploymentStatus"), someRnd("Occupation"), someRnd("TypeOfWork"), someRnd("(Self)Employed"),
      someRnd("CompanySize"), Some("Yes"),
      someRnd("SocioEconomicBackground"), rnd("SES"),
      someRnd("university"), someRnd("categoryOfDegree"), someRnd("degreeType"),
      postgradUniversity = None, postgradCategoryOfDegree = None, postgradDegreeType = None
    )

  def someRnd(prefix: String): Option[String] = Some(rnd(prefix))
  def rnd(prefix: String) = s"$prefix-${Random.nextInt(100)}"
}
