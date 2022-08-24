package repositories.allocation

import model.AllocationStatuses
import model.persisted.AssessorAllocation
import model.persisted.eventschedules.SkillType
//import reactivemongo.api.indexes.IndexType.Ascending
import repositories.{ AssessorAllocationMongoRepository, CollectionNames }
import testkit.MongoRepositorySpec

class AssessorAllocationRepositorySpec extends MongoRepositorySpec {

  val collectionName: String = CollectionNames.ASSESSOR_ALLOCATION
  def repository = new AssessorAllocationMongoRepository(mongo)

  val allocations: Seq[AssessorAllocation] = Seq(
    AssessorAllocation("assessorId1", "eventId1", AllocationStatuses.UNCONFIRMED, SkillType.ASSESSOR, "version1"),
    AssessorAllocation("assessorId2", "eventId1", AllocationStatuses.CONFIRMED, SkillType.ASSESSOR, "version1"),
    AssessorAllocation("assessorId3", "eventId1", AllocationStatuses.UNCONFIRMED, SkillType.ASSESSOR, "version1")
  )

  "AssessorAllocationRepository" must {
    "create indexes for the repository" in {
      val indexes = indexDetails(repository).futureValue
      indexes must contain theSameElementsAs
        Seq(
          IndexDetails(name = "_id_", keys = Seq(("_id", "Ascending")), unique = false),
          IndexDetails(name = "id_1_eventId_1", keys = Seq(("id", "Ascending"), ("eventId", "Ascending")), unique = false)
        )
    }

    "correctly retrieve documents" in {
      repository.save(allocations).futureValue
      allocations.foreach { allocation =>
        val findResult = repository.find(allocation.id).futureValue
        findResult mustBe allocation :: Nil
      }
    }

    "findAll documents" in {
      repository.save(allocations).futureValue
      repository.findAll.futureValue must contain theSameElementsAs allocations
    }

    "find assessor allocations" in {
      val moreAllocations = Seq(
        AssessorAllocation("assessorId1", "eventId2", AllocationStatuses.UNCONFIRMED, SkillType.ASSESSOR, "version1")
      )

      repository.save(allocations ++ moreAllocations).futureValue
      val res = repository.findAllocations(Seq("assessorId1", "assessorId3")).futureValue
      res.size mustBe 3
    }

    "correctly retrieve single document" in {
      repository.save(allocations).futureValue
      allocations.foreach { allocation =>
        val findResult = repository.find(allocation.id, allocation.eventId).futureValue
        findResult mustBe Some(allocation)
      }
    }

    "get all the allocations for an event" in {
      repository.save(allocations).futureValue
      val result = repository.allocationsForEvent("eventId1").futureValue
      result.size mustBe 3
      result mustBe allocations
    }

    "return an empty list for events that don`t exist" in {
      val result = repository.allocationsForEvent("eventId1").futureValue
      result.isEmpty mustBe true
    }

    "delete documents" in {
      repository.save(allocations).futureValue
      val result = repository.delete(allocations.head :: Nil).futureValue
      result mustBe unit
      val docs = repository.allocationsForEvent("eventId1").futureValue
      docs mustBe allocations.tail
    }

    "return an exception when no documents have been deleted" in {
      val result = repository.delete(allocations.head :: Nil).failed.futureValue
      result mustBe a[model.Exceptions.NotFoundException]
    }

    "update allocation status" in {
      val assessorId = "assessorId1"
      val eventId = "eventId1"
      repository.save(Seq(allocations.head)).futureValue
      repository.updateAllocationStatus(assessorId, eventId, AllocationStatuses.CONFIRMED)
      repository.find(assessorId, eventId).futureValue mustBe
        Some(AssessorAllocation(assessorId, eventId, AllocationStatuses.CONFIRMED, SkillType.ASSESSOR, "version1"))
    }
  }
}
