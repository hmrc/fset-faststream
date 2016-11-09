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

package model.exchange.passmarksettings

import play.api.libs.json.Json
import reactivemongo.bson.Macros

trait PassMarkThresholds

trait Phase1Thresholds {
  def situational: PassMarkThreshold
  def behavioural: PassMarkThreshold
}

trait Phase2Thresholds {
  def etray: PassMarkThreshold
}

case class Phase1PassMarkThresholds(
  situational: PassMarkThreshold,
  behavioural: PassMarkThreshold
) extends PassMarkThresholds with Phase1Thresholds

object Phase1PassMarkThresholds {
  implicit val phase1PassMarkThresholds = Json.format[Phase1PassMarkThresholds]
  implicit val phase1PassMarkThresholdsHandler = Macros.handler[Phase1PassMarkThresholds]
}

case class Phase2PassMarkThresholds(
  etray: PassMarkThreshold
) extends PassMarkThresholds with Phase2Thresholds

object Phase2PassMarkThresholds {
  implicit val phase1PassMarkThresholds = Json.format[Phase2PassMarkThresholds]
  implicit val phase1PassMarkThresholdsHandler = Macros.handler[Phase2PassMarkThresholds]
}
