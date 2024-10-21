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

// Used to be SeeingTheBigPictureScores
case class RelatesScores(
                          b19communicatesEffectively: Option[Double] = None, // exercise 1
                          b20influencesOthers: Option[Double] = None, // exercise 1
                          b3selfAware: Option[Double] = None, // exercise 2
                          b4communicatesEffectively: Option[Double] = None, // exercise 2
                          b10selfAwareAndManages: Option[Double] = None, // exercise 3
                          b11communicatesEffectively: Option[Double] = None // exercise 3
                        ) {
  override def toString: String =
    s"b19communicatesEffectively=$b19communicatesEffectively," +
      s"b20influencesOthers=$b20influencesOthers," +
      s"b3selfAware=$b3selfAware," +
      s"b4communicatesEffectively=$b4communicatesEffectively," +
      s"b10selfAwareAndManages=$b10selfAwareAndManages," +
      s"b11communicatesEffectively=$b11communicatesEffectively"
}

object RelatesScores {
  private val prefix = "relates"
  def exercise1Headers: List[String] = List(s"$prefix-b19communicatesEffectively", s"$prefix-b20influencesOthers")

  def exercise2Headers: List[String] = List(s"$prefix-b3selfAware", s"$prefix-b4communicatesEffectively")

  def exercise3Headers: List[String] = List(s"$prefix-b10selfAwareAndManages", s"$prefix-b11communicatesEffectively")

  implicit val relatesScoresFormat: OFormat[RelatesScores] = Json.format[RelatesScores]
}
