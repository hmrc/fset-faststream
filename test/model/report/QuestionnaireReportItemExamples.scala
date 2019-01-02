/*
 * Copyright 2019 HM Revenue & Customs
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
  val NoParentOccupation1 = QuestionnaireReportItem(Some("Male"), Some("Heterosexual/straight"), Some("Irish"),
      None, None, None, None, Some("No"), "SE-1", Some("W76-WIN"))
  val NoParentOccupation2 = QuestionnaireReportItem(Some("Female"), Some("Bisexual"), Some("Other White background"),
      None, None, None, None, Some("No"), "SE-2", Some("O33-OXF"))

  val questionnaire1 = newQuestionnaire
  val questionnaire2 = newQuestionnaire

  def newQuestionnaire =
    QuestionnaireReportItem(someRnd("Gender"), someRnd("Orientation"), someRnd("Ethnicity"),
      someRnd("EmploymentStatus"), someRnd("Occupation"), someRnd("(Self)Employed"), someRnd("CompanySize"),
      someRnd("SocioEconomicBackground"), rnd("SES"), someRnd("university"))

  def someRnd(prefix: String): Option[String] = Some(rnd(prefix))
  def rnd(prefix: String) = s"$prefix-${Random.nextInt(100)}"
}
