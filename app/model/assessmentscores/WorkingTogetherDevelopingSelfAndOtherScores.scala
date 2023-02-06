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

import play.api.libs.json.Json

case class WorkingTogetherDevelopingSelfAndOtherScores(
                                                        formsEffectiveWorkingRelationships: Option[Double] = None, // team
                                                        inclusiveApproach: Option[Double] = None, // team
                                                        encouragesCollaboration: Option[Double] = None, // team
                                                        encouragesTeamwork: Option[Double] = None, // leadership
                                                        understandsStakeholderNeeds: Option[Double] = None, // leadership
                                                        improvesOwnPerformance: Option[Double] = None // leadership
                                                      ) {
  override def toString: String =
    s"formsEffectiveWorkingRelationships=$formsEffectiveWorkingRelationships," +
    s"inclusiveApproach=$inclusiveApproach," +
    s"encouragesCollaboration=$encouragesCollaboration," +
    s"encouragesTeamwork=$encouragesTeamwork," +
    s"understandsStakeholderNeeds=$understandsStakeholderNeeds," +
    s"improvesOwnPerformance=$improvesOwnPerformance"
}

object WorkingTogetherDevelopingSelfAndOtherScores {
  implicit val buildingProductiveRelationshipsScoresFormat =
    Json.format[WorkingTogetherDevelopingSelfAndOtherScores]
}
