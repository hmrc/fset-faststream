/*
 * Copyright 2016 HM Revenue & Customs
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

package models

import java.util.UUID

import play.api.libs.json._

case class UniqueIdentifier(val uuid: UUID) extends AnyVal {
  override def toString() = uuid.toString
}

object UniqueIdentifier {
  def apply(value: String): UniqueIdentifier = {
    UniqueIdentifier(UUID.fromString(value))
  }

  implicit val uniqueIdentifierFormats: Writes[UniqueIdentifier] = Writes {
    (identifier: UniqueIdentifier) => JsString(identifier.toString)
  }
  implicit val uniqueIdentifierReads: Reads[UniqueIdentifier] = Reads {
    (jsValue: JsValue) => JsSuccess(UniqueIdentifier(jsValue.as[String]))
  }
}
