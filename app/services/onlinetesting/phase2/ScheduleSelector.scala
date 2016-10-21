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

import config.{ Phase2Schedule, Phase2TestsConfig }

import scala.util.Random

trait ScheduleSelector {
  def testConfig: Phase2TestsConfig

  def getRandomSchedule(currentScheduleIds: List[Int] = Nil): Phase2Schedule = {
    val schedules = getAvailableSchedules(currentScheduleIds)

    require(schedules.nonEmpty, "Phase2 schedule list cannot be empty")
    schedules.toSeq(Random.nextInt(schedules.size))
  }

  def getAvailableSchedules(currentScheduleIds: List[Int]) = {
    testConfig.schedules.values.filter(schedule => !currentScheduleIds.contains(schedule.scheduleId))
  }

  def schedulesAvailable(currentScheduleIds: List[Int]) = {
    getAvailableSchedules(currentScheduleIds).nonEmpty
  }
}
