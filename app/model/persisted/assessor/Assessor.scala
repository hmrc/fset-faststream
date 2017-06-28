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

package model.persisted.assessor

import model.AllocationStatuses.AllocationStatus
import model.persisted.eventschedules.{ Event, Location }
import model.persisted.eventschedules.SkillType.SkillType
import org.joda.time.LocalDate
import play.api.libs.json.{Json, OFormat}
import reactivemongo.bson.{BSONDocument, BSONHandler, Macros}
import repositories.{BSONLocalDateHandler, BSONLocalTimeHandler}

case class Assessor(
  userId: String,
  skills: List[String],
  civilServant: Boolean,
  availability: List[AssessorAvailability] = Nil
)

object Assessor {
  implicit val persistedAssessorFormat: OFormat[Assessor] = Json.format[Assessor]
  implicit val assessorHandler: BSONHandler[BSONDocument, Assessor] = Macros.handler[Assessor]
}
