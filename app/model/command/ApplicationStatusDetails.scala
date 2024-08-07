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

import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.ApplicationStatus
import model.ProgressStatuses.ProgressStatus
import play.api.libs.json.{Json, OFormat}

import java.time.OffsetDateTime

case class ApplicationStatusDetails(status: ApplicationStatus,
                                    applicationRoute: ApplicationRoute,
                                    latestProgressStatus: Option[ProgressStatus],
                                    statusDate: Option[OffsetDateTime] = None,
                                    overrideSubmissionDeadline: Option[OffsetDateTime]) {
  def toExchange: ApplicationStatusDetailsExchange = {
    ApplicationStatusDetailsExchange(
      status,
      applicationRoute,
      latestProgressStatus,
      statusDate,
      overrideSubmissionDeadline
    )
  }
}

object ApplicationStatusDetails { // This is needed for Mongo DateTime serialization
  implicit val applicationStatusDetailsFormat: OFormat[ApplicationStatusDetails] = Json.format[ApplicationStatusDetails]
}

case class ApplicationStatusDetailsExchange(status: ApplicationStatus,
                                    applicationRoute: ApplicationRoute,
                                    latestProgressStatus: Option[ProgressStatus],
                                    statusDate: Option[OffsetDateTime] = None,
                                    overrideSubmissionDeadline: Option[OffsetDateTime])

object ApplicationStatusDetailsExchange {
  implicit val applicationStatusDetailsFormat: OFormat[ApplicationStatusDetailsExchange] = Json.format[ApplicationStatusDetailsExchange]
}
