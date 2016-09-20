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

package model

import play.api.libs.json.{ Format, JsString, JsSuccess, JsValue }
import reactivemongo.bson.{ BSON, BSONHandler, BSONString }
import scala.language.implicitConversions

object ApplicationStatus extends Enumeration {
  type ApplicationStatus = Value
  val WITHDRAWN, CREATED, IN_PROGRESS, SUBMITTED = Value
  val PHASE1_TESTS = Value

  implicit def toString(applicationStatus: ApplicationStatus): String = applicationStatus.toString

  implicit val applicationStatusFormat = new Format[ApplicationStatus] {
    def reads(json: JsValue) = JsSuccess(ApplicationStatus.withName(json.as[String]))
    def writes(myEnum: ApplicationStatus) = JsString(myEnum.toString)
  }

  implicit object BSONEnumHandler extends BSONHandler[BSONString, ApplicationStatus] {
    def read(doc: BSONString) = ApplicationStatus.withName(doc.value)
    def write(stats: ApplicationStatus) = BSON.write(stats.toString)
  }
}
