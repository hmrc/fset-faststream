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

package model

import model.ApplicationRoute.ApplicationRoute
import model.command.ProgressResponse
import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, Json, OFormat, __}

import java.time.OffsetDateTime

case class ApplicationResponse(applicationId: String,
                               applicationStatus: String,
                               applicationRoute: ApplicationRoute,
                               userId: String,
                               testAccountId: String,
                               progressResponse: ProgressResponse,
                               civilServiceExperienceDetails: Option[CivilServiceExperienceDetails],
                               overriddenSubmissionDeadline: Option[OffsetDateTime])

object ApplicationResponse {
  implicit val applicationResponseFormat: OFormat[ApplicationResponse] = Json.format[ApplicationResponse]

  val mongoFormat: Format[ApplicationResponse] = (
    (__ \ "applicationId").format[String] and
      (__ \ "applicationStatus").format[String] and
      (__ \ "applicationRoute").format[ApplicationRoute] and
      (__ \ "userId").format[String] and
      (__ \ "testAccountId").format[String] and
      (__ \ "progressResponse").format[ProgressResponse] and
      (__ \ CivilServiceExperienceDetails.root).formatNullable[CivilServiceExperienceDetails] and
      (__ \ "overriddenSubmissionDeadline").formatNullable[OffsetDateTime]
    )(ApplicationResponse.apply, unlift(ApplicationResponse.unapply))
}
