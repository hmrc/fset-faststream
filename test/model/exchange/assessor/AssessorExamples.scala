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

package model.exchange.assessor

import model.exchange.{ Assessor, AssessorAvailability }
import model.persisted.EventExamples
import org.joda.time.LocalDate

object AssessorExamples {
  val UserId1 = "57364"
  val Assessor1 = Assessor(UserId1, List("assessor", "qac"), List("Sdip"), civilServant = true)
}

object AssessorAvailabilityExamples {
  val AssessorAvailabilityInBothLondonAndNewcastle = List(
    AssessorAvailability(EventExamples.LocationLondon.name, new LocalDate(2017, 10, 10)),
    AssessorAvailability(EventExamples.LocationLondon.name, new LocalDate(2017, 10, 10)),
    AssessorAvailability(EventExamples.LocationNewcastle.name, new LocalDate(2017, 5, 10)),
    AssessorAvailability(EventExamples.LocationNewcastle.name, new LocalDate(2017, 5, 10))
  )
}
