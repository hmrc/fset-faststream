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

package models.page

import models.AdjustmentsExamples._
import org.joda.time.DateTime
import testkit.UnitSpec

class Phase2TestsPageSpec extends UnitSpec {

  "Phase2TestsPage isInvigilatedETrayApproved" should {
    "return true when invigilated eTray has been approved" in {
      val page = Phase2TestsPage(DateTime.now(), None, Some(InvigilatedETrayAdjustment))
      page.isInvigilatedETrayApproved mustBe true
    }

    "return false when invigilated eTray has not been approved" in {
      val page = Phase2TestsPage(DateTime.now(), None, Some(NoAdjustments))
      page.isInvigilatedETrayApproved mustBe false
    }
  }
}
