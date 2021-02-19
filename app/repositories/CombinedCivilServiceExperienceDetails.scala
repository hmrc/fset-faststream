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

package repositories

// Combined from personal-details and civil-service-experience-details sections of application collection
case class CombinedCivilServiceExperienceDetails(isCivilServant: Option[String],
                                                 isEDIP: Option[String],
                                                 edipYear: Option[String],
                                                 isSDIP: Option[String],
                                                 sdipYear: Option[String],
                                                 otherInternship: Option[String],
                                                 otherInternshipName: Option[String],
                                                 otherInternshipYear: Option[String],
                                                 fastPassCertificate: Option[String]
                                                ) {
  def toList: List[Option[String]] =
    List(isCivilServant, isEDIP, edipYear, isSDIP, sdipYear,
      otherInternship, otherInternshipName, otherInternshipYear, fastPassCertificate)
}
