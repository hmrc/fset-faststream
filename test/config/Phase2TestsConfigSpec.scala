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

package config

import config.Phase2ScheduleExamples._
import testkit.UnitSpec

class Phase2TestsConfigSpec extends UnitSpec {

  "Schedule name by schedule id" should {
    val config = Phase2TestsConfig(10, 20, Map("daro" -> DaroSchedule), None)

    "return schedule name by schedule id" in {
      val name = config.scheduleNameByScheduleId(DaroSchedule.scheduleId)
      name mustBe "daro"
    }

    "throw an exception when no schedule id" in {
      an[IllegalArgumentException] must be thrownBy {
        config.scheduleNameByScheduleId(WardSchedule.scheduleId)
      }
    }
  }

  "schedule for invigilated e-tray" should {
    "return daro" in {
      val config = Phase2TestsConfig(10, 20, Map("oria" -> OriaSchedule, "daro" -> DaroSchedule), None)
      config.scheduleForInvigilatedETray mustBe DaroSchedule
    }

    "throw an exception when there is no daro schedule" in {
      an[IllegalArgumentException] must be thrownBy {
        Phase2TestsConfig(10, 20, Map("oria" -> OriaSchedule), None)
      }
    }
  }

  "when configuring phase2 tests to always invite a candidate to a specific eTray" should {
    "throw an exception when the specified eTray is not in the schedule list" in {
      an[IllegalArgumentException] must be thrownBy {
        Phase2TestsConfig(10, 20, Map("oria" -> OriaSchedule), Some("BOOM"))
      }
    }
  }
}
