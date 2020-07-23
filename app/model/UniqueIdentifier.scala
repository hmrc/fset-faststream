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

package model

import java.util.UUID

import play.api.libs.json._
import reactivemongo.bson.{ BSON, BSONHandler, BSONString }

case class UniqueIdentifier(uuid: UUID) {
  override def toString = uuid.toString
}

object UniqueIdentifier {
  def apply(value: String): UniqueIdentifier = {
    UniqueIdentifier(UUID.fromString(value))
  }

  def randomUniqueIdentifier = {
    UniqueIdentifier(UUID.randomUUID())
  }

  def toOpt(value: String): Option[UniqueIdentifier] = {
    if (value.isEmpty) {
      None
    } else {
      Some(UniqueIdentifier(value))
    }
  }

  def toOpt(value: Option[String]): Option[UniqueIdentifier] = {
    value.flatMap(toOpt)
  }

  def fromOpt(optUID: Option[UniqueIdentifier]): String = {
    optUID.map(_.toString()).getOrElse("")
  }

  implicit val uniqueIdentifierFormats: Writes[UniqueIdentifier] = Writes {
    (identifier: UniqueIdentifier) => JsString(identifier.toString)
  }
  implicit val uniqueIdentifierReads: Reads[UniqueIdentifier] = Reads {
    (jsValue: JsValue) => JsSuccess(UniqueIdentifier(jsValue.as[String]))
  }

  implicit object UniqueIdentifierBSONHandler extends BSONHandler[BSONString, UniqueIdentifier] {
    def read(doc: BSONString) = UniqueIdentifier(doc.value)
    def write(id: UniqueIdentifier) = BSON.write(id.toString)
  }
}
