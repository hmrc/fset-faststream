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

package model.exchange

import model.persisted.eventschedules.SkillType._
import play.api.libs.json.{ Json, OFormat }

case class AssessorSkill(name: SkillType, displayText: String)

object AssessorSkill {

  implicit val assessorSkillFormatter: OFormat[AssessorSkill] = Json.format[AssessorSkill]

  val AllSkillsWithLabels = List(
    AssessorSkill(ASSESSOR, "FSAC Assessor"),
    AssessorSkill(CHAIR, "Chair"),
    AssessorSkill(CFS_ASSESSOR, "CFS Assessor"),
    AssessorSkill(CYB_ASSESSOR, "CYB Assessor"),
    AssessorSkill(DAT_ASSESSOR, "DAT Assessor"),
    AssessorSkill(DEPARTMENTAL_ASSESSOR, "Departmental Assessor"),
    AssessorSkill(EAC_ASSESSOR, "EAC Assessor"),
    // This has been replaced by GES_DS_ASSESSOR but we are keeping it for now
    AssessorSkill(EAC_DS_ASSESSOR, "EAC_DS Assessor"),
    AssessorSkill(GES_DS_ASSESSOR, "GES_DS Assessor"),
    AssessorSkill(EDIP_ASSESSOR, "EDIP Assessor"),
    AssessorSkill(EDIP_QAC, "EDIP QAC"),
    AssessorSkill(EXERCISE_MARKER, "Exercise Marker"),
    AssessorSkill(FCO_ASSESSOR, "FCO Assessor"),
    AssessorSkill(FIN_ASSESSOR, "FIN Assessor"),
    AssessorSkill(GCFS_ASSESSOR, "GCFS Assessor"),
    AssessorSkill(HOP_ASSESSOR, "HOP Assessor"),
    AssessorSkill(HR_ASSESSOR, "HR Assessor"),
    AssessorSkill(ORAC_ASSESSOR, "ORAC Assessor"),
    AssessorSkill(ORAC_EM_ASSESSOR, "ORAC_EM Assessor"),
    AssessorSkill(ORAC_QAC, "ORAC QAC"),
    AssessorSkill(OPD_ASSESSOR, "OPD Assessor"),
    AssessorSkill(PDFS_ASSESSOR, "PDFS Assessor"),
    AssessorSkill(PRO_ASSESSOR, "PRO Assessor"),
    AssessorSkill(QUALITY_ASSURANCE_COORDINATOR, "Quality Assurance Coordinator"),
    AssessorSkill(RMT_ASSESSOR, "RMT Assessor"),
    AssessorSkill(SAC_ASSESSOR, "SAC Assessor"),
    AssessorSkill(SAC_EM_ASSESSOR, "SAC_EM Assessor"),
    AssessorSkill(SAC_SAM_ASSESSOR, "SAC_SAM Assessor"),
    AssessorSkill(SDIP_ASSESSOR, "SDIP Assessor"),
    AssessorSkill(SDIP_QAC, "SDIP QAC"),
    AssessorSkill(SEFS_ASSESSOR, "SEFS Assessor"),
    AssessorSkill(SIFTER, "Sifter"),
    AssessorSkill(SRAC_ASSESSOR, "SRAC Assessor")
  )

  val SkillMap: Map[SkillType, AssessorSkill] = AllSkillsWithLabels.map { s =>
    s.name -> s
  }.toMap
}
