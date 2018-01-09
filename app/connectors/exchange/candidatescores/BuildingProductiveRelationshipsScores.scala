/*
 * Copyright 2018 HM Revenue & Customs
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

package connectors.exchange.candidatescores

import play.api.libs.json.Json

case class BuildingProductiveRelationshipsScores(
  formingRelationships: Option[Double] = None,
  facilitatingOthersParticipation: Option[Double] = None,
  involvingOthersInDecisionMaking: Option[Double] = None,
  establishingRelationships: Option[Double] = None,
  seekingHelpFromOutsideTeam: Option[Double] = None,
  identifyingOpportunitiesForOthers: Option[Double] = None,
  keepingSkillSetUpToDate: Option[Double] = None
)

object BuildingProductiveRelationshipsScores {
  implicit val buildingProductiveRelationshipsScoresFormat =
    Json.format[BuildingProductiveRelationshipsScores]
}
