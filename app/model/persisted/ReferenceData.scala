/*
 * Copyright 2019 HM Revenue & Customs
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

import play.api.libs.json.Format
import play.api.libs.json._
import play.api.libs.functional.syntax._

case class ReferenceData[T](options: List[T], default: T, aggregate: T) {
  val allValues: Set[T] = (aggregate :: options).toSet
}

object ReferenceData {

  // default formatter does not work for generic types, has to define fields manually...
  implicit def referenceDataFormat[T : Format]: Format[ReferenceData[T]] =
    ((__ \ "options").format[List[T]] ~
      (__ \ "default").format[T] ~
      (__ \ "aggregate").format[T]
      )(ReferenceData.apply, unlift(ReferenceData.unapply))
}
