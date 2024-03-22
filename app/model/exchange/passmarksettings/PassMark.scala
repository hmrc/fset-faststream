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

package model.exchange.passmarksettings

import model.SchemeId
import play.api.libs.json.{Json, OFormat}

trait PassMark {
  def schemeId: SchemeId
  def schemeThresholds: PassMarkThresholds
}

case class Phase1PassMark(
                           schemeId: SchemeId,
                           schemeThresholds: Phase1PassMarkThresholds
) extends PassMark

object Phase1PassMark {
  implicit val phase1PassMark: OFormat[Phase1PassMark] = Json.format[Phase1PassMark]
}

case class Phase2PassMark(
                           schemeId: SchemeId,
                           schemeThresholds: Phase2PassMarkThresholds
) extends PassMark

object Phase2PassMark {
  implicit val phase2PassMark: OFormat[Phase2PassMark] = Json.format[Phase2PassMark]
}

case class Phase3PassMark(
                           schemeId: SchemeId,
                           schemeThresholds: Phase3PassMarkThresholds
) extends PassMark

object Phase3PassMark {
  implicit val phase3PassMark: OFormat[Phase3PassMark] = Json.format[Phase3PassMark]
}

// Competency based FSAC pass marks (no longer used)
case class AssessmentCentreCompetencyPassMark(
                                               schemeId: SchemeId,
                                               schemeThresholds: AssessmentCentreCompetencyPassMarkThresholds
                         ) extends PassMark

object AssessmentCentreCompetencyPassMark {
  implicit val jsonFormat: OFormat[AssessmentCentreCompetencyPassMark] = Json.format[AssessmentCentreCompetencyPassMark]
}

// Exercise based FSAC pass marks (used in the current version of the fsac evaluation)
case class AssessmentCentreExercisePassMark(
                                             schemeId: SchemeId,
                                             schemeThresholds: AssessmentCentreExercisePassMarkThresholds
                         ) extends PassMark

object AssessmentCentreExercisePassMark {
  implicit val jsonFormat: OFormat[AssessmentCentreExercisePassMark] = Json.format[AssessmentCentreExercisePassMark]
}
