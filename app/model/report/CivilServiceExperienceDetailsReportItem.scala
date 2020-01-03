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

package model.report

import model.persisted.CivilServiceExperienceDetailsForDiversityReport
import play.api.libs.json.Json

case class CivilServiceExperienceDetailsReportItem(
                                                    isCivilServant: Option[String],
                                                    isFastTrack: Option[String],
                                                    isEDIP: Option[String],
                                                    isSDIP: Option[String],
                                                    isEligibleForFastPass: Option[String],
                                                    fastPassCertificate: Option[String])

object CivilServiceExperienceDetailsReportItem {
  implicit val civilServiceExperienceDetailsReportItemFormat = Json.format[CivilServiceExperienceDetailsReportItem]

  // If you add a custom apply() to a case class companion object then Json.reads and Json.writes fail
  def create(civilServiceExperience: CivilServiceExperienceDetailsForDiversityReport): CivilServiceExperienceDetailsReportItem = {
    CivilServiceExperienceDetailsReportItem(
      isCivilServant = civilServiceExperience.isCivilServant,
      isFastTrack = civilServiceExperience.isFastTrack,
      isEDIP = civilServiceExperience.isEDIP,
      isSDIP = civilServiceExperience.isSDIP,
      isEligibleForFastPass = civilServiceExperience.isEligibleForFastPass,
      fastPassCertificate = civilServiceExperience.fastPassCertificate)
  }
}
