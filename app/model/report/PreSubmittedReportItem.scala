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

import connectors.ExchangeObjects.Candidate
import model.Commands.PhoneNumber
import model.persisted.ApplicationIdsAndStatus
import play.api.libs.json.{ Json, OFormat }

case class PreSubmittedReportItem(firstName: String,
                                  lastName: String,
                                  preferredName: Option[String],
                                  email: String,
                                  phoneNumber: Option[PhoneNumber],
                                  applicationStatus: String,
                                  applicationRoute: String,
                                  progressStatus: Option[String])

object PreSubmittedReportItem {
  implicit val format: OFormat[PreSubmittedReportItem] = Json.format[PreSubmittedReportItem]

  def apply(user: Candidate, preferredName: Option[String], phoneNumber: Option[PhoneNumber],
            app: ApplicationIdsAndStatus): PreSubmittedReportItem = {
    PreSubmittedReportItem(
      user.firstName, user.lastName, preferredName, user.email, phoneNumber, app.applicationStatus, app.applicationRoute, app.progressStatus
    )
  }
}
