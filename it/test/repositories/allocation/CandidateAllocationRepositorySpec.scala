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

package repositories.allocation

import model.AllocationStatuses
import model.AllocationStatuses._
import model.Exceptions.NotFoundException
import model.exchange.candidateevents.CandidateRemoveReason
import model.persisted.CandidateAllocation
import repositories.{CandidateAllocationMongoRepository, CollectionNames}
import testkit.MongoRepositorySpec

import java.time.LocalDate

class CandidateAllocationRepositorySpec extends MongoRepositorySpec {

  override val collectionName: String = CollectionNames.CANDIDATE_ALLOCATION
  def repository: CandidateAllocationMongoRepository = new CandidateAllocationMongoRepository(mongo)
  val appId1 = "appId1"
  val appId2 = "appId2"
  val appId3 = "appId3"
  val allocations: Seq[CandidateAllocation] = Seq(
    CandidateAllocation(appId1, "eventId1", "sessionId1", UNCONFIRMED, "version1", removeReason = None, LocalDate.now(), reminderSent = false),
    CandidateAllocation(appId2, "eventId1", "sessionId1", CONFIRMED, "version1", removeReason = None, LocalDate.now(), reminderSent = false),
    CandidateAllocation(appId3, "eventId2", "sessionId2", UNCONFIRMED, "version1", removeReason = None,
      LocalDate.now().minusDays(6), reminderSent = false
    )
  )

  def storeAllocations() = allocations.foreach(a => repository.save(Seq(a)).futureValue)

