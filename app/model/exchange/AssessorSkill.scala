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

package model.exchange

import model.persisted.eventschedules.SkillType._
import play.api.libs.json.{ Json, OFormat }

case class AssessorSkill(name: SkillType, displayText: String)

object AssessorSkill {

  implicit val assessorSkillFormatter: OFormat[AssessorSkill] = Json.format[AssessorSkill]

  val AllSkillsWithLabels = List(
    AssessorSkill(ASSESSOR, "FSAC Assessor"),
    AssessorSkill(DAT_ASSESSOR, "DAT Assessor"),
    AssessorSkill(FCO_ASSESSOR, "FCO Assessor"),
    AssessorSkill(GCFS_ASSESSOR, "GCFS Assessor"),
    AssessorSkill(EAC_ASSESSOR, "EAC Assessor"),
    AssessorSkill(EAC_DS_ASSESSOR, "EAC_DS Assessor"),
    AssessorSkill(SAC_ASSESSOR, "SAC Assessor"),
    AssessorSkill(HOP_ASSESSOR, "HOP Assessor"),
    AssessorSkill(PDFS_ASSESSOR, "PDFS Assessor"),
    AssessorSkill(SEFS_ASSESSOR, "SEFS Assessor"),
    AssessorSkill(EDIP_ASSESSOR, "EDIP Assessor"),
    AssessorSkill(SDIP_ASSESSOR, "SDIP Assessor"),
    AssessorSkill(SDIP_QAC, "SDIP QAC"),
    AssessorSkill(EDIP_QAC, "EDIP QAC"),
    AssessorSkill(SRAC_ASSESSOR, "SRAC Assessor"),
    AssessorSkill(ORAC_ASSESSOR, "ORAC Assessor"),
    AssessorSkill(DEPARTMENTAL_ASSESSOR, "Departmental Assessor"),
    AssessorSkill(CHAIR, "Chair"),
    AssessorSkill(EXERCISE_MARKER, "Exercise Marker"),
    AssessorSkill(QUALITY_ASSURANCE_COORDINATOR, "Quality Assurance Coordinator"),
    AssessorSkill(SIFTER, "Sifter")
  )

  val SkillMap: Map[SkillType, AssessorSkill] = AllSkillsWithLabels.map { s =>
    s.name -> s
  }(scala.collection.breakOut): Map[SkillType, AssessorSkill]
}
