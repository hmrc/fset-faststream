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

package model.persisted.eventschedules

import play.api.libs.json.{Format, JsString, JsSuccess, JsValue}

import scala.language.implicitConversions

object SkillType extends Enumeration {
  type SkillType = Value

  val ASSESSOR, CFS_ASSESSOR, CYB_ASSESSOR, DAT_ASSESSOR, SRAC_ASSESSOR, ORAC_ASSESSOR, DEPARTMENTAL_ASSESSOR = Value
  val FCO_ASSESSOR, FIN_ASSESSOR, GCFS_ASSESSOR, EAC_ASSESSOR, EAC_DS_ASSESSOR, GES_DS_ASSESSOR, SAC_ASSESSOR, HOP_ASSESSOR = Value
  val PDFS_ASSESSOR, PRO_ASSESSOR, SEFS_ASSESSOR, EDIP_ASSESSOR, SDIP_ASSESSOR, SDIP_QAC, EDIP_QAC = Value
  val SAC_SAM_ASSESSOR, SAC_EM_ASSESSOR, ORAC_QAC, ORAC_EM_ASSESSOR, OPD_ASSESSOR, RMT_ASSESSOR = Value
  val CHAIR, EXERCISE_MARKER, QUALITY_ASSURANCE_COORDINATOR, SIFTER = Value

  implicit def toString(SkillType: SkillType): String = SkillType.toString

  implicit val SkillTypeFormat: Format[SkillType] = new Format[SkillType] {
    def reads(json: JsValue): JsSuccess[Value] = JsSuccess(SkillType.withName(json.as[String].toUpperCase()))
    def writes(skillType: SkillType): JsString = JsString(skillType.toString)
  }
}
