/*
 * Copyright 2022 HM Revenue & Customs
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

import org.mongodb.scala.bson.BsonValue
import play.api.libs.json.{Format, JsString, JsSuccess, JsValue}
import uk.gov.hmrc.mongo.play.json.Codecs
//import reactivemongo.bson.{ BSON, BSONHandler, BSONString }

object CivilServantAndInternshipType extends Enumeration {

  type CivilServantAndInternshipType = Value

  val CivilServant, SDIP, EDIP, OtherInternship = Value

  implicit val internshipTypeFormat = new Format[CivilServantAndInternshipType] {
    def reads(json: JsValue) = JsSuccess(CivilServantAndInternshipType.withName(json.as[String]))
    def writes(myEnum: CivilServantAndInternshipType) = JsString(myEnum.toString)
  }

/*
  implicit object BSONEnumHandler extends BSONHandler[BSONString, CivilServantAndInternshipType] {
    def read(doc: BSONString) = CivilServantAndInternshipType.withName(doc.value)
    def write(stats: CivilServantAndInternshipType) = BSON.write(stats.toString)
  }*/

  implicit class BsonOps(val internshipType: CivilServantAndInternshipType) extends AnyVal {
    def toBson: BsonValue = Codecs.toBson(internshipType)
  }
}
