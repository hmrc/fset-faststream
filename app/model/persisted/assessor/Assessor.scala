/*
 * Copyright 2020 HM Revenue & Customs
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

import model.SchemeId
import model.persisted.assessor.AssessorStatus.AssessorStatus
import play.api.libs.json.{ Json, OFormat }
import reactivemongo.bson.{ BSONDocument, BSONHandler, Macros }

case class Assessor(
  userId: String,
  version: Option[String],
  skills: List[String],
  sifterSchemes: List[SchemeId],
  civilServant: Boolean,
  availability: Set[AssessorAvailability] = Set.empty,
  status: AssessorStatus
)

object Assessor {
  implicit val persistedAssessorFormat: OFormat[Assessor] = Json.format[Assessor]
  implicit val assessorHandler: BSONHandler[BSONDocument, Assessor] = Macros.handler[Assessor]
}
