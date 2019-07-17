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

package services.reporting

import testkit.UnitSpec

class SocioEconomicCalculatorSpec extends UnitSpec {

  import SocioEconomicCalculatorSpec._
  val calculator = new SocioEconomicScoreCalculator {}

  "The socio-economic score calculator" should {
    "calculate the employment status size of unemployed" in {
      calculator.calculateEmploymentStatusSize(unemployed) must be(SocioEconomicCalculator.NotApplicable)
    }

    "calculate the employment status size of unemployed but seeking work" in {
      calculator.calculateEmploymentStatusSize(unemployedSeekingWork) must be(SocioEconomicCalculator.NotApplicable)
    }

    "calculate the employment status size of Unknown" in {
      calculator.calculateEmploymentStatusSize(prefersNotToSay) must be(SocioEconomicCalculator.NotApplicable)
    }

    "calculate the employment status size of '7- Other employees" in {
      calculator.calculateEmploymentStatusSize(otherEmployees_7_Variant1) must be (SocioEconomicCalculator.OtherEmployees)
      calculator.calculateEmploymentStatusSize(otherEmployees_7_Variant2) must be (SocioEconomicCalculator.OtherEmployees)
    }

    "calculate the employment status size of '6- Supervisors" in {
      calculator.calculateEmploymentStatusSize(supervisors_6_Variant1) must be (SocioEconomicCalculator.Supervisors)
      calculator.calculateEmploymentStatusSize(supervisors_6_Variant2) must be (SocioEconomicCalculator.Supervisors)
    }

    "calculate the employment status size of '5- Managers-small organisations" in {
      calculator.calculateEmploymentStatusSize(managers_5_Variant1) must be (SocioEconomicCalculator.ManagersSmallOrganisations)
      calculator.calculateEmploymentStatusSize(managers_5_Variant2) must be (SocioEconomicCalculator.ManagersSmallOrganisations)
      calculator.calculateEmploymentStatusSize(managers_5_Variant2) must be (SocioEconomicCalculator.ManagersSmallOrganisations)
    }

    "calculate the employment status size of '4- Managers-large organisations" in {
      calculator.calculateEmploymentStatusSize(managers_4_Variant1) must be (SocioEconomicCalculator.ManagersLargeOrganisations)
      calculator.calculateEmploymentStatusSize(managers_4_Variant2) must be (SocioEconomicCalculator.ManagersLargeOrganisations)
      calculator.calculateEmploymentStatusSize(managers_4_Variant2) must be (SocioEconomicCalculator.ManagersLargeOrganisations)
    }

    "calculate the employment status size of '3- Self-employed, no employees" in {
      calculator.calculateEmploymentStatusSize(self_employed_3_Variant1) must be (SocioEconomicCalculator.SelfEmployedNoEmployees)
      calculator.calculateEmploymentStatusSize(self_employed_3_Variant2) must be (SocioEconomicCalculator.SelfEmployedNoEmployees)
      calculator.calculateEmploymentStatusSize(self_employed_3_Variant3) must be (SocioEconomicCalculator.SelfEmployedNoEmployees)
    }

    "calculate the employment status size of '2- Employers-small organisations" in {
      calculator.calculateEmploymentStatusSize(employers_2_Variant1) must be (SocioEconomicCalculator.EmployersSmallOrganisations)
      calculator.calculateEmploymentStatusSize(employers_2_Variant2) must be (SocioEconomicCalculator.EmployersSmallOrganisations)
      calculator.calculateEmploymentStatusSize(employers_2_Variant3) must be (SocioEconomicCalculator.EmployersSmallOrganisations)
    }

    "calculate the employment status size of '1- Employers-large organisations" in {
      calculator.calculateEmploymentStatusSize(employers_1_Variant1) must be (SocioEconomicCalculator.EmployersLargeOrganisations)
      calculator.calculateEmploymentStatusSize(employers_1_Variant2) must be (SocioEconomicCalculator.EmployersLargeOrganisations)
      calculator.calculateEmploymentStatusSize(employers_1_Variant3) must be (SocioEconomicCalculator.EmployersLargeOrganisations)
    }

    "calculate the socio-economic score of unemployed" in {
      calculator.calculate(unemployed) must be("")
    }

    "calculate the socio-economic score of unemployed but seeking work" in {
      calculator.calculate(unemployed) must be("")
    }

    "calculate the socio-economic score of Unknown" in {
      calculator.calculate(prefersNotToSay) must be("")
    }

    "calculate the socio-economic score of modern professionals" in {
      calculator.calculate(modernProfessional_Variant1) must be("SE-1")
      calculator.calculate(modernProfessional_Variant2) must be("SE-1")
      calculator.calculate(modernProfessional_Variant3) must be("SE-1")
      calculator.calculate(modernProfessional_Variant6) must be("SE-1")
      calculator.calculate(modernProfessional_Variant7) must be("SE-1")
    }

    "calculate the socio-economic score of clerical" in {
      calculator.calculate(clerical_Variant1) must be("SE-1")
      calculator.calculate(clerical_Variant2) must be("SE-3")
      calculator.calculate(clerical_Variant3) must be("SE-3")
      calculator.calculate(clerical_Variant6) must be("SE-1")
      calculator.calculate(clerical_Variant7) must be("SE-2")
    }

    "calculate the socio-economic score of senior managers and administrators" in {
      calculator.calculate(managers_Variant1) must be("SE-1")
      calculator.calculate(managers_Variant2) must be("SE-3")
      calculator.calculate(managers_Variant3) must be("SE-3")
      calculator.calculate(managers_Variant4) must be("SE-1")
      calculator.calculate(managers_Variant5) must be("SE-1")
      calculator.calculate(managers_Variant6) must be("SE-1")
      calculator.calculate(managers_Variant7) must be("SE-1")
    }

    "calculate the socio-economic score of technical and craft occupations" in {
      calculator.calculate(technical_Variant1) must be("SE-1")
      calculator.calculate(technical_Variant2) must be("SE-3")
      calculator.calculate(technical_Variant3) must be("SE-3")
      calculator.calculate(technical_Variant6) must be("SE-4")
      calculator.calculate(technical_Variant7) must be("SE-4")
    }

    "calculate the socio-economic score of semi-routine manual occupations" in {
      calculator.calculate(semi_routine_Variant1) must be("SE-1")
      calculator.calculate(semi_routine_Variant2) must be("SE-3")
      calculator.calculate(semi_routine_Variant3) must be("SE-3")
      calculator.calculate(semi_routine_Variant6) must be("SE-4")
      calculator.calculate(semi_routine_Variant7) must be("SE-5")
    }

    "calculate the socio-economic score of routine manual and service occupations" in {
      calculator.calculate(routine_Variant1) must be("SE-1")
      calculator.calculate(routine_Variant2) must be("SE-3")
      calculator.calculate(routine_Variant3) must be("SE-3")
      calculator.calculate(routine_Variant6) must be("SE-4")
      calculator.calculate(routine_Variant7) must be("SE-5")
    }

    "calculate the socio-economic score of middle and junior managers occupations" in {
      calculator.calculate(middle_Variant1) must be("SE-1")
      calculator.calculate(middle_Variant2) must be("SE-3")
      calculator.calculate(middle_Variant3) must be("SE-3")
      calculator.calculate(middle_Variant6) must be("SE-1")
      calculator.calculate(middle_Variant7) must be("SE-1")
    }

    "calculate the socio-economic score of traditional professional occupations" in {
      calculator.calculate(traditional_Variant1) must be("SE-1")
      calculator.calculate(traditional_Variant2) must be("SE-1")
      calculator.calculate(traditional_Variant3) must be("SE-1")
      calculator.calculate(traditional_Variant4) must be("SE-1")
      calculator.calculate(traditional_Variant5) must be("SE-1")
    }
  }

