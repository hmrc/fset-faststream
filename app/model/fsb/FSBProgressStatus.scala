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

package model.fsb

import model.SchemeId

object FSBProgressStatus {
  def awaitingAllocation(scheme: SchemeId) = s"FSB_AWAITING_ALLOCATION_${scheme.value.toUpperCase}"
  def allocationConfirmed(scheme: SchemeId) = s"FSB_ALLOCATION_CONFIRMED_${scheme.value.toUpperCase}"
  def allocationUnconfirmed(scheme: SchemeId) = s"FSB_ALLOCATION_UNCONFIRMED_${scheme.value.toUpperCase}"
  def failedToAttend(scheme: SchemeId) = s"FSB_FAILED_TO_ATTEND_${scheme.value.toUpperCase}"
  def resultEntered(scheme: SchemeId) = s"FSB_RESULT_ENTERED_${scheme.value.toUpperCase}"
  def passed(scheme: SchemeId) = s"FSB_PASSED_${scheme.value.toUpperCase}"
  def failed(scheme: SchemeId) = s"FSB_FAILED_${scheme.value.toUpperCase}"
  def failedNotified(scheme: SchemeId) = s"FSB_FAILED_NOTIFIED_${scheme.value.toUpperCase}"
}
