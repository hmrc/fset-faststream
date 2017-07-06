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

package model

import play.api.libs.json._
import play.api.libs.functional.syntax._
import reactivemongo.bson.{ BSON, BSONHandler, BSONString, Macros }

case class SchemeId(value: String)

object SchemeId {
  implicit val rds: Reads[SchemeId] = (__ \ "id").read[String].map{ id => new SchemeId(id) }
  implicit val wrs: Writes[SchemeId] = (__ \ "id").write[String].contramap{ (scheme: SchemeId) => scheme.value }
  implicit val fmt: Format[SchemeId] = Format(rds, wrs)

  // Custom formatter to prevent a nested case object in BSON
  implicit object SchemeIdHandler extends BSONHandler[BSONString, SchemeId] {
      override def write(schemeId: SchemeId): BSONString = BSON.write(schemeId.value)
      override def read(bson: BSONString): SchemeId = SchemeId(bson.value)
  }
}

/** Wrapper for scheme data
  *
  * @param id The scheme ID to be delivered across the wire/stored in DB etc.
  * @param code The abbreviated form
  * @param name The form displayed to end users
  */
case class Scheme(id: SchemeId, code: String, name: String)



object Scheme {
  implicit val schemeFormat = Json.format[Scheme]
  implicit val schemeHandler = Macros.handler[Scheme]

  def apply(id: String, code: String, name: String): Scheme = Scheme(SchemeId(id), code, name)
}
