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

package model.sift

import model.ProgressStatuses._
import model.sift.SiftReminderNotice.UnrecognisedSiftReminderNoticeProgressStatusException

import scala.concurrent.duration.{ DAYS, HOURS, TimeUnit }

sealed case class SiftReminderNotice(hoursBeforeReminder: Int, progressStatus: ProgressStatus) {

  private def timeUnitAndPhase: (TimeUnit, ProgressStatus) = progressStatus match {
    case SIFT_FIRST_REMINDER => (DAYS, SIFT_FIRST_REMINDER)
    case SIFT_SECOND_REMINDER => (HOURS, SIFT_SECOND_REMINDER)
    case status => throw UnrecognisedSiftReminderNoticeProgressStatusException(
      s"$status is an unrecognised progress status for sift reminder notices")
  }

  val (timeUnit, phase) = timeUnitAndPhase
}

object SiftFirstReminder extends SiftReminderNotice(72, SIFT_FIRST_REMINDER)
object SiftSecondReminder extends SiftReminderNotice(24, SIFT_SECOND_REMINDER)

object SiftReminderNotice {
  case class UnrecognisedSiftReminderNoticeProgressStatusException(msg: String) extends Exception(msg)
}
