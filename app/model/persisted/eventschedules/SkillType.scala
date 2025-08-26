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

  val ASSESSOR, CFS_ASSESSOR, CHAIR, CYB_ASSESSOR, DAT_ASSESSOR, DEPARTMENTAL_ASSESSOR, EXERCISE_MARKER = Value
  val EAC_ASSESSOR, EAC_DS_ASSESSOR, EDIP_ASSESSOR, EDIP_QAC, FCO_ASSESSOR, FIN_ASSESSOR, GCFS_ASSESSOR  = Value
  val GES_DS_ASSESSOR, HOP_ASSESSOR, HR_ASSESSOR, ORAC_ASSESSOR, ORAC_EM_ASSESSOR, ORAC_QAC, OPD_ASSESSOR = Value
  val PDFS_ASSESSOR, PRO_ASSESSOR, SAC_ASSESSOR, SAC_EM_ASSESSOR, SAC_SAM_ASSESSOR = Value
  val SDIP_ASSESSOR, SDIP_QAC, SEFS_ASSESSOR, SRAC_ASSESSOR = Value
  val QUALITY_ASSURANCE_COORDINATOR, RMT_ASSESSOR, SIFTER = Value

  implicit def toString(SkillType: SkillType): String = SkillType.toString

  implicit val SkillTypeFormat: Format[SkillType] = new Format[SkillType] {
    def reads(json: JsValue): JsSuccess[Value] = JsSuccess(SkillType.withName(json.as[String].toUpperCase()))
    def writes(skillType: SkillType): JsString = JsString(skillType.toString)
  }
}
