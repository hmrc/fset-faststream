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

case class AdaptsScores(
                         b04generatesNovelApproaches: Option[Double] = None, // exercise 1
                         b05learningAgility: Option[Double] = None, // exercise 1
                         b06flexibility: Option[Double] = None, // exercise 2
                         b07learningAgility: Option[Double] = None, // exercise 3
                         b08flexibility: Option[Double] = None // exercise 3
                       ) {
  override def toString: String =
    s"b04generatesNovelApproaches=$b04generatesNovelApproaches," +
      s"b05learningAgility=$b05learningAgility," +
      s"b06flexibility=$b06flexibility," +
      s"b07learningAgility=$b07learningAgility," +
      s"b08flexibility=$b08flexibility"
}

object AdaptsScores {
  private val prefix = "adapts"
  def exercise1Headers: List[String] = List(s"$prefix-b04generatesNovelApproaches", s"$prefix-b05learningAgility")

  def exercise2Headers: List[String] = List(s"$prefix-b06flexibility")

  def exercise3Headers: List[String] = List(s"$prefix-b07learningAgility", s"$prefix-b08flexibility")

  implicit val adaptsScoresFormat: OFormat[AdaptsScores] =
    Json.format[AdaptsScores]
}
