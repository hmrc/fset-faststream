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

import play.api.libs.json.{ Format, JsString, JsSuccess, JsValue }
import reactivemongo.bson.{ BSON, BSONHandler, BSONString }
//TODO: delete when we update the reports
object InternshipType extends Enumeration {

  type InternshipType = Value

  val EDIP, SDIPPreviousYear, SDIPCurrentYear = Value

  implicit val internshipTypeFormat = new Format[InternshipType] {
    def reads(json: JsValue) = JsSuccess(InternshipType.withName(json.as[String]))
    def writes(myEnum: InternshipType) = JsString(myEnum.toString)
  }

  implicit object BSONEnumHandler extends BSONHandler[BSONString, InternshipType] {
    def read(doc: BSONString) = InternshipType.withName(doc.value)
    def write(stats: InternshipType) = BSON.write(stats.toString)
  }
}
