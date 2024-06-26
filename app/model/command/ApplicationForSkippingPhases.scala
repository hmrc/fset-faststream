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

package model.command

import model.persisted.SchemeEvaluationResult
import play.api.libs.json.{Json, OFormat}

// Used to skip Phase2 and Phase3 tests
case class ApplicationForSkippingPhases(
  applicationId: String,
  currentSchemeStatus: Seq[SchemeEvaluationResult]
)

object ApplicationForSkippingPhases {
  implicit val applicationForSkippingPhases: OFormat[ApplicationForSkippingPhases] = Json.format[ApplicationForSkippingPhases]
}
