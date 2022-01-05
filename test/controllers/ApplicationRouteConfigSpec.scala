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

package controllers

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import config.ApplicationRouteFrontendConfig
import testkit.UnitSpec

class ApplicationRouteStateSpec extends UnitSpec {
  "New Accounts creation and submit applications" should {
    "be enabled when there is no start date and disable date" in {
      val config = ApplicationRouteStateImpl(ApplicationRouteFrontendConfig(None, None, None, None))
      config.newAccountsStarted mustBe true
      config.newAccountsEnabled mustBe true
      config.applicationsSubmitEnabled mustBe true
    }

    "be enabled when there is no start date" in {
      val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
      val futureDate = LocalDateTime.now.plusDays(2L).format(format)
      val config = ApplicationRouteStateImpl(ApplicationRouteFrontendConfig.read(None, None, Some(futureDate), Some(futureDate)))

      config.newAccountsStarted mustBe true
      config.newAccountsEnabled mustBe true
      config.applicationsSubmitEnabled mustBe true
    }

    "be enabled when the disable date is in the future and start date is in the past" in {
      val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
      val pastDate = LocalDateTime.now.minusDays(2L).format(format)
      val futureDate = LocalDateTime.now.plusDays(2L).format(format)

      val config = ApplicationRouteStateImpl(ApplicationRouteFrontendConfig.read(None, Some(pastDate), Some(futureDate), Some(futureDate)))
      config.newAccountsStarted mustBe true
      config.applicationsSubmitEnabled mustBe true
      config.newAccountsEnabled mustBe true
    }

    "be disabled when the disable date is in the past and start date is in the past" in {
      val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
      val startDate = LocalDateTime.now.minusDays(1L).format(format)
      val pastDisableDate = LocalDateTime.now.minusMinutes(1L).format(format)

      val config = ApplicationRouteStateImpl(ApplicationRouteFrontendConfig.read(None,
        Some(startDate), Some(pastDisableDate), Some(pastDisableDate)))
      config.newAccountsStarted mustBe true
      config.applicationsSubmitEnabled mustBe false
      config.newAccountsEnabled mustBe false
    }

    "throw an exception when start date is in the future and disable date is in the past" in {
      val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
      val futureDate = LocalDateTime.now.plusDays(2L).format(format)
      val pastDate = LocalDateTime.now.minusMinutes(1L).format(format)

      an[IllegalArgumentException] must be thrownBy ApplicationRouteStateImpl(ApplicationRouteFrontendConfig.read(None, Some(futureDate),
        Some(pastDate), Some(pastDate)))
    }

    "be disabled when start date is in the future" in {
      val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
      val startDate = LocalDateTime.now.plusDays(2L).format(format)
      val blockDate = LocalDateTime.now.plusDays(3L).format(format)

      val config = ApplicationRouteStateImpl(ApplicationRouteFrontendConfig.read(None, Some(startDate), Some(blockDate), Some(blockDate)))
      config.newAccountsStarted mustBe false
      config.applicationsSubmitEnabled mustBe true
      config.newAccountsEnabled mustBe true
    }
  }
}
