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

package config

import java.time.format.{DateTimeFormatter, DateTimeParseException}

import testkit.UnitSpec

class ApplicationRouteFrontendConfigSpec extends UnitSpec {
  val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

  "Faststream frontend configuration reader" should {
    "return configuration without any dates if they are not present" in {
      val faststreamFrontendConfig = ApplicationRouteFrontendConfig.read(None, None, None, None)
      faststreamFrontendConfig mustBe ApplicationRouteFrontendConfig(None, None, None, None)
    }

    "parse and return dates" in {
      val faststreamFrontendConfig = ApplicationRouteFrontendConfig.read(None, Some("2016-02-29T12:01:02"), Some("2016-03-30T12:01:02"),
        Some("2016-04-13T13:03:04"))
      faststreamFrontendConfig.startNewAccountsDate.get.format(format) mustBe "2016-02-29T12:01:02"
      faststreamFrontendConfig.blockNewAccountsDate.get.format(format) mustBe "2016-03-30T12:01:02"
      faststreamFrontendConfig.blockApplicationsDate.get.format(format) mustBe "2016-04-13T13:03:04"
    }

    "be throw when the new accounts disable date is invalid" in {
      an[DateTimeParseException] should be thrownBy ApplicationRouteFrontendConfig.read(None, Some("1/1/1999"), Some("1/1/1999"), None)
    }

    "be throw when the applications disable date is invalid" in {
      an[DateTimeParseException] should be thrownBy ApplicationRouteFrontendConfig.read(None, None, None, Some("1/1/1999"))
    }

    "be throw when the new accounts disable date is invalid by having only one digit segment in time" in {
      an[DateTimeParseException] should be thrownBy ApplicationRouteFrontendConfig.read(
        None, Some("2016-03-30 9:01:02"), Some("2016-03-30 9:01:02"), Some("2016-03-30 12:01:02")
      )
    }

    "be throw when the applications disable date is invalid by having only one digit segment in time" in {
      an[DateTimeParseException] should be thrownBy ApplicationRouteFrontendConfig.read(
        None, Some("2016-03-30T12:01:02"), Some("2016-03-30T12:01:02"), Some("2016-03-30T9:01:02")
      )
    }
  }
}
