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

package model.report

import model.Adjustments
import play.api.libs.json.Json

final case class AdjustmentReportItem(userId: String,
                                      applicationId: Option[String],
                                      firstName: Option[String],
                                      lastName: Option[String],
                                      preferredName: Option[String],
                                      email: Option[String],
                                      telephone: Option[String],
                                      gis: Option[String],
                                      disabilityCategories: Option[List[String]],
                                      otherDisabilityDescription: Option[String],
                                      applicationStatus: Option[String],
                                      needsSupportForOnlineAssessmentDescription: Option[String],
                                      needsSupportAtVenueDescription: Option[String],
                                      hasDisability: Option[String],
                                      hasDisabilityDescription: Option[String],
                                      adjustments: Option[Adjustments],
                                      adjustmentsComment: Option[String])

object AdjustmentReportItem {
  implicit val adjustmentReportFormat = Json.format[AdjustmentReportItem]
}
