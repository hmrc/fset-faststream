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

package model.persisted

import play.api.libs.json.{Json, OFormat}
import repositories.CombinedCivilServiceExperienceDetails

case class CivilServiceExperienceDetailsForDiversityReport(
                                                            isCivilServant: Option[String],
                                                            isEDIP: Option[String],
                                                            edipYear: Option[String],
                                                            isSDIP: Option[String],
                                                            sdipYear: Option[String],
                                                            otherInternship: Option[String],
                                                            otherInternshipName: Option[String],
                                                            otherInternshipYear: Option[String],
                                                            fastPassCertificate: Option[String])

object CivilServiceExperienceDetailsForDiversityReport {
  implicit val civilServiceExperienceDetailsForDiversityReportFormat: OFormat[CivilServiceExperienceDetailsForDiversityReport] =
    Json.format[CivilServiceExperienceDetailsForDiversityReport]

  def apply(csed: CombinedCivilServiceExperienceDetails) :CivilServiceExperienceDetailsForDiversityReport = {
    CivilServiceExperienceDetailsForDiversityReport(
      isCivilServant = csed.isCivilServant, isEDIP = csed.isEDIP, edipYear = csed.edipYear, isSDIP = csed.isSDIP,
      sdipYear = csed.sdipYear, otherInternship = csed.otherInternship, otherInternshipName = csed.otherInternshipName,
      otherInternshipYear = csed.otherInternshipYear, fastPassCertificate = csed.fastPassCertificate
    )
  }
}
