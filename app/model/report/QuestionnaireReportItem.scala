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

import play.api.libs.json.{Json, OFormat}

case class QuestionnaireReportItem(
                                    sex: Option[String],
                                    sexualOrientation: Option[String],
                                    ethnicity: Option[String],
                                    isEnglishYourFirstLanguage: Option[String],
                                    parentEmploymentStatus: Option[String],
                                    parentOccupation: Option[String],
                                    parentTypeOfWorkAtAge14: Option[String],
                                    parentEmployedOrSelf: Option[String],
                                    parentCompanySize: Option[String],
                                    parentSuperviseEmployees: Option[String],
                                    lowerSocioEconomicBackground: Option[String],
                                    socioEconomicScore: String,
                                    university: Option[String],
                                    categoryOfDegree: Option[String],
                                    degreeType: Option[String],
                                    postgradUniversity: Option[String],
                                    postgradCategoryOfDegree: Option[String],
                                    postgradDegreeType: Option[String]
                                  ) {
  override def toString =
    "(" +
      s"sex=$sex," +
      s"sexualOrientation=$sexualOrientation," +
      s"ethnicity=$ethnicity," +
      s"isEnglishYourFirstLanguage=$isEnglishYourFirstLanguage," +
      s"parentEmploymentStatus=$parentEmploymentStatus," +
      s"parentOccupation=$parentOccupation," +
      s"parentTypeOfWorkAtAge14=$parentTypeOfWorkAtAge14," +
      s"parentEmployedOrSelf=$parentEmployedOrSelf," +
      s"parentCompanySize=$parentCompanySize," +
      s"parentSuperviseEmployees=$parentSuperviseEmployees," +
      s"lowerSocioEconomicBackground=$lowerSocioEconomicBackground," +
      s"socioEconomicScore=$socioEconomicScore," +
      s"university=$university," +
      s"categoryOfDegree=$categoryOfDegree," +
      s"postgradUniversity=$postgradUniversity," +
      s"postgradCategoryOfDegree=$postgradCategoryOfDegree," +
      s"postgradDegreeType=$postgradDegreeType" +
      s")"
}

object QuestionnaireReportItem {
  implicit val questionnaireReportItemFormat: OFormat[QuestionnaireReportItem] = Json.format[QuestionnaireReportItem]
}
