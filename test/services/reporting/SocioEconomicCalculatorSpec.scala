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

package services.reporting

import repositories.application.DiversityQuestionsText
import testkit.UnitSpec

class SocioEconomicCalculatorSpec extends UnitSpec with DiversityQuestionsText {

  import SocioEconomicCalculatorSpec._
  val calculator = new SocioEconomicScoreCalculator()

  "The socio-economic score calculator" should {
    "calculate the employment status size of long term unemployed" in {
      calculator.calculateEmploymentStatusSize(longTermUnemployed) mustBe 0
    }

    "calculate the employment status size of unemployed but seeking work" in {
      calculator.calculateEmploymentStatusSize(unemployedSeekingWork) mustBe SocioEconomicCalculator.NotApplicable
    }

    "calculate the employment status size of Retired" in {
      calculator.calculateEmploymentStatusSize(retired) mustBe SocioEconomicCalculator.NotApplicable
    }

    "calculate the employment status size of Unknown" in {
      calculator.calculateEmploymentStatusSize(prefersNotToSay) mustBe SocioEconomicCalculator.NotApplicable
    }

    "calculate the employment status size of '7- Other employees" in {
      calculator.calculateEmploymentStatusSize(otherEmployees_7_Variant1) mustBe SocioEconomicCalculator.OtherEmployees
      calculator.calculateEmploymentStatusSize(otherEmployees_7_Variant2) mustBe SocioEconomicCalculator.OtherEmployees
    }

    "calculate the employment status size of '6- Supervisors" in {
      calculator.calculateEmploymentStatusSize(supervisors_6_Variant1) mustBe SocioEconomicCalculator.Supervisors
      calculator.calculateEmploymentStatusSize(supervisors_6_Variant2) mustBe SocioEconomicCalculator.Supervisors
    }

    "calculate the employment status size of '5- Managers-small organisations" in {
      calculator.calculateEmploymentStatusSize(managers_5_Variant1) mustBe SocioEconomicCalculator.ManagersSmallOrganisations
      calculator.calculateEmploymentStatusSize(managers_5_Variant2) mustBe SocioEconomicCalculator.ManagersSmallOrganisations
      calculator.calculateEmploymentStatusSize(managers_5_Variant2) mustBe SocioEconomicCalculator.ManagersSmallOrganisations
    }

    "calculate the employment status size of '4- Managers-large organisations" in {
      calculator.calculateEmploymentStatusSize(managers_4_Variant1) mustBe SocioEconomicCalculator.ManagersLargeOrganisations
      calculator.calculateEmploymentStatusSize(managers_4_Variant2) mustBe SocioEconomicCalculator.ManagersLargeOrganisations
      calculator.calculateEmploymentStatusSize(managers_4_Variant2) mustBe SocioEconomicCalculator.ManagersLargeOrganisations
    }

    "calculate the employment status size of '3- Self-employed, no employees" in {
      calculator.calculateEmploymentStatusSize(self_employed_3_Variant1) mustBe SocioEconomicCalculator.SelfEmployedNoEmployees
      calculator.calculateEmploymentStatusSize(self_employed_3_Variant2) mustBe SocioEconomicCalculator.SelfEmployedNoEmployees
      calculator.calculateEmploymentStatusSize(self_employed_3_Variant3) mustBe SocioEconomicCalculator.SelfEmployedNoEmployees
    }

    "calculate the employment status size of '2- Employers-small organisations" in {
      calculator.calculateEmploymentStatusSize(employers_2_Variant1) mustBe SocioEconomicCalculator.EmployersSmallOrganisations
      calculator.calculateEmploymentStatusSize(employers_2_Variant2) mustBe SocioEconomicCalculator.EmployersSmallOrganisations
      calculator.calculateEmploymentStatusSize(employers_2_Variant3) mustBe SocioEconomicCalculator.EmployersSmallOrganisations
    }

    "calculate the employment status size of '1- Employers-large organisations" in {
      calculator.calculateEmploymentStatusSize(employers_1_Variant1) mustBe SocioEconomicCalculator.EmployersLargeOrganisations
      calculator.calculateEmploymentStatusSize(employers_1_Variant2) mustBe SocioEconomicCalculator.EmployersLargeOrganisations
      calculator.calculateEmploymentStatusSize(employers_1_Variant3) mustBe SocioEconomicCalculator.EmployersLargeOrganisations
    }

    "calculate the socio-economic score of long term unemployed" in {
      calculator.calculate(longTermUnemployed) mustBe "SE-5"
    }

    "calculate the socio-economic score of unemployed but seeking work" in {
      calculator.calculate(unemployedSeekingWork) mustBe ""
    }

    "calculate the socio-economic score of retired" in {
      calculator.calculate(retired) mustBe ""
    }

    "calculate the socio-economic score of Unknown" in {
      calculator.calculate(prefersNotToSay) mustBe ""
    }

    "calculate the socio-economic score of modern professionals" in {
      calculator.calculate(modernProfessional_Variant1) mustBe "SE-1"
      calculator.calculate(modernProfessional_Variant2) mustBe "SE-1"
      calculator.calculate(modernProfessional_Variant3) mustBe "SE-1"
      calculator.calculate(modernProfessional_Variant6) mustBe "SE-1"
      calculator.calculate(modernProfessional_Variant7) mustBe "SE-1"
    }

    "calculate the socio-economic score of clerical" in {
      calculator.calculate(clerical_Variant1) mustBe "SE-1"
      calculator.calculate(clerical_Variant2) mustBe "SE-3"
      calculator.calculate(clerical_Variant3) mustBe "SE-3"
      calculator.calculate(clerical_Variant6) mustBe "SE-1"
      calculator.calculate(clerical_Variant7) mustBe "SE-2"
    }

    "calculate the socio-economic score of senior managers and administrators" in {
      calculator.calculate(managers_Variant1) mustBe "SE-1"
      calculator.calculate(managers_Variant2) mustBe "SE-3"
      calculator.calculate(managers_Variant3) mustBe "SE-3"
      calculator.calculate(managers_Variant4) mustBe "SE-1"
      calculator.calculate(managers_Variant5) mustBe "SE-1"
      calculator.calculate(managers_Variant6) mustBe "SE-1"
      calculator.calculate(managers_Variant7) mustBe "SE-1"
    }

    "calculate the socio-economic score of technical and craft occupations" in {
      calculator.calculate(technical_Variant1) mustBe "SE-1"
      calculator.calculate(technical_Variant2) mustBe "SE-3"
      calculator.calculate(technical_Variant3) mustBe "SE-3"
      calculator.calculate(technical_Variant6) mustBe "SE-4"
      calculator.calculate(technical_Variant7) mustBe "SE-4"
    }

    "calculate the socio-economic score of semi-routine manual occupations" in {
      calculator.calculate(semi_routine_Variant1) mustBe "SE-1"
      calculator.calculate(semi_routine_Variant2) mustBe "SE-3"
      calculator.calculate(semi_routine_Variant3) mustBe "SE-3"
      calculator.calculate(semi_routine_Variant6) mustBe "SE-4"
      calculator.calculate(semi_routine_Variant7) mustBe "SE-5"
    }

    "calculate the socio-economic score of routine manual and service occupations" in {
      calculator.calculate(routine_Variant1) mustBe "SE-1"
      calculator.calculate(routine_Variant2) mustBe "SE-3"
      calculator.calculate(routine_Variant3) mustBe "SE-3"
      calculator.calculate(routine_Variant6) mustBe "SE-4"
      calculator.calculate(routine_Variant7) mustBe "SE-5"
    }

    "calculate the socio-economic score of middle and junior managers occupations" in {
      calculator.calculate(middle_Variant1) mustBe "SE-1"
      calculator.calculate(middle_Variant2) mustBe "SE-3"
      calculator.calculate(middle_Variant3) mustBe "SE-3"
      calculator.calculate(middle_Variant6) mustBe "SE-1"
      calculator.calculate(middle_Variant7) mustBe "SE-1"
    }

    "calculate the socio-economic score of traditional professional occupations" in {
      calculator.calculate(traditional_Variant1) mustBe "SE-1"
      calculator.calculate(traditional_Variant2) mustBe "SE-1"
      calculator.calculate(traditional_Variant3) mustBe "SE-1"
      calculator.calculate(traditional_Variant4) mustBe "SE-1"
      calculator.calculate(traditional_Variant5) mustBe "SE-1"
    }

    "calculate the socio-economic score for a variety of scenarios" in {
      val v1: Map[String, String] = Map(
        employeeOrSelfEmployed -> "Self-employed/freelancer without employees",
        highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Senior managers and administrators",
        sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
        superviseEmployees -> "Yes"
      )
      calculator.calculate(v1) mustBe "SE-3"

      val v2: Map[String, String] = Map(
        employeeOrSelfEmployed -> "Employee",
        highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Clerical (office work) and intermediate",
        sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
        superviseEmployees -> "No"
      )
      calculator.calculate(v2) mustBe "SE-2"

      val v3: Map[String, String] = Map(
        employeeOrSelfEmployed -> "Self-employed with employees",
        highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Senior managers and administrators",
        sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
        superviseEmployees -> "No"
      )
      calculator.calculate(v3) mustBe "SE-3"

      val v4: Map[String, String] = Map(
        employeeOrSelfEmployed -> "Employee",
        highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Senior managers and administrators",
        sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
        superviseEmployees -> "Yes"
      )
      calculator.calculate(v4) mustBe "SE-1"

      val v5: Map[String, String] = Map(
        employeeOrSelfEmployed -> "Self-employed/freelancer without employees",
        highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Technical and craft",
        sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
        superviseEmployees -> "No"
      )
      calculator.calculate(v5) mustBe "SE-3"

      val v6: Map[String, String] = Map(
        employeeOrSelfEmployed -> "Self-employed with employees",
        highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Technical and craft",
        sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
        superviseEmployees -> "Yes"
      )
      calculator.calculate(v6) mustBe "SE-1"

      val v7: Map[String, String] = Map(
        employeeOrSelfEmployed -> "Employee",
        highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Technical and craft",
        sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
        superviseEmployees -> "No"
      )
      calculator.calculate(v7) mustBe "SE-4"

      val v8: Map[String, String] = Map(
        employeeOrSelfEmployed -> "Employee",
        highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Modern professional",
        sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
        superviseEmployees -> "Yes"
      )
      calculator.calculate(v8) mustBe "SE-1"

      val v9: Map[String, String] = Map(
        employeeOrSelfEmployed -> "Employee",
        highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Modern professional",
        sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
        superviseEmployees -> "Yes"
      )
      calculator.calculate(v9) mustBe "SE-1"

      val v10: Map[String, String] = Map(
        highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Long term unemployed"
      )
      calculator.calculate(v10) mustBe "SE-5"

      val v11: Map[String, String] = Map(
        employeeOrSelfEmployed -> "Employee",
        highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Modern professional",
        sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
        superviseEmployees -> "Yes"
      )
      calculator.calculate(v11) mustBe "SE-1"
    }
  }

