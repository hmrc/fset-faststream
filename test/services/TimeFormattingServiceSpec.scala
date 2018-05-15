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

package services

import factories.DateTimeFactory
import org.joda.time.DateTime
import org.mockito.Mockito._
import testkit.UnitWithAppSpec

class TimeFormattingServiceSpec extends UnitWithAppSpec {

  "Time formatting service" should {
    "correctly display days, hours and minutes remaining until expiry" in {
      val dateTimeFactoryMock = mock[DateTimeFactory]
      val now = DateTime.parse("2018-10-01T12:10:00Z")
      when(dateTimeFactoryMock.nowLocalTimeZone).thenReturn(now)

      val testTimeFormattingService = new TimeFormattingService {
        val dateTimeFactory = dateTimeFactoryMock
      }

      val expiryDate = now.plusDays(3).plusHours(3).plusMinutes(11)
      val durationRemaining = testTimeFormattingService.durationFromNow(expiryDate)
      durationRemaining mustBe "3 days, 3 hours and 11 minutes"
    }

    "correctly display hours and minutes remaining until expiry" in {
      val dateTimeFactoryMock = mock[DateTimeFactory]
      val now = DateTime.parse("2018-10-01T12:10:00Z")
      when(dateTimeFactoryMock.nowLocalTimeZone).thenReturn(now)

      val testTimeFormattingService = new TimeFormattingService {
        val dateTimeFactory = dateTimeFactoryMock
      }

      val expiryDate = now.plusHours(3).plusMinutes(11)
      val durationRemaining = testTimeFormattingService.durationFromNow(expiryDate)
      durationRemaining mustBe "3 hours and 11 minutes"
    }

    "correctly display minutes remaining until expiry" in {
      val dateTimeFactoryMock = mock[DateTimeFactory]
      val now = DateTime.parse("2018-10-01T12:10:00Z")
      when(dateTimeFactoryMock.nowLocalTimeZone).thenReturn(now)

      val testTimeFormattingService = new TimeFormattingService {
        val dateTimeFactory = dateTimeFactoryMock
      }

      val expiryDate = now.plusMinutes(11)
      val durationRemaining = testTimeFormattingService.durationFromNow(expiryDate)
      durationRemaining mustBe "11 minutes"
    }

    "correctly display zero minutes remaining until expiry when the time now is the expiry time" in {
      val dateTimeFactoryMock = mock[DateTimeFactory]
      val now = DateTime.parse("2018-10-01T12:10:00Z")
      when(dateTimeFactoryMock.nowLocalTimeZone).thenReturn(now)

      val testTimeFormattingService = new TimeFormattingService {
        val dateTimeFactory = dateTimeFactoryMock
      }

      val expiryDate = now
      val durationRemaining = testTimeFormattingService.durationFromNow(expiryDate)
      durationRemaining mustBe "0 minutes"
    }

    "correctly display zero minutes remaining until expiry when we have less than a minute remaining" in {
      val dateTimeFactoryMock = mock[DateTimeFactory]
      val now = DateTime.parse("2018-10-01T12:10:00Z")
      when(dateTimeFactoryMock.nowLocalTimeZone).thenReturn(now)

      val testTimeFormattingService = new TimeFormattingService {
        val dateTimeFactory = dateTimeFactoryMock
      }

      val expiryDate = now.plusSeconds(10)
      val durationRemaining = testTimeFormattingService.durationFromNow(expiryDate)
      durationRemaining mustBe "0 minutes"
    }

    "correctly display negative minutes remaining once we are past expiry" in {
      val dateTimeFactoryMock = mock[DateTimeFactory]
      val now = DateTime.parse("2018-10-01T12:10:00Z")
      when(dateTimeFactoryMock.nowLocalTimeZone).thenReturn(now)

      val testTimeFormattingService = new TimeFormattingService {
        val dateTimeFactory = dateTimeFactoryMock
      }

      val expiryDate = now.minusMinutes(1)
      val durationRemaining = testTimeFormattingService.durationFromNow(expiryDate)
      durationRemaining mustBe "-1 minutes"
    }
  }
}
