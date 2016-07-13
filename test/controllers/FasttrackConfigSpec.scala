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

package controllers

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import config.FasttrackFrontendConfig
import org.scalatestplus.play.PlaySpec

class FasttrackConfigSpec extends PlaySpec {
  "New Accounts creation and submit applications" should {
    "be enabled when there is no disable date" in {
      val config = FasttrackConfig(FasttrackFrontendConfig(None, None))
      config.newAccountsEnabled must be(true)
      config.applicationsSubmitEnabled must be(true)
    }

    "be enabled when the disable date is in the future" in {
      val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
      val futureDate = LocalDateTime.now.plusDays(2L).format(format)

      val config = FasttrackConfig(FasttrackFrontendConfig.read(Some(futureDate), Some(futureDate)))
      config.applicationsSubmitEnabled must be(true)
      config.newAccountsEnabled must be(true)
    }

    "be disabled when the disable date is in the past" in {
      val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
      val futureDate = LocalDateTime.now.minusMinutes(1L).format(format)

      val config = FasttrackConfig(FasttrackFrontendConfig.read(Some(futureDate), Some(futureDate)))
      config.applicationsSubmitEnabled must be(false)
      config.newAccountsEnabled must be(false)
    }
  }

}
