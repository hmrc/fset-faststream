/*
 * Copyright 2023 HM Revenue & Customs
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

import model.CivilServantAndInternshipType.CivilServantAndInternshipType
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

import play.api.libs.json.Json

case class CivilServiceExperienceDetails(
  applicable: Boolean,
  civilServantAndInternshipTypes: Option[Seq[CivilServantAndInternshipType]] = None,
  edipYear: Option[String] = None,
  sdipYear: Option[String] = None,
  otherInternshipName: Option[String] = None,
  otherInternshipYear: Option[String] = None,
  fastPassReceived: Option[Boolean] = None,
  fastPassAccepted: Option[Boolean] = None,
  certificateNumber: Option[String] = None
) {
  override def toString = {
    s"applicable=$applicable," +
      s"civilServantAndInternshipTypes=$civilServantAndInternshipTypes," +
      s"edipYear=$edipYear," +
      s"sdipYear=$sdipYear," +
      s"otherInternshipName=$otherInternshipName," +
      s"otherInternshipYear=$otherInternshipYear," +
      s"fastPassReceived=$fastPassReceived," +
      s"fastPassAccepted=$fastPassAccepted," +
      s"certificateNumber=$certificateNumber"
  }
}

object CivilServiceExperienceDetails {
  implicit val civilServiceExperienceDetailsFormat = Json.format[CivilServiceExperienceDetails]

  // Provide an explicit mongo format here to deal with the sub-document root
  val root = "civil-service-experience-details"
  val mongoFormat: Format[CivilServiceExperienceDetails] = (
    (__ \ root \ "applicable").format[Boolean] and
      (__ \ root \ "civilServantAndInternshipTypes").formatNullable[Seq[CivilServantAndInternshipType]] and
      (__ \ root \ "edipYear").formatNullable[String] and
      (__ \ root \ "sdipYear").formatNullable[String] and
      (__ \ root \ "otherInternshipName").formatNullable[String] and
      (__ \ root \ "otherInternshipYear").formatNullable[String] and
      (__ \ root \ "fastPassReceived").formatNullable[Boolean] and
      (__ \ root \ "fastPassAccepted").formatNullable[Boolean] and
      (__ \ root \ "certificateNumber").formatNullable[String]
    )(CivilServiceExperienceDetails.apply, unlift(CivilServiceExperienceDetails.unapply))
}