  object SocioEconomicCalculatorSpec {
    val traditional_Variant5: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Traditional professional",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "I don't know/prefer not to say",
      "Did they supervise employees?" -> "No"
    )

    val traditional_Variant4: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Traditional professional",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "Yes"
    )

    val traditional_Variant3: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Traditional professional",
      "Did they work as an employee or were they self-employed?" -> "Self-employed/freelancer without employees",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "No"
    )

    val traditional_Variant2: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Traditional professional",
      "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "I don't know/prefer not to say"
    )

    val traditional_Variant1: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Traditional professional",
      "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "I don't know/prefer not to say"
    )

    val middle_Variant7: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Middle or junior managers",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "I don't know/prefer not to say",
      "Did they supervise employees?" -> "No"
    )

    val middle_Variant6: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Middle or junior managers",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "Yes"
    )

    val middle_Variant3: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Middle or junior managers",
      "Did they work as an employee or were they self-employed?" -> "Self-employed/freelancer without employees",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "No"
    )

    val middle_Variant2: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Middle or junior managers",
      "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "I don't know/prefer not to say"
    )

    val middle_Variant1: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Middle or junior managers",
      "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "I don't know/prefer not to say"
    )

    val routine_Variant7: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Routine manual and service",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "I don't know/prefer not to say",
      "Did they supervise employees?" -> "No"
    )

    val routine_Variant6: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Routine manual and service",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "Yes"
    )

    val routine_Variant3: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Routine manual and service",
      "Did they work as an employee or were they self-employed?" -> "Self-employed/freelancer without employees",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "No"
    )

    val routine_Variant2: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Routine manual and service",
      "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "I don't know/prefer not to say"
    )

    val routine_Variant1: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Routine manual and service",
      "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "I don't know/prefer not to say"
    )

    val semi_routine_Variant7: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Semi-routine manual and service",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "I don't know/prefer not to say",
      "Did they supervise employees?" -> "No"
    )

    val semi_routine_Variant6: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Semi-routine manual and service",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "Yes"
    )

    val semi_routine_Variant3: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Semi-routine manual and service",
      "Did they work as an employee or were they self-employed?" -> "Self-employed/freelancer without employees",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "No"
    )

    val semi_routine_Variant2: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Semi-routine manual and service",
      "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "I don't know/prefer not to say"
    )

    val semi_routine_Variant1: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Semi-routine manual and service",
      "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "I don't know/prefer not to say"
    )

    val technical_Variant7: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Technical and craft",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "I don't know/prefer not to say",
      "Did they supervise employees?" -> "No"
    )

    val technical_Variant6: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Technical and craft",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "Yes"
    )

    val technical_Variant3: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Technical and craft",
      "Did they work as an employee or were they self-employed?" -> "Self-employed/freelancer without employees",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "No"
    )

    val technical_Variant2: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Technical and craft",
      "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "I don't know/prefer not to say"
    )

    val technical_Variant1: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Technical and craft",
      "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "I don't know/prefer not to say"
    )

    val managers_Variant1: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Senior managers and administrators",
      "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "I don't know/prefer not to say"
    )

    val managers_Variant2: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Senior managers and administrators",
      "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "I don't know/prefer not to say"
    )

    val managers_Variant3: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Senior managers and administrators",
      "Did they work as an employee or were they self-employed?" -> "Self-employed/freelancer without employees",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "No"
    )

    val managers_Variant4: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Senior managers and administrators",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "I don't know/prefer not to say"
    )

    val managers_Variant5: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Senior managers and administrators",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "I don't know/prefer not to say"
    )

    val managers_Variant6: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Senior managers and administrators",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "Yes"
    )

    val managers_Variant7: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Senior managers and administrators",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "I don't know/prefer not to say",
      "Did they supervise employees?" -> "No"
    )

    val clerical_Variant1: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Clerical (office work) and intermediate",
      "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "No"
    )

    val clerical_Variant2: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Clerical (office work) and intermediate",
      "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "I don't know/prefer not to say"
    )

    val clerical_Variant3: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Clerical (office work) and intermediate",
      "Did they work as an employee or were they self-employed?" -> "Self-employed/freelancer without employees",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "No"
    )

    val clerical_Variant6: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Clerical (office work) and intermediate",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "Yes"
    )

    val clerical_Variant7: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Clerical (office work) and intermediate",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "I don't know/prefer not to say",
      "Did they supervise employees?" -> "No"
    )

    val modernProfessional_Variant1: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Modern professional",
      "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "No"
    )

    val modernProfessional_Variant2: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Modern professional",
      "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "I don't know/prefer not to say"
    )

    val modernProfessional_Variant3: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Modern professional",
      "Did they work as an employee or were they self-employed?" -> "Self-employed/freelancer without employees",
      "Which size would best describe their place of work?" -> "Large (over 24 employees)",
      "Did they supervise employees?" -> "No"
    )

    val modernProfessional_Variant6: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Modern professional",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "Small (1 - 24 employees)",
      "Did they supervise employees?" -> "Yes"
    )

    val modernProfessional_Variant7: Map[String, String] = Map(
      "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Modern professional",
      "Did they work as an employee or were they self-employed?" -> "Employee",
      "Which size would best describe their place of work?" -> "I don't know/prefer not to say",
      "Did they supervise employees?" -> "No"
    )

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
