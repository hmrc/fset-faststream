/*
 * Copyright 2021 HM Revenue & Customs
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

package services

import org.joda.time.LocalDateTime
import testkit.UnitSpec

class GBTimeZoneServiceSpec extends UnitSpec {
  val service = GBTimeZoneService

  "Time Zone Service (GB)" should {
    "advertise 'Europe/London' as its timezone" in {
      service.timeZone.getID mustBe "Europe/London"
    }

    "convert UTC time to GB time" in {
      // UTC time which maps onto a British _SUMMER_ time (UTC+1)
      val input = new LocalDateTime(2016, 3, 27, 1, 30)

      val expected = new LocalDateTime(2016, 3, 27, 2, 30)
      val actual = service.localize(getUtcMillis(input))

      actual mustBe expected
    }

    "make no changes when UTC and GB are equal" in {
      // UTC time which maps onto a British _WINTER_ time (UTC+0)
      val input = new LocalDateTime(2016, 3, 27, 0, 30)

      val expected = new LocalDateTime(2016, 3, 27, 0, 30)
      val actual = service.localize(getUtcMillis(input))

      actual mustBe expected
    }
  }

  def getUtcMillis(localDateTime: LocalDateTime): Long =
    localDateTime.toDateTime(org.joda.time.DateTimeZone.UTC).getMillis
}
