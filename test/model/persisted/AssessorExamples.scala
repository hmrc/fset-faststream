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

package model.persisted

import model.persisted.eventschedules.Location
import org.joda.time.LocalDate

object AssessorExamples {

  val AssessorUserId = "USR1"

  val london = Location("London")
  val newcastle = Location("Newcastle")

  val assessorAvailability = AssessorAvailability(london, new LocalDate(2017, 10, 10))

  val AssessorExisting = Assessor(AssessorUserId,
    skills = List("qac", "chair"),
    civilServant = true,
    availability =
      AssessorAvailability(london, new LocalDate(2017, 10, 10)) ::
      AssessorAvailability(london, new LocalDate(2017, 11, 10)) ::
      AssessorAvailability(newcastle, new LocalDate(2017, 5, 10)) ::
      AssessorAvailability(newcastle, new LocalDate(2017, 6, 10)) ::
      Nil
    )

  val AssessorNew: Assessor = AssessorExisting.copy(skills = List("assessor"), availability = Nil)
  val AssessorMerged: Assessor = AssessorExisting.copy(skills = List("assessor"))


  val AssessorWithAvailability: Assessor = AssessorExisting.copy(
    skills = List(),
    availability = AssessorAvailability(london, new LocalDate(2017, 11, 11)) :: Nil
  )

  val AssessorWithAvailabilityMerged: Assessor = AssessorWithAvailability.copy(
    skills = AssessorExisting.skills,
    availability = AssessorExisting.availability ++ AssessorWithAvailability.availability
  )
}
