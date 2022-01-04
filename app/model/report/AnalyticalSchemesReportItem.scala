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

package model.report

import model.persisted.ContactDetailsWithId
import play.api.libs.json.Json

case class AnalyticalSchemesReportItem(firstName: Option[String],
                                       lastName: Option[String],
                                       email: Option[String],
                                       firstSchemePreference: Option[String],
                                       guaranteedInterviewScheme: Option[String],
                                       behaviouralTScore: Option[String],
                                       situationalTScore: Option[String],
                                       etrayTScore: Option[String],
                                       overallVideoInterviewScore: Option[String]
                         )

object AnalyticalSchemesReportItem {
  def apply(application: ApplicationForAnalyticalSchemesReport, contactDetails: ContactDetailsWithId): AnalyticalSchemesReportItem = {
    AnalyticalSchemesReportItem(
      firstName = application.firstName,
      lastName = application.lastName,
      email = Some(contactDetails.email),
      firstSchemePreference = application.firstSchemePreference,
      guaranteedInterviewScheme = Some(if (application.guaranteedInterviewScheme.getOrElse(false)) "Y" else "N"),
      behaviouralTScore = application.behaviouralTScore.map(_.toString),
      situationalTScore = application.situationalTScore.map(_.toString),
      etrayTScore = application.etrayTScore.map(_.toString),
      overallVideoInterviewScore = application.overallVideoScore.map(_.toString)
    )
  }

  implicit val analyticalSchemesReportItemFormat = Json.format[AnalyticalSchemesReportItem]
}
