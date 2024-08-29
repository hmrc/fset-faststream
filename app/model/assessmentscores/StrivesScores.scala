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

// Used to be CommunicatingAndInfluencingScores
case class StrivesScores(
                          b21alignsWithGoals: Option[Double] = None, // exercise 1
                          b22tacklesRootCause: Option[Double] = None, // exercise 1
                          b8strivesToSucceed: Option[Double] = None, // exercise 2
                          b9goalOriented: Option[Double] = None, // exercise 2
                          b15motivation: Option[Double] = None, // exercise 3
                          b16conduct: Option[Double] = None // exercise 3
                        ) {
  override def toString: String =
    s"b21aignsWithGoals=$b21alignsWithGoals," +
      s"b22tacklesRootCause=$b22tacklesRootCause," +
      s"communicatesClearly=$b8strivesToSucceed," +
      s"negotiatesAndPersuades=$b9goalOriented," +
      s"b15motivation=$b15motivation," +
      s"b16conduct=$b16conduct"
}

object StrivesScores {
  implicit val strivesScoresFormat: OFormat[StrivesScores] = Json.format[StrivesScores]
}
