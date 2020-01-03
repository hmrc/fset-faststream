/*
 * Copyright 2020 HM Revenue & Customs
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
import reactivemongo.bson.Macros

case class Preferences(
  firstLocation: LocationPreference,
  secondLocation: Option[LocationPreference] = None,
  secondLocationIntended: Option[Boolean] = None,
  alternatives: Option[Alternatives] = None
) {
  lazy val isValid: Boolean =
    hasValidLocations &&
      hasValidSecondLocationIntention &&
      firstLocation.isValid &&
      secondLocation.forall(_.isValid)

  lazy val hasValidLocations: Boolean =
    secondLocation.forall(p => p.region != firstLocation.region || p.location != firstLocation.location)

  // scalastyle:off simplify.boolean.expression - There seems to be a bug in scalastyle when mixed with contains()
  lazy val hasValidSecondLocationIntention: Boolean =
    secondLocation.isEmpty || secondLocationIntended.contains(true)
  // scalastyle:on simplify.boolean.expression
}

object Preferences {
  implicit val jsonFormat = Json.format[Preferences]
  implicit val bsonFormat = Macros.handler[Preferences]
}
