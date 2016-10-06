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

import akka.actor.{Actor, ActorRef, Props}
import model.PersistedObjects.DiversitySocioEconomic

class SocioEconomicCalculator(aggregator: ActorRef) extends Actor with AnswerProcessorTrait
  with SocioEconomicScoreCalculatorTrait with SocioEconomicCollector {

  override def receive: Receive = {
    case QuestionnaireProfile(answers) =>
      process(answers)
      val calculationMessage = createMessage

      aggregator ! calculationMessage
      context.stop(self)
  }
}

trait SocioEconomicCollector extends Collector {

  type Message = DiversitySocioEconomic

  override val collectorMap: collection.mutable.Map[String, Int] = collection.mutable.Map(
    ("SE-1", 0),
    ("SE-2", 0),
    ("SE-3", 0),
    ("SE-4", 0),
    ("SE-5", 0)
  )

  override def createMessage: DiversitySocioEconomic = new DiversitySocioEconomic(collectorMap.toMap)
}

trait SocioEconomicScoreCalculatorTrait extends Calculable {

  case class ParentalOccupationQuestionnaire(
                                                        typeOfWork: String,
                                                        typeOfOccupation: String,
                                                        sizeOfCompany: String,
                                                        isSupervisor: String)

  object ParentalOccupationQuestionnaire {
    def apply(questionnaire: Map[String, String]):ParentalOccupationQuestionnaire  = {
      ParentalOccupationQuestionnaire(
        typeOfWork = questionnaire.getOrElse("Did they work as an employee or were they self-employed?", ""),
        typeOfOccupation = questionnaire.getOrElse("When you were 14, what kind of work did your highest-earning parent or guardian do?", ""),
        sizeOfCompany = questionnaire.getOrElse("Which size would best describe their place of work?",""),
        isSupervisor = questionnaire.getOrElse("Did they supervise employees?", ""))
    }
  }

  def calculate(answer: Map[String, String]): String = {
    //    Logger.debug("## SocioEconomicScoreCalculatorTrait: " + answer)
    calculateSocioEconomicScore(calculateEmploymentStatusSize(answer), getTypeOfOccupation(answer))
  }

  //scalastyle:off line.size.limit
  protected def calculateEmploymentStatusSize(answer: Map[String, String]): Int = {
    val NotApplicable = 0
    val EmployersLargeOrnanisations = 1
    val EmployersSmallOrganisations = 2
    val SelfEmployedNoEmployees = 3
    val ManagersLargeOrganisations = 4
    val ManagersSmallOrganisations = 5
    val Supervisors = 6
    val OtherEmployees = 7

    ParentalOccupationQuestionnaire(answer) match {
      case ParentalOccupationQuestionnaire("Employee", "Senior managers and administrators", "Small (1 - 24 employees)", _) => ManagersSmallOrganisations
      case ParentalOccupationQuestionnaire("Employee", "Senior managers and administrators", "Large (over 24 employees)", _) => ManagersLargeOrganisations
      case ParentalOccupationQuestionnaire("Employee", _, _, "No" | "I don't know/prefer not to say") => OtherEmployees
      case ParentalOccupationQuestionnaire("Employee", _, _, "Yes") => Supervisors
      case ParentalOccupationQuestionnaire("Self-employed/freelancer without employees", _, _, _) => SelfEmployedNoEmployees
      case ParentalOccupationQuestionnaire("Self-employed with employees", _, "Small (1 - 24 employees)", _) => EmployersSmallOrganisations
      case ParentalOccupationQuestionnaire("Self-employed with employees", _, "Large (over 24 employees)", _) => EmployersLargeOrnanisations
      case _ => NotApplicable
    }
  }
  //scalastyle:on line.size.limit



  protected def getTypeOfOccupation(answer: Map[String, String]): Int = {
    val TypeOfOccupation: Map[String, Int] = Map(
      "Modern professional" -> 1,
      "Clerical and intermediate" -> 2,
      "Senior managers and administrators" -> 3,
      "Technical and craft" -> 4,
      "Semi-routine manual and service" -> 5,
      "Routine manual and service" -> 6,
      "Middle or junior managers" -> 7,
      "Traditional professional" -> 8
    )

    TypeOfOccupation(answer("Which type of occupation did they have?"))
  }

  protected def calculateSocioEconomicScore(employmentStatusSizeValue: Int, typeOfOccupation: Int): String = {
    val socioEconomicScoreMatrix: Array[Array[Int]] = Array(
      Array(1, 1, 1, 1, 1, 1, 1),
      Array(1, 3, 3, 1, 1, 1, 2),
      Array(1, 3, 3, 1, 1, 1, 1),
      Array(1, 3, 3, 1, 1, 4, 4),
      Array(1, 3, 3, 1, 1, 4, 5),
      Array(1, 3, 3, 1, 1, 4, 5),
      Array(1, 3, 3, 1, 1, 1, 1),
      Array(1, 1, 1, 1, 1, 1, 1)
    )

    employmentStatusSizeValue match {
      case 0 => "N/A"
      case _ => s"SE-${socioEconomicScoreMatrix(typeOfOccupation - 1)(employmentStatusSizeValue - 1)}"
    }
  }
}

object SocioEconomicCalculator {
  def props(aggregator: ActorRef) = Props(new SocioEconomicCalculator(aggregator))
}
