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

import model.ApplicationRoute.ApplicationRoute
import model.Commands.PostCode
import org.joda.time.LocalDate
import play.api.libs.json.JodaWrites._ // This is needed for DateTime serialization
import play.api.libs.json.JodaReads._ // This is needed for DateTime serialization
import play.api.libs.json.{ Json, OFormat }

case class Candidate(userId: String,
                     applicationId: Option[String],
                     testAccountId: Option[String],
                     email: Option[String],
                     firstName: Option[String],
                     lastName: Option[String],
                     preferredName: Option[String],
                     dateOfBirth: Option[LocalDate],
                     address: Option[Address],
                     postCode: Option[PostCode],
                     country: Option[String],
                     applicationRoute: Option[ApplicationRoute],
                     applicationStatus: Option[String]
                    ) {

  def name: String = preferredName.getOrElse(firstName.getOrElse(""))
}

object Candidate {
  implicit val candidateFormat: OFormat[Candidate] = Json.format[Candidate]
}

case class CandidateIds(userId: String, applicationId: String)

object CandidateIds {
  implicit val candidateIdFormat: OFormat[CandidateIds] = Json.format[CandidateIds]
}
