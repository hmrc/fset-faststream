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

case class RelatesScores(
                          b15selfRegulation: Option[Double] = None, // exercise 1
                          b16personalImpact: Option[Double] = None, // exercise 1
                          b17communicatesEffectively: Option[Double] = None, // exercise 2
                          b18influencesOthers: Option[Double] = None, // exercise 2
                          b19selfRegulation: Option[Double] = None, // exercise 3
                          b20personalImpact: Option[Double] = None // exercise 3
                        ) {
  override def toString: String =
    s"b15selfRegulation=$b15selfRegulation," +
      s"b16personalImpact=$b16personalImpact," +
      s"b17communicatesEffectively=$b17communicatesEffectively," +
      s"b18influencesOthers=$b18influencesOthers," +
      s"b19selfRegulation=$b19selfRegulation," +
      s"b20personalImpact=$b20personalImpact"
}

object RelatesScores {
  private val prefix = "relates"
  def exercise1Headers: List[String] = List(s"$prefix-b15selfRegulation", s"$prefix-b16personalImpact")

  def exercise2Headers: List[String] = List(s"$prefix-b17communicatesEffectively", s"$prefix-b18influencesOthers")

  def exercise3Headers: List[String] = List(s"$prefix-b19selfRegulation", s"$prefix-b20personalImpact")

  implicit val relatesScoresFormat: OFormat[RelatesScores] = Json.format[RelatesScores]
}
