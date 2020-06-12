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

import model.CivilServantAndInternshipType.CivilServantAndInternshipType
import play.api.libs.json.Json
import reactivemongo.bson.Macros

case class CivilServiceExperienceDetails(
  applicable:Boolean,
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
  implicit val civilServiceExperienceDetailsHandler = Macros.handler[CivilServiceExperienceDetails]
}