  "CandidateAllocationRepository" must {
    "create indexes for the repository" in {
      val indexes = indexDetails(repository).futureValue
      indexes must contain theSameElementsAs
        Seq(
          IndexDetails(name = "_id_", keys = Seq(("_id", "Ascending")), unique = false),
          IndexDetails(name = "id_1_eventId_1_sessionId_1",
            keys = Seq(("id", "Ascending"), ("eventId", "Ascending"), ("sessionId", "Ascending")), unique = false)
        )
    }

    "correctly find allocations by applicationId" in {
      storeAllocations()
      val result = repository.find(appId1).futureValue
      result.size mustBe 1
      val expectedAllocations = allocations.filter(_.id == appId1)
      result mustBe expectedAllocations
    }

    "correctly find allocations by session" in {
      storeAllocations()
      val result = repository.activeAllocationsForSession("eventId1", "sessionId1").futureValue
      result.size mustBe 2
      val expectedAllocations = allocations.filter(_.sessionId == "sessionId1")
      result mustBe expectedAllocations
    }

    "correctly find allocations by application" in {
      storeAllocations()
      val result = repository.allocationsForApplication(appId1).futureValue
      result.size mustBe 1
      val expectedAllocations = allocations.filter(_.id == appId1)
      result mustBe expectedAllocations
    }

    "return an empty list for sessions that don`t exist" in {
      val result = repository.activeAllocationsForSession("eventId1", "invalid_session_id").futureValue
      result.isEmpty mustBe true
    }

    "remove candidate allocations" in {
      storeAllocations()
      val app = allocations.head
      val result = repository.removeCandidateAllocation(app).futureValue
      result mustBe unit

      val docs2 = repository.activeAllocationsForSession("eventId1", "sessionId1").futureValue
      docs2.size mustBe 1
    }

    "check allocation exists" in {
      storeAllocations()
      val app = allocations.head
      val res = repository.isAllocationExists(app.id, app.eventId, app.sessionId, Some(app.version)).futureValue
      res mustBe true
      repository.removeCandidateAllocation(app).futureValue
      val res2 = repository.isAllocationExists(app.id, app.eventId, app.sessionId, Some(app.version)).futureValue
      res2 mustBe false
    }

    "remove candidate allocations and find it in removal list" in {
      storeAllocations()
      val app = allocations.head.copy(removeReason = Some(CandidateRemoveReason.NoShow))
      val result = repository.removeCandidateAllocation(app).futureValue
      result mustBe unit

      val markedAsRemoved = repository.findAllAllocations(Seq(app.id)).futureValue
      markedAsRemoved must contain(app.copy(status = AllocationStatuses.REMOVED))

      val docs1 = repository.activeAllocationsForEvent("eventId1").futureValue
      docs1.size mustBe 1

      val docs2 = repository.activeAllocationsForSession("eventId1", "sessionId1").futureValue
      docs2.size mustBe 1
    }

    "remove candidate removal reason" in {
      storeAllocations()
      val app = allocations.head.copy(removeReason = Some(CandidateRemoveReason.NoShow))
      repository.removeCandidateAllocation(app).futureValue
      val result1 = repository.find(appId1).futureValue
      result1.size mustBe 1
      repository.removeCandidateRemovalReason(appId1).futureValue
      val result2 = repository.find(appId1).futureValue
      result2 mustBe Seq.empty
    }

    "return an exception when no documents have been deleted" in {
      val result = repository.delete(allocations.head :: Nil).failed.futureValue
      result mustBe a[model.Exceptions.NotFoundException]
    }

    "delete documents" in {
      storeAllocations()
      val result = repository.delete(allocations.head :: Nil).futureValue
      result mustBe unit
      val docs = repository.activeAllocationsForSession("eventId1", "sessionId1").futureValue
      docs.size mustBe 1
      docs.head mustBe allocations.tail.head
    }

    "find candidates to notify and mark as reminder sent" in {
      storeAllocations()
      repository.findAllUnconfirmedAllocated(5).futureValue.size mustBe 1
      val res = repository.findAllUnconfirmedAllocated(0).futureValue
      res.size mustBe 2
      val first = res.head
      repository.markAsReminderSent(first.id, first.eventId, first.sessionId).futureValue
      repository.findAllUnconfirmedAllocated(0).futureValue.size mustBe 1
    }

    "find candidates with confirmed or unconfirmed allocations" in {
      storeAllocations()
      repository.findAllConfirmedOrUnconfirmedAllocations(Seq(appId1, appId2, appId3), Seq("eventId1", "eventId2")).futureValue.size mustBe 3
      repository.findAllConfirmedOrUnconfirmedAllocations(Seq(appId1, appId2), Seq("eventId1")).futureValue.size mustBe 2
      repository.findAllConfirmedOrUnconfirmedAllocations(Seq(appId3), Seq("eventId2")).futureValue.size mustBe 1
    }

    "handle saving an empty list of allocations" in {
      repository.save(Nil).futureValue mustBe unit
    }

    "handle deleting an empty list of allocations" in {
      repository.delete(Nil).futureValue mustBe unit
    }

    "updateStructure" should {
      "update the expected fields" in {
        storeAllocations()
        repository.updateStructure() // Sets reminderSent to true and updates the createdAt timestamp
        val resultAfterUpdate = repository.findAllAllocations(Seq(appId1, appId2, appId3)).futureValue
        resultAfterUpdate.foreach { candidateAllocation =>
          candidateAllocation.reminderSent mustBe true
          candidateAllocation.createdAt mustBe LocalDate.now()
        }
      }
    }

    "allAllocationUnconfirmed" should {
      "find all documents with an unconfirmed status" in {
        storeAllocations()
        val result = repository.allAllocationUnconfirmed.futureValue
        result.forall(_.status == AllocationStatuses.UNCONFIRMED) mustBe true
      }
    }

    "deleting allocations" should {
      "delete a single allocation for the given data" in {
        storeAllocations()
        repository.deleteOneAllocation("eventId2", "sessionId2", appId3, "version1").futureValue
        val result1 = repository.findAllAllocations(Seq(appId1, appId2, appId3)).futureValue
        result1.size mustBe 2

        repository.deleteOneAllocation("eventId1", "sessionId1", appId2, "version1", CONFIRMED).futureValue
        val result2 = repository.findAllAllocations(Seq(appId1, appId2, appId3)).futureValue
        result2.size mustBe 1
      }

      "throw a NotFoundException if the document cannot be found" in {
        storeAllocations()
        val result1 = repository.deleteOneAllocation("eventId1", "sessionId1", appId1, "version1Missing").failed.futureValue
        result1 mustBe a[NotFoundException]
        val result2 = repository.deleteOneAllocation("eventId1", "sessionId1", appId1, "version1", REMOVED).failed.futureValue
        result2 mustBe a[NotFoundException]
      }
    }
  }
}
