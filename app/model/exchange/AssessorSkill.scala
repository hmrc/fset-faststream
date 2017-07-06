/*
 * Copyright 2017 HM Revenue & Customs
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

package model.exchange

import model.persisted.eventschedules.SkillType._
import play.api.libs.json.Json

case class AssessorSkill(name: SkillType, label: String)

object AssessorSkill {

  implicit val assessorSkillFormatter = Json.format[AssessorSkill]

  val AllSkillsWithLabels = List(
    AssessorSkill(ASSESSOR, "Assessor"),
    AssessorSkill(DEPARTMENTAL_ASSESSOR, "Departmental Assessor"),
    AssessorSkill(CHAIR, "Chair"),
    AssessorSkill(EXERCISE_MARKER, "Exercise Marker"),
    AssessorSkill(QUALITY_ASSURANCE_COORDINATOR, "Quality Assurance Coordinator"),
    AssessorSkill(SIFTER, "Sifter")
  )
}
