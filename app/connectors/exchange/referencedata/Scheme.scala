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

package connectors.exchange.referencedata

import play.api.libs.json._

case class SchemeId(value: String)

object SchemeId {
  // Custom json formatter to serialise to a string
  val schemeIdWritesFormat: Writes[SchemeId] = Writes[SchemeId](scheme => JsString(scheme.value))
  val schemeIdReadsFormat: Reads[SchemeId] = Reads[SchemeId](scheme => JsSuccess(SchemeId(scheme.as[String])))

  implicit val schemeIdFormat = Format(schemeIdReadsFormat, schemeIdWritesFormat)
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
)

object Scheme {
  implicit val schemeFormat = Json.format[Scheme]

  // scalastyle:off parameter.number
  def apply(id: String, code: String, name: String, civilServantEligible: Boolean,
    degree: Option[Degree], siftRequirement: Option[SiftRequirement.Value], siftEvaluationRequired: Boolean,
    fsbType: Option[FsbType], schemeGuide: Option[String], schemeQuestion: Option[String]
  ): Scheme =
    Scheme(SchemeId(id), code, name, civilServantEligible, degree, siftRequirement, siftEvaluationRequired,
      fsbType, schemeGuide, schemeQuestion)
  // scalastyle:on

  val Sdip = "Sdip"
  val SdipId = SchemeId(Sdip)

  val GESDS = "DiplomaticServiceEconomists"
  val GESDSId = SchemeId(GESDS)
}
