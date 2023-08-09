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

import javax.inject.Singleton
import model.persisted.QuestionnaireAnswer
import repositories.application.DiversityQuestionsText

object SocioEconomicCalculator extends SocioEconomicScoreCalculator {
  // These provide the columns of the matrix
  val NotApplicable = 0
  val EmployersLargeOrganisations = 1
  val EmployersSmallOrganisations = 2
  val SelfEmployedNoEmployees = 3
  val ManagersLargeOrganisations = 4
  val ManagersSmallOrganisations = 5
  val Supervisors = 6
  val OtherEmployees = 7
}

@Singleton
class SocioEconomicScoreCalculator extends Calculable with DiversityQuestionsText {
  import SocioEconomicCalculator._

  override def calculateAsInt(answers: Map[String, QuestionnaireAnswer]): Int = {
    val flattenedAnswers = answers.map { case (question, answer) => question -> answer.answer.getOrElse("") }

    val employmentStatusSize = calculateEmploymentStatusSize(flattenedAnswers)
    if (employmentStatusSize != NotApplicable) {
      calculateSocioEconomicScoreAsInt(employmentStatusSize, getTypeOfOccupation(flattenedAnswers))
    } else {
      if (flattenedAnswers.isLongTermUnemployed) {
        5
      } else {
        0
      }
    }
  }

  override def calculate(answers: Map[String, String]): String = {
    val employmentStatusSize = calculateEmploymentStatusSize(answers)
    if (employmentStatusSize != NotApplicable) {
      calculateSocioEconomicScore(employmentStatusSize, getTypeOfOccupation(answers))
    } else {
      if (answers.isLongTermUnemployed) {
        "SE-5"
      } else {
        ""
      }
    }
  }

  implicit class Answers(questionnaire: Map[String, String]) {
    def isLongTermUnemployed = questionnaire.getOrElse(highestEarningParentOrGuardianTypeOfWorkAtAge14, "") == "Long term unemployed"
  }

  case class ParentalOccupationQuestionnaire(
                                              typeOfWork: String,       // Q2
                                              typeOfOccupation: String, // Q1
                                              sizeOfCompany: String,    // Q3
                                              isSupervisor: String      // Q4
                                            )

  object ParentalOccupationQuestionnaire {
    def apply(questionnaire: Map[String, String]):ParentalOccupationQuestionnaire  = {
      ParentalOccupationQuestionnaire(
        typeOfWork = questionnaire.getOrElse(employeeOrSelfEmployed, ""),
        typeOfOccupation = questionnaire.getOrElse(highestEarningParentOrGuardianTypeOfWorkAtAge14, ""),
        sizeOfCompany = questionnaire.getOrElse(sizeOfPlaceOfWork,""),
        isSupervisor = questionnaire.getOrElse(superviseEmployees, ""))
    }
  }

  //scalastyle:off line.size.limit
  private[reporting] def calculateEmploymentStatusSize(answer: Map[String, String]): Int = {
    ParentalOccupationQuestionnaire(answer) match {
      case ParentalOccupationQuestionnaire("Employee", "Senior managers and administrators", SizeOfPlaceOfWork.Small, _)  => ManagersSmallOrganisations  // column 5
      case ParentalOccupationQuestionnaire("Employee", "Senior managers and administrators", SizeOfPlaceOfWork.Large, _)  => ManagersLargeOrganisations  // column 4
      case ParentalOccupationQuestionnaire("Employee", _, _, "No" | "I don't know/prefer not to say")                     => OtherEmployees              // column 7
      case ParentalOccupationQuestionnaire("Employee", _, _, "Yes")                                                       => Supervisors                 // column 6
      case ParentalOccupationQuestionnaire("Self-employed/freelancer without employees", _, _, _)                         => SelfEmployedNoEmployees     // column 3
      case ParentalOccupationQuestionnaire("Self-employed with employees", _, SizeOfPlaceOfWork.Small, _)                 => EmployersSmallOrganisations // column 2
      case ParentalOccupationQuestionnaire("Self-employed with employees", _, SizeOfPlaceOfWork.Large, _)                 => EmployersLargeOrganisations // column 1
      case _                                                                                                              => NotApplicable
    }
  }
  //scalastyle:on line.size.limit

  // These are the values we store for the question:
  // "When you were 14, what kind of work did your highest-earning parent or guardian do?"
  // These provide the rows of the matrix
  private[reporting] def getTypeOfOccupation(answers: Map[String, String]): Int = {
    val TypeOfOccupation: Map[String, Int] = Map(
      "Modern professional" -> 1,
      "Clerical (office work) and intermediate" -> 2,
      "Senior managers and administrators" -> 3,
      "Technical and craft" -> 4,
      "Semi-routine manual and service" -> 5,
      "Routine manual and service" -> 6,
      "Middle or junior managers" -> 7,
      "Traditional professional" -> 8
    )
    TypeOfOccupation(answers(highestEarningParentOrGuardianTypeOfWorkAtAge14))
  }

  private val socioEconomicScoreMatrix: Array[Array[Int]] = Array(
    Array(1, 1, 1, 1, 1, 1, 1),
    Array(1, 3, 3, 1, 1, 1, 2),
    Array(1, 3, 3, 1, 1, 1, 1),
    Array(1, 3, 3, 1, 1, 4, 4),
    Array(1, 3, 3, 1, 1, 4, 5),
    Array(1, 3, 3, 1, 1, 4, 5),
    Array(1, 3, 3, 1, 1, 1, 1),
    Array(1, 1, 1, 1, 1, 1, 1)
  )

  private[reporting] def calculateSocioEconomicScoreAsInt(employmentStatusSizeValue: Int, typeOfOccupation: Int): Int = {
    employmentStatusSizeValue match {
      case 0 => 0
      case _ => socioEconomicScoreMatrix(typeOfOccupation - 1)(employmentStatusSizeValue - 1)
    }
  }

  private[reporting] def calculateSocioEconomicScore(employmentStatusSizeValue: Int, typeOfOccupation: Int): String = {
    employmentStatusSizeValue match {
      case 0 => "N/A"
      case _ => s"SE-${socioEconomicScoreMatrix(typeOfOccupation - 1)(employmentStatusSizeValue - 1)}"
    }
  }
}
