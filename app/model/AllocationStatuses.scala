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

package model

import play.api.libs.json.{Format, JsString, JsSuccess, JsValue}
import reactivemongo.bson.{BSON, BSONHandler, BSONString}

object AllocationStatuses extends Enumeration {
  type AllocationStatus = Value

  val UNCONFIRMED, CONFIRMED, DECLINED, REMOVED = Value

  implicit val applicationStatusFormat = new Format[AllocationStatus] {
    def reads(json: JsValue) = JsSuccess(AllocationStatuses.withName(json.as[String].toUpperCase()))
    def writes(myEnum: AllocationStatus) = JsString(myEnum.toString)
  }

  implicit object BSONEnumHandler extends BSONHandler[BSONString, AllocationStatus] {
    def read(doc: BSONString) = AllocationStatuses.withName(doc.value.toUpperCase())
    def write(stats: AllocationStatus) = BSON.write(stats.toString)
  }
}
