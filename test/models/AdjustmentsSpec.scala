/*
 * Copyright 2020 HM Revenue & Customs
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

package models

import testkit.UnitSpec

class AdjustmentsSpec extends UnitSpec {

  "Adjustments" should {
    "be invigilated Work based scenarios when it is approved" in {
      Adjustments(Some(List("etrayInvigilated")), Some(true)).isInvigilatedETrayApproved mustBe true
    }

    "be not invigilated Work based scenarios when it is not approved" in {
      Adjustments(Some(List("etrayInvigilated")), Some(false)).isInvigilatedETrayApproved mustBe false
    }

    "be not invigilated Work based scenarios when it is not present on the adjustments list" in {
      Adjustments(Some(List()), Some(true)).isInvigilatedETrayApproved mustBe false
    }
  }

}
