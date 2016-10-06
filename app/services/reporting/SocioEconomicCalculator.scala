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
        typeOfWork = questionnaire("Did they work as an employee or were they self-employed?"),
        typeOfOccupation = questionnaire("When you were 14, what kind of work did your highest-earning parent or guardian do?"),
        sizeOfCompany = questionnaire("Which size would best describe their place of work?"),
        isSupervisor = questionnaire("Did they supervise employees?"))
    }
  }

  //scalastyle:off line.size.limit
  def calculate(answer: Map[String, String]): String = {
    ParentalOccupationQuestionnaire.apply(answer) match {
      case ParentalOccupationQuestionnaire("Employee", "Senior managers and administrators", "Small (1 - 24 employees)", _) => "5 - Managers-small organisations"
      case ParentalOccupationQuestionnaire("Employee", "Senior managers and administrators", "Large (over 24 employees)", _) => "4 - Managers-large organisations"
      case ParentalOccupationQuestionnaire("Employee", _, _, "No") => "7 - Other employees"
      case ParentalOccupationQuestionnaire("Employee", _, _, "Yes") => "6 - Supervisors"
      case ParentalOccupationQuestionnaire("Self-employed/freelancer without employees", _, _, _) => "3 - Self-employeed, no employees"
      case ParentalOccupationQuestionnaire("Self-employed with employees", _, "Small (1 - 24 employees)", _) => "2 - Employeers-small organisations"
      case ParentalOccupationQuestionnaire("Self-employed with employees", _, "Large (over 24 employees)", _) => "1 - Employeers-large organisations"
      case _ => ""
    }
  }
  //scalastyle:on line.size.limit
}

object SocioEconomicCalculator {
  def props(aggregator: ActorRef) = Props(new SocioEconomicCalculator(aggregator))
}
