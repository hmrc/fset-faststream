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

package services.onlinetesting.phase2

import config.{ Phase2Schedule, Phase2TestsConfig }
import play.api.Logger

import scala.util.Random

trait Phase2TestSelector {
  def testConfig: Phase2TestsConfig

  def schedulesAvailable(currentScheduleIds: List[Int]) = true

  def getNextSchedule(currentScheduleIds: List[Int] = Nil): (String, Phase2Schedule) = {
    val unallocatedExists = getUnallocatedSchedules(currentScheduleIds) != List.empty
    val numberOfSelectors = testConfig.schedules.size

    val result = if (unallocatedExists) {
      getRandomScheduleWithName(currentScheduleIds)
    } else {
      val schedule = testConfig.schedules.values.find(_.scheduleId == currentScheduleIds(currentScheduleIds.size % numberOfSelectors)).head
      getAlwaysChooseSchedule.getOrElse((testConfig.scheduleNameByScheduleId(schedule.scheduleId), schedule))
    }
    testConfig.alwaysChooseSchedule.foreach { scheduleName =>
      Logger.info(s"AlwaysChooseSchedule is configured so all candidates will be invited to take Etray schedule: $scheduleName")
    }
    result
  }

  private def getRandomScheduleWithName(currentScheduleIds: List[Int]): (String, Phase2Schedule) = {
    val schedules = getUnallocatedSchedules(currentScheduleIds)

    require(schedules.nonEmpty, "Phase2 schedule list cannot be empty")
    val schedule = schedules.toSeq(Random.nextInt(schedules.size))
    getAlwaysChooseSchedule.getOrElse((testConfig.scheduleNameByScheduleId(schedule.scheduleId), schedule))
  }

  private def getUnallocatedSchedules(currentScheduleIds: List[Int])=
    testConfig.schedules.values.filter(schedule => !currentScheduleIds.contains(schedule.scheduleId))

  private def getAlwaysChooseSchedule=
    testConfig.alwaysChooseSchedule.map ( scheduleName => (scheduleName, testConfig.schedules(scheduleName)) )
}
