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

package model.command

import model.{Address, CivilServiceExperienceDetails, FSACIndicator}
import model.Commands.{PhoneNumber, PostCode}
import model.persisted.{ContactDetails, PersonalDetails}
import play.api.libs.json.{Json, OFormat}

import java.time.LocalDate

case class GeneralDetails(firstName: String,
                          lastName: String,
                          preferredName: String,
                          email: String,
                          dateOfBirth: LocalDate,
                          outsideUk: Boolean,
                          address: Address,
                          postCode: Option[PostCode],
                          fsacIndicator: Option[FSACIndicator],
                          country: Option[String],
                          phone: PhoneNumber,
                          civilServiceExperienceDetails: Option[CivilServiceExperienceDetails],
                          edipCompleted: Option[Boolean],
                          edipYear: Option[String],
                          otherInternshipCompleted: Option[Boolean],
                          otherInternshipName: Option[String],
                          otherInternshipYear: Option[String],
                          updateApplicationStatus: Option[Boolean] = None)

object GeneralDetails {
  implicit val generalDetailsFormat: OFormat[GeneralDetails] = Json.format[GeneralDetails]

  def apply(pd: PersonalDetails, cd: ContactDetails, fsacIndicator: FSACIndicator,
            civilServiceExperienceDetails: Option[CivilServiceExperienceDetails]): GeneralDetails = {
    GeneralDetails(
      pd.firstName, pd.lastName, pd.preferredName, cd.email,
      pd.dateOfBirth, cd.outsideUk, cd.address, cd.postCode,
      Some(fsacIndicator), cd.country, cd.phone, civilServiceExperienceDetails,
      pd.edipCompleted, pd.edipYear, pd.otherInternshipCompleted,
      pd.otherInternshipName, pd.otherInternshipYear
    )
  }
}
