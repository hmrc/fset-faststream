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

// Used to be WorkingTogetherDevelopingSelfAndOtherScores
case class AdaptsScores(
                         b5novelApproaches: Option[Double] = None, // exercise 2
                         b6openToChange: Option[Double] = None, // exercise 2
                         b7learningAgility: Option[Double] = None, // exercise 2
                         b12consolidatesLearning: Option[Double] = None, // exercise 3
                         b13learningAtPace: Option[Double] = None, // exercise 3
                         b14respondsFlexibily: Option[Double] = None // exercise 3
                       ) {
  override def toString: String =
    s"b5novelApproaches=$b5novelApproaches," +
      s"b6openToChange=$b6openToChange," +
      s"b7learningAgility=$b7learningAgility," +
      s"b12consolidatesLearning=$b12consolidatesLearning," +
      s"b13learningAtPace=$b13learningAtPace," +
      s"b14respondsFlexibily=$b14respondsFlexibily"
}

object AdaptsScores {
  implicit val adaptsScoresFormat: OFormat[AdaptsScores] =
    Json.format[AdaptsScores]
}
