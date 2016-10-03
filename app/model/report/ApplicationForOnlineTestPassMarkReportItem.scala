/*
 * Copyright 2016 HM Revenue & Customs
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

import model.SchemeType.SchemeType
import model.persisted.AssistanceDetails
import play.api.libs.json.Json

case class ApplicationForOnlineTestPassMarkReportItem(applicationId: String,
                                                      schemes: List[SchemeType],
                                                      disability: Option[String],
                                                      onlineAdjustments: Option[String],
                                                      assessmentCentreAdjustments: Option[String])


object ApplicationForOnlineTestPassMarkReportItem {
  implicit val applicationForOnlineTestReportItemFormat = Json.format[ApplicationForOnlineTestPassMarkReportItem]
}
