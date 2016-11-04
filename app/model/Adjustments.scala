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

package model

import play.api.libs.json.Json

case class Adjustments(
  adjustments: Option[List[String]],
  adjustmentsConfirmed: Option[Boolean],
  etray: Option[AdjustmentDetail],
  video: Option[AdjustmentDetail]
)

case class AdjustmentDetail(
  timeNeeded: Option[Int] = None,
  invigilatedInfo: Option[String] = None,
  otherInfo: Option[String] = None
)

object Adjustments{
  implicit val adjustmentDetailFormat = Json.format[AdjustmentDetail]
  implicit val adjustmentsFormat = Json.format[Adjustments]
}