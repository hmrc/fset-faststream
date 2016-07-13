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

class EthnicityCalculatorSpec extends PlaySpec {

  import EthnicityCalculatorSpec._

  "the calculator" should {
    "process the questions" in {
      val calculator = new AnswerProcessorTrait with EthnicityCalculatorTrait with EthnicityCollector {}
      calculator.process(answers)
      calculator.collectorMap("Indian") must be(2)

    }

    "process the questions and skip the unknown values" in {
      val calculator = new AnswerProcessorTrait with EthnicityCalculatorTrait with EthnicityCollector {}
      calculator.process(answersWithUnknown)
      calculator.collectorMap("Indian") must be(2)
    }
  }

}

object EthnicityCalculatorSpec {
  val answers: List[Map[String, String]] = List(
    Map("What is your ethnic group?" -> "Indian"), Map("What is your ethnic group?" -> "African"),
    Map("What is your ethnic group?" -> "Indian"), Map("What is your ethnic group?" -> "Pakistani")
  )

  val unknown = List(Map("What is your ethnic group?" -> ""))

  val answersWithUnknown = answers ++ unknown
}
