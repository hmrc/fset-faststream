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

package model.exchange

import org.joda.time.DateTime
import play.api.libs.json.Json


case class Phase1Test(testType: String, // TODO maybe this should be a const?
  usedForResults: Boolean,
  testProvider: String = "cubiks",
  testUrl: String,
  invitationDate: DateTime,
  started: Boolean = false,
  completed: Boolean = false,
  resultsReadyToDownload: Boolean = false
)

object Phase1Test{
  implicit def phase1TestFormat = Json.format[Phase1Test]
}

case class Phase1TestProfile(expirationDate: DateTime,
  tests: List[Phase1Test]
)

object Phase1TestProfile {
  implicit def phase1TestProfileFormat = Json.format[Phase1TestProfile]
}
