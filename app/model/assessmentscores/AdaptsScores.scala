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
                         formsEffectiveWorkingRelationships: Option[Double] = None, // exercise 2
                         inclusiveApproach: Option[Double] = None, // exercise 2
                         encouragesCollaboration: Option[Double] = None, // exercise 2
                         establishesRelationships: Option[Double] = None, // exercise 3
                         seeksInformation: Option[Double] = None, // exercise 3
                         identifiesGaps: Option[Double] = None // exercise 3
                       ) {
  override def toString: String =
    s"formsEffectiveWorkingRelationships=$formsEffectiveWorkingRelationships," +
      s"inclusiveApproach=$inclusiveApproach," +
      s"encouragesCollaboration=$encouragesCollaboration," +
      s"establishesRelationships=$establishesRelationships," +
      s"seeksInformation=$seeksInformation," +
      s"identifiesGaps=$identifiesGaps"
}

object AdaptsScores {
  implicit val adaptsScoresFormat: OFormat[AdaptsScores] =
    Json.format[AdaptsScores]
}
