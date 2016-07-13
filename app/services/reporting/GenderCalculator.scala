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
import model.PersistedObjects.DiversityGender

class GenderCalculator(aggregator: ActorRef) extends Actor with AnswerProcessorTrait
  with GenderCalculationTrait with GenderCollector {

  override def receive: Receive = {
    case QuestionnaireProfile(answers) =>
      process(answers)
      val calculationMessage = createMessage
      aggregator ! calculationMessage
      context.stop(self)
  }
}

trait GenderCollector extends Collector {
  type Message = DiversityGender

  override val collectorMap: collection.mutable.Map[String, Int] = collection.mutable.Map(
    ("Male", 0),
    ("Female", 0),
    ("Other", 0)
  )

  override def createMessage: DiversityGender = new DiversityGender(collectorMap.toMap)
}

trait GenderCalculationTrait extends Calculable {
  def calculate(answer: Map[String, String]): String = {
    val key = "What is your gender identity?"
    answer(key)
  }
}

object GenderCalculator {
  def props(aggregator: ActorRef) = Props(new GenderCalculator(aggregator))
}
