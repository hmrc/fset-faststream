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

package connectors.exchange.sift

import org.joda.time.DateTime
import testkit.UnitWithAppSpec

class SiftStateSpec extends UnitWithAppSpec {

  "Sift state" should {
    "correctly display the time remaining until expiry" in {
      val now = DateTime.now()
      val siftEnteredDate = now
      val expiryDate = now.plusDays(3).plusHours(3).plusMinutes(11)

      val siftState = SiftState(siftEnteredDate, expiryDate)
      val durationRemaining = siftState.expiryDateDurationRemaining

      durationRemaining mustBe "3 days and 3 hours and 10 minutes"
    }
  }
}
