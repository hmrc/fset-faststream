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

package model

import model.OnlineTestApplicationExamples._
import testkit.UnitSpec

class OnlineTestApplicationSpec extends UnitSpec {

  "Online Test" should {
    "be invigilated e-tray when needs online adjustments and invigilated e-tray are set" in {
      val test = OnlineTest.copy(
        needsOnlineAdjustments = true,
        eTrayAdjustments = Some(AdjustmentDetail(invigilatedInfo = Some("invigilated e-tray")))
      )
      test.isInvigilatedETray mustBe true
    }

    "be not invigilated e-tray when online adjustments are not set" in {
      val test = OnlineTest.copy(needsOnlineAdjustments = false)
      test.isInvigilatedETray mustBe false
    }

    "be not invigilated e-tray when invigilated e-tray info is not set" in {
      val test = OnlineTest.copy(needsOnlineAdjustments = true)
      test.isInvigilatedETray mustBe false
    }
  }
}
