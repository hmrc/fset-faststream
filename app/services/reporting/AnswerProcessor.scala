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

import model.PersistedObjects.PersistedAnswer

trait AnswerProcessorTrait {
  this: Calculable with Collector =>

  def process(answers: List[Map[String, String]]): Unit = {
    processAnswers(answers)
  }

  def processAnswers(answers: List[Map[String, String]]): Unit = {
    for {
      answer <- answers
    } yield {
      val score = calculate(answer)
      updateTotals(score)
    }
  }

  def updateTotals(score: String) = {
    collectorMap.get(score) match {
      case Some(_) => collectorMap(score) += 1
      case _ =>
    }
  }
}

trait Calculable {
  def calculateAsInt(answers: Map[String, PersistedAnswer]): Int
  def calculate(answers: Map[String, String]): String
}

trait Collector {

  type Message

  def collectorMap: collection.mutable.Map[String, Int]

  def createMessage: Message
}
