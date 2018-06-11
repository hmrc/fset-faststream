/*
 * Copyright 2018 HM Revenue & Customs
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
import org.joda.time.DateTime
import play.api.libs.json.Json

case class ApplicationStatusDetails(applicationStatus: String, // TODO: change to ApplicationStatus type
                                    applicationRoute: ApplicationRoute,
                                    latestProgressStatus: Option[ProgressStatus],
                                    statusDate: Option[DateTime] = None,
                                    overrideSubmissionDeadline: Option[DateTime])

object ApplicationStatusDetails {
  implicit val applicationStatusDetailsFormat = Json.format[ApplicationStatusDetails]
}
