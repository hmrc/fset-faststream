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

package model.persisted

import org.joda.time.DateTime
import play.api.libs.json.Json
import reactivemongo.bson.{ BSONDocument, BSONHandler, Macros }

case class AuditEvent(
  name: String,
  created: DateTime,
  applicationId: Option[String],
  userId: Option[String],
  createdBy: Option[String] = None
)

object AuditEvent {
  import repositories.BSONDateTimeHandler
  implicit val auditEventFormat = Json.format[AuditEvent]
  implicit val eventHandler: BSONHandler[BSONDocument, AuditEvent] = Macros.handler[AuditEvent]
}
