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

package services.reporting

import org.scalatestplus.play.PlaySpec

class SocioEconomicCalculatorSpec extends PlaySpec {

  import SocioEconomicCalculatorSpec._
  val calculator = new SocioEconomicScoreCalculatorTrait {}

  "The employment status/size calculator" should {

    "calculate the score of a self employed with 24 or more employees" in {
      calculator.calculateEmploymentStatusSize(employersLargeOrnanisations) must be(calculator.EmployersLargeOrnanisations)
    }

    "calculate the score of a self employed with less than 24 employees" in {
      calculator.calculateEmploymentStatusSize(employersSmallOrganisations) must be(calculator.EmployersSmallOrganisations)
    }

    "calculate the score of a self employed/freelancer without employees" in {
      calculator.calculateEmploymentStatusSize(selfEmployedNoEmployees) must be(calculator.SelfEmployedNoEmployees)
    }

    "calculate the score of managers of large organizations" in {
      calculator.calculateEmploymentStatusSize(managersLargeOrganisations) must be(calculator.ManagersLargeOrganisations)
    }

    "calculate the score of managers of small organizations" in {
      calculator.calculateEmploymentStatusSize(managersSmallOrganisations) must be(calculator.ManagersSmallOrganisations)
    }

    "calculate the score of supervisors" in {
      calculator.calculateEmploymentStatusSize(supervisors) must be(calculator.Supervisors)
    }

    "calculate the score of other employees" in {
      calculator.calculateEmploymentStatusSize(otherEmployees) must be(calculator.OtherEmployees)
    }

    "calculate the score of unemployed as a N/A" in {
      calculator.calculateEmploymentStatusSize(unemployed) must be(calculator.NotApplicable)
    }

    "calculate the score of unemployed but seeking work" in {
      calculator.calculateEmploymentStatusSize(unemployedSeekingWork) must be(calculator.NotApplicable)
    }

    "calculate the score of Unknown" in {
      calculator.calculateEmploymentStatusSize(prefersNotToSay) must be(calculator.NotApplicable)
    }

    "The socio-economic score calculator" should {

      "calculate the score of a self employed with 24 or more employees on a Modern professional occupation" in {
        calculator.calculate(employersLargeOrnanisations) must be("SE-1")
      }

      "calculate the score of a self employed with less than 24 employees on a Clerical and intermediate occupation" in {
        calculator.calculate(employersSmallOrganisations) must be("SE-3")
      }

      "calculate the score of a self employed/freelancer without employees on a Technical and craft occupation" in {
        calculator.calculate(selfEmployedNoEmployees) must be("SE-3")
      }

      "calculate the score of managers of large organizations on a Senior managers and administrators occupation" in {
        calculator.calculate(managersLargeOrganisations) must be("SE-1")
      }

      "calculate the score of managers of small organizations on a Senior managers and administrators occupation" in {
        calculator.calculate(managersSmallOrganisations) must be("SE-1")
      }

      "calculate the score of supervisors on a Semi-routine manual and service occupation" in {
        calculator.calculate(supervisors) must be("SE-4")
      }

      "calculate the score of other employees on a routine manual and service occupation" in {
        calculator.calculate(otherEmployees) must be("SE-5")
      }

      "calculate the score of unemployed" in {
        calculator.calculate(unemployed) must be("N/A")
      }

      "calculate the score of unemployed but seeking work" in {
        calculator.calculate(unemployedSeekingWork) must be("N/A")
      }

      "calculate the score of Unknown" in {
        calculator.calculate(prefersNotToSay) must be("N/A")
      }
    }
  }

  object SocioEconomicCalculatorSpec {
    val employersLargeOrnanisations: Map[String, String] = Map(
      "Parent/guardian work status" -> "Employed",
      "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
      "Which type of occupation did they have?" -> "Modern professional",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)"
    )

    val employersSmallOrganisations: Map[String, String] = Map(
      "Parent/guardian work status" -> "Employed",
      "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
      "Which type of occupation did they have?" -> "Clerical and intermediate",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)"
    )

    val selfEmployedNoEmployees: Map[String, String] = Map(
      "Parent/guardian work status" -> "Employed",
      "Did they work as an employee or were they self-employed?" -> "Self-employed/freelancer without employees",
      "Which type of occupation did they have?" -> "Technical and craft"
    )

    val managersLargeOrganisations: Map[String, String] = Map(
      "Parent/guardian work status" -> "Employed",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which type of occupation did they have?" -> "Senior managers and administrators",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)"
    )

    val managersSmallOrganisations: Map[String, String] = Map(
      "Parent/guardian work status" -> "Employed",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which type of occupation did they have?" -> "Senior managers and administrators",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)"
    )

    val supervisors: Map[String, String] = Map(
      "Parent/guardian work status" -> "Employed",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which type of occupation did they have?" -> "Semi-routine manual and service",
      "Which size would best describe their place of work?" -> "N/A",
      "Did they supervise any other employees?" -> "Yes"
    )

    val otherEmployees: Map[String, String] = Map(
      "Parent/guardian work status" -> "Employed",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which type of occupation did they have?" -> "Routine manual and service",
      "Which size would best describe their place of work?" -> "N/A",
      "Did they supervise any other employees?" -> "No"
    )

    val unemployed: Map[String, String] = Map(
      "Which type of occupation did they have?" -> "Unemployed"
    )

    val unemployedSeekingWork: Map[String, String] = Map(
      "Which type of occupation did they have?" -> "Unemployed but seeking work"
    )

    val prefersNotToSay: Map[String, String] = Map(
      "Which type of occupation did they have?" -> "Unknown"
    )
  }

}
