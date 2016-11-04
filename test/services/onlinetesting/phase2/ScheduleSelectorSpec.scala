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

package services.onlinetesting.phase2

import config.Phase2ScheduleExamples._
import config.{ Phase2Schedule, Phase2TestsConfig }
import org.scalatestplus.play.PlaySpec

class ScheduleSelectorSpec extends PlaySpec {

  "get random schedule" should {
    "throw an exception when no schedules are configured" in {
      val selector = createSelector(Map())

      intercept[IllegalArgumentException] {
        selector.getRandomScheduleWithName()
      }
    }

    "return a schedule randomly" in {
      val schedules = Map("daro" -> DaroShedule, "irad" -> IradShedule, "ward" -> WardShedule)
      val selector = createSelector(schedules)
      val randomSchedules = 1 to 1000 map (_ => selector.getRandomScheduleWithName())

      randomSchedules.distinct must contain theSameElementsAs schedules.values
    }
  }

  private def createSelector(schedules: Map[String, Phase2Schedule]): ScheduleSelector = new ScheduleSelector {
    def testConfig = Phase2TestsConfig(1, 90, schedules)
  }

}
