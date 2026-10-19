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

package model.assessmentscores

import play.api.libs.json.{Json, OFormat}

case class ThinksScores(
                         b01holisticThinking: Option[Double] = None, // exercise 1
                         b02analyticalThinking: Option[Double] = None, // exercise 2
                         b03holisticThinking: Option[Double] = None // exercise 2
                       ) {
  override def toString: String =
    s"b01holisticThinking=$b01holisticThinking," +
      s"b02analyticalThinking=$b02analyticalThinking," +
      s"b03holisticThinking=$b03holisticThinking"
}

object ThinksScores {
  private val prefix = "thinks"
  def exercise1Headers: List[String] = List(s"$prefix-b01holisticThinking")

  def exercise2Headers: List[String] = List(s"$prefix-b02analyticalThinking", s"$prefix-b03holisticThinking")

  implicit val thinksScoresFormat: OFormat[ThinksScores] = Json.format[ThinksScores]
}
