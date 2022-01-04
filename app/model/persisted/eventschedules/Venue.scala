/*
 * Copyright 2022 HM Revenue & Customs
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

package model.persisted.eventschedules

import play.api.libs.json.{ Json, OFormat }
import reactivemongo.bson.Macros

// TODO: it's not really description - it's display value we will use on UI.
case class Venue(name: String, description: String)
object Venue {
  implicit val venueFormat: OFormat[Venue] = Json.format[Venue]
  implicit val venueHandler = Macros.handler[Venue]
}
