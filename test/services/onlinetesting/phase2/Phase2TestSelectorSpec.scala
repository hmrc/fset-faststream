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

package services.onlinetesting.phase2

import config.Phase2ScheduleExamples._
import config.{ Phase2Schedule, Phase2TestsConfig }
import testkit.UnitSpec

class Phase2TestSelectorSpec extends UnitSpec {

  "get random schedule" should {
    "throw an exception when no schedules are configured" in {
      val selector = createSelector(Map())

      intercept[IllegalArgumentException] {
        selector.getNextSchedule()
      }
    }

    "return a schedule randomly" in {
      val schedules = Map("daro" -> DaroSchedule, "irad" -> IradSchedule, "ward" -> WardSchedule)
      val selector = createSelector(schedules)
      val randomSchedules = 1 to 1000 map (_ => selector.getNextSchedule())

      randomSchedules.distinct must contain theSameElementsAs schedules
    }

    "return a schedule randomly for the first n schedules defined and then repeat those schedules in subsequent calls" in {
      val schedules = Map("daro" -> DaroSchedule, "irad" -> IradSchedule, "ward" -> WardSchedule)
      val selector = createSelector(schedules)

      def createSchedules(startingSchedule: List[(String, Phase2Schedule)]) = {
        (1 to schedules.size).foldLeft(startingSchedule)((list, _) =>
          list :+ selector.getNextSchedule(list.map{case(_, s) => s.scheduleId}))
      }

      val randomSchedules = createSchedules(List.empty[(String, Phase2Schedule)])

      randomSchedules.distinct must contain theSameElementsAs schedules

      val randomSchedulesCombined = createSchedules(randomSchedules)

      randomSchedulesCombined must be(randomSchedules ::: randomSchedules)
    }
  }


  private def createSelector(schedules: Map[String, Phase2Schedule]): Phase2TestSelector = new Phase2TestSelector {
    def testConfig = Phase2TestsConfig(1, 90, schedules)
  }
}
