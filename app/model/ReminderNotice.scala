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

package model

import model.ProgressStatuses.{ PHASE1_TESTS_FIRST_REMINDER, PHASE1_TESTS_SECOND_REMINDER, ProgressStatus }

final case class ReminderNotice(hoursBeforeReminder: Int, progressStatuses: ProgressStatus) {
  require(hoursBeforeReminder > 0)
  require(progressStatuses == PHASE1_TESTS_FIRST_REMINDER || progressStatuses == PHASE1_TESTS_SECOND_REMINDER)

  def timeUnit: String = progressStatuses match {
    case PHASE1_TESTS_SECOND_REMINDER => "hours"
    case PHASE1_TESTS_FIRST_REMINDER => "days"
  }
}
