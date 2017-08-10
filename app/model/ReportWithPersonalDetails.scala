/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.libs.json.{ Json, OFormat }

case class ReportWithPersonalDetails(applicationId: String, userId: String, progress: Option[String], firstLocation: Option[String],
  firstLocationFirstScheme: Option[String], firstLocationSecondScheme: Option[String], secondLocation: Option[String],
  secondLocationFirstScheme: Option[String], secondLocationSecondScheme: Option[String], alevels: Option[String],
  stemlevels: Option[String], alternativeLocation: Option[String], alternativeScheme: Option[String], hasDisability: Option[String],
  hasAdjustments: Option[String], guaranteedInterview: Option[String], firstName: Option[String], lastName: Option[String],
  preferredName: Option[String], dateOfBirth: Option[String], cubiksUserId: Option[Int])

object ReportWithPersonalDetails {
  implicit val reportWithPersonalDetailsFormat: OFormat[ReportWithPersonalDetails] = Json.format[ReportWithPersonalDetails]
}
