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

package model

import model.ApplicationStatus._
import play.api.libs.json.{ JsSuccess, Json }
import testkit.UnitSpec

class ApplicationStatusSpec extends UnitSpec {

  "Application status" should {
    "read JSON formatter should ignore case sensitivity" in {
      ApplicationStatus.applicationStatusFormat.reads(Json.toJson("submitted")) mustBe JsSuccess(SUBMITTED)
      ApplicationStatus.applicationStatusFormat.reads(Json.toJson("SUBMITTED")) mustBe JsSuccess(SUBMITTED)
    }

    "follow the contract that toString returns the key: capitalised name" in {
      ApplicationStatus.values.foreach { s =>
        ApplicationStatus.withName(s.toString) mustBe s
      }
    }
  }
}
