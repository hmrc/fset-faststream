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

import model.ApplicationRoute.ApplicationRoute
import model.persisted.ContactDetailsWithId
import play.api.libs.json.Json

case class InternshipReportItem(
  applicationRoute: ApplicationRoute,
  progressStatus: Option[String],
  firstName: Option[String],
  lastName: Option[String],
  preferredName: Option[String],
  email: Option[String],
  guaranteedInterviewScheme: Option[String],
  behaviouralTScore: Option[String],
  situationalTScore: Option[String]
)

case object InternshipReportItem {
  def apply(application: ApplicationForInternshipReport, contactDetails: ContactDetailsWithId): InternshipReportItem = {
    InternshipReportItem(
      applicationRoute = application.applicationRoute,
      progressStatus = application.progressStatus,
      firstName = application.firstName,
      lastName = application.lastName,
      preferredName = application.preferredName,
      email = Some(contactDetails.email),
      guaranteedInterviewScheme = Some(if (application.guaranteedInterviewScheme.getOrElse(false)) "Y" else "N"),
      behaviouralTScore = application.behaviouralTScore.map(_.toString),
      situationalTScore = application.situationalTScore.map(_.toString)
    )
  }

  implicit val internshipReportItemFormat = Json.format[InternshipReportItem]
}
