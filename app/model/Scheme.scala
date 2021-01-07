/*
 * Copyright 2021 HM Revenue & Customs
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
import reactivemongo.bson.{ BSON, BSONHandler, BSONString }

case class SchemeId(value: String) {
  implicit override def toString: String = value
}

object SchemeId {
  // Custom json formatter to serialise to a string
  val schemeIdWritesFormat: Writes[SchemeId] = Writes[SchemeId](scheme => JsString(scheme.value))
  val schemeIdReadsFormat: Reads[SchemeId] = Reads[SchemeId](scheme => JsSuccess(SchemeId(scheme.as[String])))

  implicit val schemeIdFormat = Format(schemeIdReadsFormat, schemeIdWritesFormat)

  // Custom formatter to prevent a nested case object in BSON
  implicit object SchemeIdHandler extends BSONHandler[BSONString, SchemeId] {
      override def write(schemeId: SchemeId): BSONString = BSON.write(schemeId.value)
      override def read(bson: BSONString): SchemeId = SchemeId(bson.value)
  }
}

case class Degree(
  required: String,
  specificRequirement: Boolean
)

object Degree {
  implicit val degreeFormat = Json.format[Degree]
}

object SiftRequirement extends Enumeration {
  val FORM, NUMERIC_TEST = Value

  implicit val applicationStatusFormat = new Format[SiftRequirement.Value] {
    def reads(json: JsValue) = JsSuccess(SiftRequirement.withName(json.as[String]))
    def writes(myEnum: SiftRequirement.Value) = JsString(myEnum.toString)
  }
}

/** Wrapper for scheme data
  *
  * @param id The scheme ID to be delivered across the wire/stored in DB etc.
  * @param code The abbreviated form
  * @param name The form displayed to end users
  */
case class Scheme(
  id: SchemeId,
  code: String,
  name: String,
  civilServantEligible: Boolean,
  degree: Option[Degree],
  siftRequirement: Option[SiftRequirement.Value],
  siftEvaluationRequired: Boolean,
  fsbType: Option[FsbType],
  schemeGuide: Option[String],
  schemeQuestion: Option[String]
) {

  def isSdip: Boolean = id.value == Scheme.Sdip
  def isEdip: Boolean = id.value == Scheme.Edip

}

object Scheme {

  val Sdip = "Sdip"
  val SdipId = SchemeId(Sdip)
  val Edip = "Edip"
  val EdipId = SchemeId(Edip)

  implicit val schemeFormat: OFormat[Scheme] = Json.format[Scheme]

  // scalastyle:off parameter.number
  def apply(id: String, code: String, name: String, civilServantEligible: Boolean, degree: Option[Degree],
            siftRequirement: Option[SiftRequirement.Value], siftEvaluationRequired: Boolean,
    fsbType: Option[FsbType], schemeGuide: Option[String], schemeQuestion: Option[String]
  ): Scheme =
    Scheme(SchemeId(id), code, name, civilServantEligible, degree, siftRequirement, siftEvaluationRequired,
      fsbType, schemeGuide, schemeQuestion)
  // scalastyle:on


  def isSdip(id: SchemeId): Boolean = id.value == Sdip
  def isEdip(id: SchemeId): Boolean = id.value == Edip
}
