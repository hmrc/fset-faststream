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

package fixture

import model.{ Alternatives, LocationPreference, Preferences }

object PreferencesFixture {
  def preferences(location1Scheme1: String, location1Scheme2: Option[String] = None, location2Scheme1: Option[String] = None,
    location2Scheme2: Option[String] = None, alternativeScheme: Option[Boolean] = None) =
    Preferences(
      LocationPreference("London", "London", location1Scheme1, location1Scheme2),
      location2Scheme1.map(LocationPreference("London", "Reading", _, location2Scheme2)),
      None,
      alternativeScheme.map(a => Alternatives(a, a))
    )
}
