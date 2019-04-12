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

package helpers

import play.api.libs.json._

object TitleCaseJsonNaming {

  private def mapKeys[A, B, C](m: Seq[(A, B)])(f: A => C): Seq[(C, B)] =
    m.map { case (k, v) => (f(k), v) }

  private def titleCaseReads[T](parentReads: JsValue => JsResult[T]): Reads[T] = new Reads[T] {
    override def reads(json: JsValue): JsResult[T] = {
      parentReads(json match {
        case obj: JsObject =>
          val mappedObj = mapKeys(obj.fields)(StringUtil.titleToCamelCase)
          JsObject(mappedObj)
        case x => x
      })
    }
  }

  private def titleCaseWrites[T](parentWrites: T => JsValue): Writes[T] = new Writes[T] {
    def writes(o: T): JsValue = {
      parentWrites(o) match {
        case obj: JsObject => JsObject(mapKeys(obj.fields)(StringUtil.camelToTitleCase))
        case x => x
      }
    }
  }

  def titleCase[T](format: Format[T]): Format[T] =
    Format(titleCaseReads(format.reads), titleCaseWrites(format.writes))
}
