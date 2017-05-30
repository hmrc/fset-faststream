/*
 * Copyright 2017 HM Revenue & Customs
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

package model.command

import org.joda.time.{ LocalDate, LocalTime }

case class AssessmentCentreAllocation(applicationId: String, venue: String, date: LocalDate, session: String, slot: Int, confirmed: Boolean) {

  // If a candidate is allocated at DD/MM/YYYY, the deadline for the candidate to confirm is 10 days.
  // Because we don't store the time it means we need to set DD-11/MM/YYY, and remember that there is
  // an implicit time 23:59:59 after which the allocation expires.
  // After DD-11/MM/YYYY the allocation is expired.
  // For Example:
  // - The candidate is scheduled on 25/05/2016.
  // - It means the deadline is 14/05/2016 23:59:59
  def expireDate: LocalDate = date.minusDays(11)
}
