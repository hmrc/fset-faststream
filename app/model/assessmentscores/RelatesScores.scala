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
                          generatesSolutions: Option[Double] = None, // exercise 1
                          practicalIdeas: Option[Double] = None, // exercise 1
                          identifiesBarriers: Option[Double] = None, // exercise 3
                          respondsAppropriately: Option[Double] = None, // exercise 3
                          alternativeSolutions: Option[Double] = None // exercise 3
                        ) {
  override def toString: String =
    s"generatesSolutions=$generatesSolutions," +
      s"practicalIdeas=$practicalIdeas," +
      s"identifiesBarriers=$identifiesBarriers," +
      s"respondsAppropriately=$respondsAppropriately," +
      s"alternativeSolutions=$alternativeSolutions"
}

object RelatesScores {
  implicit val relatesScoresFormat: OFormat[RelatesScores] = Json.format[RelatesScores]
}
