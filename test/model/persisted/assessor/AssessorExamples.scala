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

package model.persisted.assessor

import model.{Schemes, UniqueIdentifier}
import model.persisted.eventschedules.Location

import java.time.LocalDate

object AssessorExamples extends Schemes {

  val AssessorUserId = UniqueIdentifier.randomUniqueIdentifier.toString

  val london = Location("London")
  val newcastle = Location("Newcastle")

  val assessorAvailability = AssessorAvailability(london, LocalDate.of(2017, 10, 10))

  val persistedAvailabilities = Set(
    AssessorAvailability(london, LocalDate.of(2017, 10, 10)),
    AssessorAvailability(london, LocalDate.of(2017, 10, 11)),
    AssessorAvailability(newcastle, LocalDate.of(2017, 5, 10)),
    AssessorAvailability(newcastle, LocalDate.of(2017, 5, 11)))

  val AssessorExisting = Assessor(
    AssessorUserId,
    version = None,
    skills = List("qac", "chair"),
    sifterSchemes = List(Sdip),
    civilServant = true,
    status = AssessorStatus.AVAILABILITIES_SUBMITTED,
    availability = persistedAvailabilities
  )

  val AssessorNew: Assessor = AssessorExamples.AssessorExisting.copy(skills = List("assessor"),
    availability = Set.empty,
    status = AssessorStatus.CREATED)

  val AssessorMerged: Assessor = AssessorExamples.AssessorExisting.copy(skills = List("assessor"))

  val AssessorWithAvailability: Assessor = AssessorExisting.copy(
    skills = List(),
    availability = Set(AssessorAvailability(london, LocalDate.of(2017, 11, 11)))
  )

  val AssessorWithAvailabilityMerged: Assessor = AssessorWithAvailability.copy(
    skills = AssessorExisting.skills,
    availability = AssessorExisting.availability ++ AssessorWithAvailability.availability
  )

  val AssessorWithSkill = Assessor(
    AssessorUserId,
    version = None,
    skills = List("ASSESSOR"),
    sifterSchemes = Nil,
    civilServant = true,
    status = AssessorStatus.AVAILABILITIES_SUBMITTED,
    availability = persistedAvailabilities
  )

  val AssessorWith2Skill = Assessor(
    AssessorUserId,
    version = None,
    skills = List("ASSESSOR", "SIFTER"),
    sifterSchemes = List(Commercial),
    civilServant = true,
    status = AssessorStatus.AVAILABILITIES_SUBMITTED,
    availability = persistedAvailabilities
  )
}
