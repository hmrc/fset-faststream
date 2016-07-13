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
import model.PersistedObjects.DiversityEthnicity

class EthnicityCalculator(aggregator: ActorRef) extends Actor with AnswerProcessorTrait
  with EthnicityCalculatorTrait with EthnicityCollector {

  override def receive: Receive = {
    case QuestionnaireProfile(answers) =>
      process(answers)
      val calculationMessage = createMessage
      aggregator ! calculationMessage
      context.stop(self)
  }
}

trait EthnicityCollector extends Collector {

  type Message = DiversityEthnicity

  override val collectorMap: collection.mutable.Map[String, Int] = collection.mutable.Map(
    ("English/Welsh/Scottish/Northern Irish/British", 0),
    ("Irish", 0),
    ("Gypsy or Irish Traveller", 0),
    ("Other White background", 0),
    ("White and Black Caribbean", 0),
    ("White and Black African", 0),
    ("White and Asian", 0),
    ("Other mixed/multiple ethnic background", 0),
    ("Indian", 0),
    ("Pakistani", 0),
    ("Bangladeshi", 0),
    ("Chinese", 0),
    ("Other Asian background", 0),
    ("African", 0),
    ("Caribbean", 0),
    ("Other Black/African/Caribbean background", 0),
    ("Arab", 0),
    ("Other ethnic group", 0)
  )

  override def createMessage: DiversityEthnicity = new DiversityEthnicity(collectorMap.toMap)
}

trait EthnicityCalculatorTrait extends Calculable {
  override def calculate(answer: Map[String, String]): String = {
    val key = "What is your ethnic group?"
    answer(key)
  }
}

object EthnicityCalculator {
  def props(aggregator: ActorRef) = Props(new EthnicityCalculator(aggregator))
}
