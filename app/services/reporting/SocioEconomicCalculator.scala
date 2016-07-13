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

import akka.actor.{ Actor, ActorRef, Props }
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

  val NotApplicable = 0
  val EmployersLargeOrnanisations = 1
  val EmployersSmallOrganisations = 2
  val SelfEmployedNoEmployees = 3
  val ManagersLargeOrganisations = 4
  val ManagersSmallOrganisations = 5
  val Supervisors = 6
  val OtherEmployees = 7

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

  def calculate(answer: Map[String, String]): String = {
    //    Logger.debug("## SocioEconomicScoreCalculatorTrait: " + answer)
    val employmentStatusSizeValue = calculateEmploymentStatusSize(answer)
    if (employmentStatusSizeValue > 0) {
      val typeOfOccupation = getTypeOfOccupation(answer)
      calculateSocioEconomicScore(employmentStatusSizeValue, typeOfOccupation)
    } else {
      "N/A"
    }
  }

  def calculateSocioEconomicScore(employmentStatusSizeValue: Int, typeOfOccupation: Int): String = {
    employmentStatusSizeValue match {
      case 0 => ""
      case _ => s"SE-${socioEconomicScoreMatrix(typeOfOccupation - 1)(employmentStatusSizeValue - 1)}"
    }
  }

  def calculateEmploymentStatusSize(answer: Map[String, String]) = {
    var response: Int = 0
    if (answer("Which type of occupation did they have?") != "Unemployed" &&
      answer("Which type of occupation did they have?") != "Unemployed but seeking work" &&
      answer("Which type of occupation did they have?") != "Unknown") {
      if (answer("Did they work as an employee or were they self-employed?") == "Self-employed/freelancer without employees") {
        response = SelfEmployedNoEmployees
      } else if (answer("Did they work as an employee or were they self-employed?") == "Self-employed with employees") {
        if (answer("Which size would best describe their place of work?") == "Small (1 - 24 employees)") {
          response = EmployersSmallOrganisations
        } else {
          response = EmployersLargeOrnanisations
        }
      } else {
        if (answer("Which type of occupation did they have?") == "Senior managers and administrators") {
          if (answer("Which size would best describe their place of work?") == "Small (1 - 24 employees)") {
            response = ManagersSmallOrganisations
          } else {
            response = ManagersLargeOrganisations
          }
        } else {
          if (answer("Did they supervise any other employees?") == "Yes") {
            response = Supervisors
          } else {
            response = OtherEmployees
          }
        }
      }
    }
    response
  }

  def getTypeOfOccupation(answer: Map[String, String]): Int = {
    TypeOfOccupation(answer("Which type of occupation did they have?"))
  }
}

object SocioEconomicCalculator {
  def props(aggregator: ActorRef) = Props(new SocioEconomicCalculator(aggregator))
}
