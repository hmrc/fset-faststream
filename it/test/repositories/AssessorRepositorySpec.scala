/*
 * Copyright 2024 HM Revenue & Customs
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

package repositories

import factories.UUIDFactory
import model.persisted.EventExamples
import model.persisted.assessor.{Assessor, AssessorAvailability, AssessorStatus}
import model.persisted.eventschedules.{Location, SkillType}
import model.{Schemes, UniqueIdentifier}
import testkit.MongoRepositorySpec

import java.time.LocalDate

class AssessorRepositorySpec extends MongoRepositorySpec with Schemes {

  override val collectionName: String = CollectionNames.ASSESSOR

  def repository = new AssessorMongoRepository(mongo)

  private val userId = UniqueIdentifier.randomUniqueIdentifier.toString
  private val AssessorWithAvailabilities = Assessor(userId, version = None,
    skills = List(SkillType.ASSESSOR.toString, SkillType.QUALITY_ASSURANCE_COORDINATOR.toString),
    sifterSchemes = List(Sdip), civilServant = true,
    availability = Set(
      AssessorAvailability(EventExamples.LocationLondon, LocalDate.of(2017, 9, 11)),
      AssessorAvailability(EventExamples.LocationNewcastle, LocalDate.of(2017, 9, 12))
    ),
    status = AssessorStatus.AVAILABILITIES_SUBMITTED
  )

  "Assessor repository" should {
    "create indexes for the repository" in {
      val indexes = indexDetails(repository).futureValue
      indexes must contain theSameElementsAs
        Seq(
          IndexDetails(name = "_id_", keys = Seq(("_id", "Ascending")), unique = false),
          IndexDetails(name = "userId_1", keys = Seq(("userId", "Ascending")), unique = true)
        )
    }

    "save and find the assessor" in {
      repository.save(AssessorWithAvailabilities).futureValue

      val result = repository.find(userId).futureValue
      result.get mustBe AssessorWithAvailabilities
    }

    "save and find all assessors" in {
      val secondAssessor = AssessorWithAvailabilities.copy(userId = "456")
      List(AssessorWithAvailabilities, secondAssessor).foreach { assessor =>
        repository.save(assessor).futureValue
      }

      val result = repository.findAll.futureValue
      result must contain(AssessorWithAvailabilities)
      result must contain(secondAssessor)
    }

    "save and find assessors by ids" in {
      val secondAssessor = AssessorWithAvailabilities.copy(userId = "456")
      val thirdAssessor = AssessorWithAvailabilities.copy(userId = "789")
      List(
        AssessorWithAvailabilities,
        secondAssessor,
        thirdAssessor
      ).foreach { assessor =>
        repository.save(assessor).futureValue
      }
      val ids = AssessorWithAvailabilities.userId :: secondAssessor.userId :: Nil
      val result = repository.findByIds(ids).futureValue
      result.size mustBe 2
    }

    "save assessor and add availabilities" in {
      repository.save(AssessorWithAvailabilities).futureValue

      val result = repository.find(userId).futureValue
      result mustBe Some(AssessorWithAvailabilities)

      val updated = AssessorWithAvailabilities.copy(
        availability = Set(
          AssessorAvailability(EventExamples.LocationLondon, LocalDate.of(2017, 9, 11)),
          AssessorAvailability(EventExamples.LocationLondon, LocalDate.of(2017, 10, 11)),
          AssessorAvailability(EventExamples.LocationNewcastle, LocalDate.of(2017, 9, 12)))
      )
      repository.save(updated).futureValue

      val updatedResult = repository.find(userId).futureValue
      updatedResult.get mustBe updated
    }

    "count submitted availabilities" in {
      val availability = AssessorWithAvailabilities
      val availability2 = availability.copy(userId = "user2")

      repository.save(availability).futureValue
      repository.save(availability2).futureValue

      val result = repository.countSubmittedAvailability.futureValue
      result mustBe 2
    }

    "find assessors without availabilities given date and location" in {
      val london = Location("London")
      val newcastle = Location("Newcastle")
      val skills = List(SkillType.EXERCISE_MARKER)

      val availabilities = Set(
        AssessorAvailability(london, LocalDate.of(2017, 8, 10)),
        AssessorAvailability(london, LocalDate.of(2017, 8, 11)),
        AssessorAvailability(newcastle, LocalDate.of(2017, 9, 10)),
        AssessorAvailability(newcastle, LocalDate.of(2017, 10, 11))
      )

      def assessor = Assessor(UUIDFactory.generateUUID(), None, skills.map(_.toString), Nil, civilServant = true,
        Set.empty, AssessorStatus.CREATED)

      val assessorsWithAvailabilities = Seq(
        assessor.copy(skills = List(SkillType.ASSESSOR.toString)),
        assessor.copy(skills = List(SkillType.ASSESSOR.toString, SkillType.CHAIR.toString)),
        assessor.copy(status = AssessorStatus.AVAILABILITIES_SUBMITTED, availability = availabilities),
        assessor.copy(skills = Nil),
        assessor.copy(skills = List(SkillType.DEPARTMENTAL_ASSESSOR.toString, SkillType.EXERCISE_MARKER.toString))
      )

      assessorsWithAvailabilities.foreach { assessor =>
        repository.save(assessor).futureValue mustBe unit
      }

      val eventDate = LocalDate.of(2017, 8, 10)
      val eventSkills = List(SkillType.ASSESSOR, SkillType.QUALITY_ASSURANCE_COORDINATOR)
      val result = repository.findUnavailableAssessors(eventSkills, london, eventDate).futureValue
      result.size mustBe 2
    }

    "save and remove assessor" in {
      repository.save(AssessorWithAvailabilities).futureValue
      repository.remove(UniqueIdentifier(userId)).futureValue
      val result = repository.find(userId).futureValue
      result mustBe None
    }

    "find availabilities for location and date" in {
      repository.save(AssessorWithAvailabilities).futureValue
      val date = LocalDate.of(2017, 9, 11)
      val result = repository.findAvailabilitiesForLocationAndDate(Location("London"), date, Seq(SkillType.ASSESSOR)).futureValue
      result mustBe Seq(AssessorWithAvailabilities)
    }
  }
}