  object SocioEconomicCalculatorSpec {
    val traditional_Variant5: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Traditional professional",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.DontKnow,
      superviseEmployees -> "No"
    )

    val traditional_Variant4: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Traditional professional",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "Yes"
    )

    val traditional_Variant3: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed/freelancer without employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Traditional professional",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "No"
    )

    val traditional_Variant2: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed with employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Traditional professional",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "I don't know/prefer not to say"
    )

    val traditional_Variant1: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed with employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Traditional professional",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "I don't know/prefer not to say"
    )

    val middle_Variant7: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Middle or junior managers",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.DontKnow,
      superviseEmployees -> "No"
    )

    val middle_Variant6: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Middle or junior managers",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "Yes"
    )

    val middle_Variant3: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed/freelancer without employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Middle or junior managers",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "No"
    )

    val middle_Variant2: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed with employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Middle or junior managers",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "I don't know/prefer not to say"
    )

    val middle_Variant1: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed with employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Middle or junior managers",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "I don't know/prefer not to say"
    )

    val routine_Variant7: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Routine manual and service",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.DontKnow,
      superviseEmployees -> "No"
    )

    val routine_Variant6: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Routine manual and service",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "Yes"
    )

    val routine_Variant3: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed/freelancer without employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Routine manual and service",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "No"
    )

    val routine_Variant2: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed with employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Routine manual and service",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "I don't know/prefer not to say"
    )

    val routine_Variant1: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed with employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Routine manual and service",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "I don't know/prefer not to say"
    )

    val semi_routine_Variant7: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Semi-routine manual and service",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.DontKnow,
      superviseEmployees -> "No"
    )

    val semi_routine_Variant6: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Semi-routine manual and service",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "Yes"
    )

    val semi_routine_Variant3: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed/freelancer without employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Semi-routine manual and service",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "No"
    )

    val semi_routine_Variant2: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed with employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Semi-routine manual and service",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "I don't know/prefer not to say"
    )

    val semi_routine_Variant1: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed with employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Semi-routine manual and service",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "I don't know/prefer not to say"
    )

    val technical_Variant7: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Technical and craft",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.DontKnow,
      superviseEmployees -> "No"
    )

    val technical_Variant6: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Technical and craft",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "Yes"
    )

    val technical_Variant3: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed/freelancer without employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Technical and craft",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "No"
    )

    val technical_Variant2: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed with employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Technical and craft",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "I don't know/prefer not to say"
    )

    val technical_Variant1: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed with employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Technical and craft",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "I don't know/prefer not to say"
    )

    val managers_Variant1: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed with employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Senior managers and administrators",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "I don't know/prefer not to say"
    )

    val managers_Variant2: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed with employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Senior managers and administrators",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "I don't know/prefer not to say"
    )

    val managers_Variant3: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed/freelancer without employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Senior managers and administrators",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "No"
    )

    val managers_Variant4: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Senior managers and administrators",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "I don't know/prefer not to say"
    )

    val managers_Variant5: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Senior managers and administrators",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "I don't know/prefer not to say"
    )

    val managers_Variant6: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Senior managers and administrators",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "Yes"
    )

    val managers_Variant7: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Senior managers and administrators",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.DontKnow,
      superviseEmployees -> "No"
    )

    val clerical_Variant1: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed with employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Clerical (office work) and intermediate",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "No"
    )

    val clerical_Variant2: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed with employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Clerical (office work) and intermediate",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "I don't know/prefer not to say"
    )

    val clerical_Variant3: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed/freelancer without employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Clerical (office work) and intermediate",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "No"
    )

    val clerical_Variant6: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Clerical (office work) and intermediate",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "Yes"
    )

    val clerical_Variant7: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Clerical (office work) and intermediate",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.DontKnow,
      superviseEmployees -> "No"
    )

    val modernProfessional_Variant1: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed with employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Modern professional",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "No"
    )

    val modernProfessional_Variant2: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed with employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Modern professional",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "I don't know/prefer not to say"
    )

    val modernProfessional_Variant3: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed/freelancer without employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Modern professional",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "No"
    )

    val modernProfessional_Variant6: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Modern professional",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "Yes"
    )

    val modernProfessional_Variant7: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Modern professional",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.DontKnow,
      superviseEmployees -> "No"
    )

    val otherEmployees_7_Variant1: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Traditional professional",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.DontKnow,
      superviseEmployees -> "No"
    )

    val otherEmployees_7_Variant2: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Modern professional",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.DontKnow,
      superviseEmployees -> "I don't know/prefer not to say"
    )

    val supervisors_6_Variant1: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Traditional professional",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "Yes"
    )

    val supervisors_6_Variant2: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Modern professional",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "Yes"
    )

    val managers_5_Variant1: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Senior managers and administrators",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "I don't know/prefer not to say"
    )

    val managers_5_Variant2: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Senior managers and administrators",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "Yes"
    )

    val managers_5_Variant3: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Senior managers and administrators",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "No"
    )

    val managers_4_Variant1: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Senior managers and administrators",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "I don't know/prefer not to say"
    )

    val managers_4_Variant2: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Senior managers and administrators",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "Yes"
    )

    val managers_4_Variant3: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Employee",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Senior managers and administrators",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "No"
    )

    val self_employed_3_Variant1: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed/freelancer without employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Senior managers and administrators",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "No"
    )

    val self_employed_3_Variant2: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed/freelancer without employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Modern professional",
      sizeOfPlaceOfWork -> "I don't know/prefer not to say",
      superviseEmployees -> "I don't know/prefer not to say"
    )

    val self_employed_3_Variant3: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed/freelancer without employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Modern professional",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "Yes"
    )

    val employers_2_Variant1: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed with employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Senior managers and administrators",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "I don't know/prefer not to say"
    )

    val employers_2_Variant2: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed with employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Modern professional",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "No"
    )

    val employers_2_Variant3: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed with employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Senior managers and administrators",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Small,
      superviseEmployees -> "Yes"
    )

    val employers_1_Variant1: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed with employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Senior managers and administrators",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "I don't know/prefer not to say"
    )

    val employers_1_Variant2: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed with employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Modern professional",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "No"
    )

    val employers_1_Variant3: Map[String, String] = Map(
      employeeOrSelfEmployed -> "Self-employed with employees",
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Senior managers and administrators",
      sizeOfPlaceOfWork -> SizeOfPlaceOfWork.Large,
      superviseEmployees -> "Yes"
    )

    val longTermUnemployed: Map[String, String] = Map(
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Long term unemployed"
    )

    val unemployedSeekingWork: Map[String, String] = Map(
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Unemployed but seeking work"
    )

    val retired: Map[String, String] = Map(
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Retired"
    )

    val prefersNotToSay: Map[String, String] = Map(
      highestEarningParentOrGuardianTypeOfWorkAtAge14 -> "Unknown"
    )
  }
}
