/*
 * Copyright 2018 HM Revenue & Customs
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

package connectors.exchange

import org.joda.time.DateTime
import play.api.libs.json.Json

case class Phase1TestGroupWithNames(expirationDate: DateTime, activeTests: Map[String, CubiksTest]) {
  def tests = activeTests.values
}

object Phase1TestGroupWithNames {
  implicit val phase1TestGroupWithNamesFormat = Json.format[Phase1TestGroupWithNames]
}

case class Phase2TestGroupWithActiveTest(expirationDate: DateTime, activeTest: CubiksTest)

object Phase2TestGroupWithActiveTest {
  implicit val phase2TestGroupWithNamesFormat = Json.format[Phase2TestGroupWithActiveTest]
}

case class SiftTestGroupWithActiveTest(expirationDate: DateTime, activeTest: CubiksTest)

object SiftTestGroupWithActiveTest {
  implicit val siftTestGroupFormat = Json.format[SiftTestGroupWithActiveTest]
}

