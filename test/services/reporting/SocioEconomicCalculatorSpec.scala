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
  val calculator = new SocioEconomicScoreCalculator {}

    "The socio-economic score calculator" should {

      "calculate the employment status size of unemployed" in {
        calculator.calculateEmploymentStatusSize(unemployed) must be()
      }

      "calculate the employment status size of unemployed but seeking work" in {
        calculator.calculateEmploymentStatusSize(unemployedSeekingWork) must be("N/A")
      }

      "calculate the employment status size of Unknown" in {
        calculator.calculateEmploymentStatusSize(prefersNotToSay) must be("N/A")
      }

      "calculate the employment status size of '7- Other employees" in {
        calculator.calculateEmploymentStatusSize(otherEmployees_7_Variant1) must be ("7- Other employees")
        calculator.calculateEmploymentStatusSize(otherEmployees_7_Variant2) must be ("7- Other employees")
      }

      "calculate the employment status size of '6- Supervisors" in {
        calculator.calculateEmploymentStatusSize(supervisors_6_Variant1) must be ("6- Supervisors")
        calculator.calculateEmploymentStatusSize(supervisors_6_Variant2) must be ("6- Supervisors")
      }

      "calculate the employment status size of '5- Managers-small organisations" in {
        calculator.calculateEmploymentStatusSize(managers_5_Variant1) must be ("5- Managers-small organisations")
        calculator.calculateEmploymentStatusSize(managers_5_Variant2) must be ("5- Managers-small organisations")
        calculator.calculateEmploymentStatusSize(managers_5_Variant2) must be ("5- Managers-small organisations")
      }

      "calculate the employment status size of '4- Managers-large organisations" in {
        calculator.calculateEmploymentStatusSize(managers_4_Variant1) must be ("4- Managers-large organisations")
        calculator.calculateEmploymentStatusSize(managers_4_Variant2) must be ("4- Managers-large organisations")
        calculator.calculateEmploymentStatusSize(managers_4_Variant2) must be ("4- Managers-large organisations")
      }

      "calculate the employment status size of '3- Self-employed, no employees" in {
        calculator.calculateEmploymentStatusSize(self_employed_3_Variant1) must be ("3- Self-employed, no employees")
        calculator.calculateEmploymentStatusSize(self_employed_3_Variant2) must be ("3- Self-employed, no employees")
        calculator.calculateEmploymentStatusSize(self_employed_3_Variant3) must be ("3- Self-employed, no employees")
      }

      "calculate the employment status size of '2- Employers-small organisations" in {
        calculator.calculateEmploymentStatusSize(employers_2_Variant1) must be ("2- Employers-small organisations")
        calculator.calculateEmploymentStatusSize(employers_2_Variant2) must be ("2- Employers-small organisations")
        calculator.calculateEmploymentStatusSize(employers_2_Variant3) must be ("2- Employers-small organisations")
      }

      "calculate the employment status size of '1- Employers-large organisations" in {
        calculator.calculateEmploymentStatusSize(employers_1_Variant1) must be ("1- Employers-large organisations")
        calculator.calculateEmploymentStatusSize(employers_1_Variant2) must be ("1- Employers-large organisations")
        calculator.calculateEmploymentStatusSize(employers_1_Variant3) must be ("1- Employers-large organisations")
      }

      "calculate the socio-economic score of unemployed" in {
        calculator.calculate(unemployed) must be("N/A")
      }

      "calculate the socio-economic score of unemployed but seeking work" in {
        calculator.calculate(unemployed) must be("N/A")
      }

      "calculate the employment status size of Unknown" in {
        calculator.calculateEmploymentStatusSize(prefersNotToSay) must be("N/A")
      }
    }

  object SocioEconomicCalculatorSpec {
    val otherEmployees_7_Variant1: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Traditional professional",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "I don't know/prefer not to say",
      "Did they supervise employees?" -> "No"
    )

    val otherEmployees_7_Variant2: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Modern professional",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "I don't know/prefer not to say",
      "Did they supervise employees?" -> "I don't know/prefer not to say"
    )


    val supervisors_6_Variant1: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Traditional professional",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "Yes"
    )

    val supervisors_6_Variant2: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Modern professional",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "Yes"
    )

    val managers_5_Variant1: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Senior managers and administrators",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "I don't know/prefer not to say"
    )

    val managers_5_Variant2: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Senior managers and administrators",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "Yes"
    )

    val managers_5_Variant3: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Senior managers and administrators",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "No"
    )

    val managers_4_Variant1: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Senior managers and administrators",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "I don't know/prefer not to say"
    )

    val managers_4_Variant2: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Senior managers and administrators",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "Yes"
    )

    val managers_4_Variant3: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Senior managers and administrators",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "No"
    )

    val self_employed_3_Variant1: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Senior managers and administrators",
      "Did they work as an employee or were they self-employed?" -> "Self-employed/freelancer without employees",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "No"
    )

    val self_employed_3_Variant2: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Modern professional",
      "Did they work as an employee or were they self-employed?" -> "Self-employed/freelancer without employees",
      "Which size would best describe their place of work?" -> "I don't know/prefer not to say",
      "Did they supervise employees?" -> "I don't know/prefer not to say"
    )

    val self_employed_3_Variant3: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Modern professional",
      "Did they work as an employee or were they self-employed?" -> "Self-employed/freelancer without employees",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "Yes"
    )

    val employers_2_Variant1: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Senior managers and administrators",
      "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "I don't know/prefer not to say"
    )

    val employers_2_Variant2: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Modern professional",
      "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "No"
    )

    val employers_2_Variant3: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Senior managers and administrators",
      "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "Yes"
    )

    val employers_1_Variant1: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Senior managers and administrators",
      "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "I don't know/prefer not to say"
    )

    val employers_1_Variant2: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Modern professional",
      "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "No"
    )

    val employers_1_Variant3: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Senior managers and administrators",
      "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "Yes"
    )

    val unemployed: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Unemployed"
    )

    val unemployedSeekingWork: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Unemployed but seeking work"
    )

    val prefersNotToSay: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Unknown"
    )
  }
}
