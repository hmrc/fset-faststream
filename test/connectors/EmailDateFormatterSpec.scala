/*
 * Copyright 2023 HM Revenue & Customs
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

package connectors

import org.scalatestplus.play.PlaySpec

import java.time.{LocalDate, OffsetDateTime}
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class EmailDateFormatterSpec extends PlaySpec {

  "EmailDateFormatterSpec" should {
    "format a LocalDate" in new TestFixture {
      dateFormatter.toDate(localDate) mustBe "31 January 2000"
    }

    "format the expiry time in the morning" in new TestFixture {
      dateFormatter.toExpiryTime(dateTime) mustBe "1 August 2017 at 1:00am"
    }

    "format the expiry time in the afternoon" in new TestFixture {
      dateFormatter.toExpiryTime(OffsetDateTime.parse("2017-08-01T12:00:00Z")) mustBe "1 August 2017 at 1:00pm"
    }

    "format the confirm time" in new TestFixture {
      dateFormatter.toConfirmTime(dateTime) mustBe "1 August 2017, 1:00am"
    }

    "process timeLeftInHours" in new TestFixture {
      dateFormatter.convertToHoursOrDays(TimeUnit.DAYS, timeLeftInHours = 24) mustBe "1"
      dateFormatter.convertToHoursOrDays(TimeUnit.DAYS, timeLeftInHours = 47) mustBe "1"
      dateFormatter.convertToHoursOrDays(TimeUnit.DAYS, timeLeftInHours = 49) mustBe "2"
      dateFormatter.convertToHoursOrDays(TimeUnit.HOURS, timeLeftInHours = 1) mustBe "1"
      dateFormatter.convertToHoursOrDays(TimeUnit.HOURS, timeLeftInHours = 24) mustBe "24"
    }
  }

  trait TestFixture {
    val dateFormatter = EmailDateFormatter
    val localDate = LocalDate.parse("2000-01-31", DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    // 1st August 2017 at midnight utc will be +1 because of BST
    val dateTime = OffsetDateTime.parse("2017-08-01T00:00:00Z")
  }
}
