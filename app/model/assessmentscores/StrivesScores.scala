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

case class StrivesScores(
                          b09strivesToImproveAndSucceed: Option[Double] = None, // exercise 1
                          b10goalOriented: Option[Double] = None, // exercise 1
                          b11goalOrientated: Option[Double] = None, // exercise 2
                          b12takesTheInitiative: Option[Double] = None, // exercise 2
                          b13motivation: Option[Double] = None, // exercise 3
                          b14commitment: Option[Double] = None // exercise 3
                        ) {
  override def toString: String =
    s"b09strivesToImproveAndSucceed=$b09strivesToImproveAndSucceed," +
      s"b10goalOriented=$b10goalOriented," +
      s"b11goalOrientated=$b11goalOrientated," +
      s"b12takesTheInitiative=$b12takesTheInitiative," +
      s"b13motivation=$b13motivation," +
      s"b14commitment=$b14commitment"
}

object StrivesScores {
  private val prefix = "strives"
  def exercise1Headers: List[String] = List(s"$prefix-b09strivesToImproveAndSucceed", s"$prefix-b10goalOriented")

  def exercise2Headers: List[String] = List(s"$prefix-b11goalOrientated", s"$prefix-b12takesTheInitiative")

  def exercise3Headers: List[String] = List(s"$prefix-b13motivation", s"$prefix-b14commitment")

  implicit val strivesScoresFormat: OFormat[StrivesScores] = Json.format[StrivesScores]
}
