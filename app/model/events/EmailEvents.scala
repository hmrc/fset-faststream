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

package model.events

import model.events.EventTypes.EventType

sealed trait EmailEvent extends EventType {
  val to: String
  val name: String
  val template: Option[String] = None

  require(to.contains("@"))
}

object EmailEvents {
  case class ApplicationSubmitted(to: String, name: String) extends EmailEvent
  case class ApplicationWithdrawn(to: String, name: String) extends EmailEvent
  case class AdjustmentsConfirmed(to: String, name: String, etrayAdjustments: String, videoAdjustments: String) extends EmailEvent
  case class AdjustmentsChanged(to: String, name: String, etrayAdjustments: String, videoAdjustments: String) extends EmailEvent
}
