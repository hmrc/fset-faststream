/*
 * Copyright 2023 HM Revenue & Customs
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

import model.ProgressStatuses._
import model.ReminderNotice.UnrecognisedReminderNoticeProgressStatusException

import scala.concurrent.duration.{ DAYS, HOURS, TimeUnit }

sealed case class ReminderNotice(hoursBeforeReminder: Int, progressStatuses: ProgressStatus) {

  private val Phase_1 = "PHASE1"
  private val Phase_2 = "PHASE2"
  private val Phase_3 = "PHASE3"

  private def timeUnitAndPhase: (TimeUnit, String) = progressStatuses match {
    case PHASE1_TESTS_SECOND_REMINDER => (HOURS, Phase_1)
    case PHASE1_TESTS_FIRST_REMINDER => (DAYS, Phase_1)
    case PHASE2_TESTS_SECOND_REMINDER => (HOURS, Phase_2)
    case PHASE2_TESTS_FIRST_REMINDER => (DAYS, Phase_2)
    case PHASE3_TESTS_SECOND_REMINDER => (HOURS, Phase_3)
    case PHASE3_TESTS_FIRST_REMINDER => (DAYS, Phase_3)
    case status => throw UnrecognisedReminderNoticeProgressStatusException(s"$status is an unrecognised progress status " +
      s"for reminder notices")
  }

  val (timeUnit, phase) = timeUnitAndPhase
}

object Phase1FirstReminder extends ReminderNotice(72, PHASE1_TESTS_FIRST_REMINDER)
object Phase1SecondReminder extends ReminderNotice(24, PHASE1_TESTS_SECOND_REMINDER)
object Phase2FirstReminder extends ReminderNotice(72, PHASE2_TESTS_FIRST_REMINDER)
object Phase2SecondReminder extends ReminderNotice(24, PHASE2_TESTS_SECOND_REMINDER)
object Phase3FirstReminder extends ReminderNotice(72, PHASE3_TESTS_FIRST_REMINDER)
object Phase3SecondReminder extends ReminderNotice(24, PHASE3_TESTS_SECOND_REMINDER)

object ReminderNotice {
  case class UnrecognisedReminderNoticeProgressStatusException(msg: String) extends Exception(msg)
}
