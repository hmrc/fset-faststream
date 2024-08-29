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

case class MakingEffectiveDecisionsScores(
                                           issuesAndRisks: Option[Double] = None, // written
                                           identifiesMitigation: Option[Double] = None, // written
                                           thoroughlyAnalyses: Option[Double] = None, // team
                                           identifiesCriticalIssues: Option[Double] = None // team
                                         ) {
  override def toString: String =
    s"issuesAndRisks=$issuesAndRisks," +
    s"identifiesMitigation=$identifiesMitigation," +
    s"thoroughlyAnalyses=$thoroughlyAnalyses," +
    s"identifiesCriticalIssues=$identifiesCriticalIssues"
}

object MakingEffectiveDecisionsScores {
  implicit val makingEffectiveDecisionsScoresFormat: OFormat[MakingEffectiveDecisionsScores] = Json.format[MakingEffectiveDecisionsScores]
}
