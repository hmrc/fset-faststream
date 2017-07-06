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

package model.report

import org.joda.time.DateTime
import play.api.libs.json.Json

case class TimeToOfferPartialItem(
  userId: String,
  fullName: Option[String],
  preferredName: Option[String],
  submittedDate: Option[DateTime],
  exportedDate: Option[DateTime],
  updateExported: Option[DateTime],
  fsacIndicator: Option[String]
)

object TimeToOfferPartialItem {
  implicit val timeToOfferPartialItemFormat = Json.format[TimeToOfferPartialItem]
}
