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

package model.persisted.assessor

import play.api.libs.json._

object AssessorStatus extends Enumeration {
  type AssessorStatus = Value

  val CREATED, AVAILABILITIES_SUBMITTED = Value

  implicit val assessorStatusFormat: Format[AssessorStatus] = new Format[AssessorStatus] {
    override def reads(json: JsValue): JsResult[AssessorStatus] = JsSuccess(AssessorStatus.withName(json.as[String].toUpperCase))
    override def writes(eventType: AssessorStatus): JsValue = JsString(eventType.toString)
  }
}
