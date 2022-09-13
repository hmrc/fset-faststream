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

package model.persisted

import org.joda.time.LocalDate
import play.api.libs.functional.syntax._
import play.api.libs.json.JodaReads.DefaultJodaLocalDateReads
import play.api.libs.json.JodaWrites.DefaultJodaLocalDateWrites
import play.api.libs.json.Reads._
import play.api.libs.json.{Format, Json, __}

case class PersonalDetails(firstName: String,
                           lastName: String,
                           preferredName: String,
                           dateOfBirth: LocalDate,
                           edipCompleted: Option[Boolean],
                           edipYear: Option[String],
                           otherInternshipCompleted: Option[Boolean],
                           otherInternshipName: Option[String],
                           otherInternshipYear: Option[String]
                          )

object PersonalDetails {

  implicit val personalDetailsFormat = Json.format[PersonalDetails]

  // Provide an explicit mongo format here to deal with the sub-document root
  // This data lives in the application collection
  val root = "personal-details"
  val mongoFormat: Format[PersonalDetails] = (
    (__ \ root \ "firstName").format[String] and
      (__ \ root \ "lastName").format[String] and
      (__ \ root \ "preferredName").format[String] and
      (__ \ root \ "dateOfBirth").format[LocalDate] and
      (__ \ root \ "edipCompleted").formatNullable[Boolean] and
      (__ \ root \ "edipYear").formatNullable[String] and
      (__ \ root \ "otherInternshipCompleted").formatNullable[Boolean] and
      (__ \ root \ "otherInternshipName").formatNullable[String] and
      (__ \ root \ "otherInternshipYear").formatNullable[String]
    )(PersonalDetails.apply, unlift(PersonalDetails.unapply))
}
