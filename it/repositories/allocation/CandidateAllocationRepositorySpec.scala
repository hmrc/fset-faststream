package repositories.allocation

import model.AllocationStatuses
import model.persisted.CandidateAllocation
import repositories.{ CandidateAllocationMongoRepository, CollectionNames }
import testkit.MongoRepositorySpec

class CandidateAllocationRepositorySpec extends MongoRepositorySpec {

  override val collectionName: String = CollectionNames.CANDIDATE_ALLOCATION
  def repository: CandidateAllocationMongoRepository = new CandidateAllocationMongoRepository()
  val allocations: Seq[CandidateAllocation] = Seq(
    CandidateAllocation("candId1", "eventId1", "sessionId1", AllocationStatuses.UNCONFIRMED, "version1", None),
    CandidateAllocation("candId2", "eventId1", "sessionId1", AllocationStatuses.CONFIRMED, "version1", None),
    CandidateAllocation("candId3", "eventId2", "sessionId2",  AllocationStatuses.UNCONFIRMED, "version1", None)
  )

  def storeAllocations = allocations.foreach(a => repository.save(Seq(a)).futureValue)

  s"CandidateAllocationRepository" must {
    "create indexes for the repository" in {
      val indexes = indexesWithFields(repositories.candidateAllocationRepository)
      indexes must contain(List("_id"))
      indexes must contain(List("id", "eventId", "sessionId"))
      indexes.size mustBe 2
    }

    "correctly find allocations by session" in {
      storeAllocations
      val result = repository.allocationsForSession("eventId1", "sessionId1").futureValue
      result.size mustBe 2
      val expectedAllocations = allocations.filter(_.sessionId == "sessionId1")
      result mustBe expectedAllocations
    }

    "return an empty list for sessions that don`t exist" in {
      val result = repository.allocationsForSession("eventId1", "invalid_session_id").futureValue
      result.isEmpty mustBe true
    }

    "remove candidate allocations" in {
      storeAllocations
      val app = allocations.head
      val result = repository.removeCandidateAllocation(app).futureValue
      result mustBe unit

      val docs2 = repository.allocationsForSession("eventId1", "sessionId1").futureValue
      docs2.size mustBe 1
    }

    "remove candidate allocations and find it in removal list" in {
      storeAllocations
      val app = allocations.head
      val result = repository.removeCandidateAllocation(app).futureValue
      result mustBe unit

      val markedAsRemoved = repository.findCandidateRemovals(Seq(app.id)).futureValue
      markedAsRemoved mustBe Seq(app.copy(status = AllocationStatuses.REMOVED))

      val docs1 = repository.allocationsForEvent("eventId1").futureValue
      docs1.size mustBe 1

      val docs2 = repository.allocationsForSession("eventId1", "sessionId1").futureValue
      docs2.size mustBe 1
    }

    "return an exception when no documents have been deleted" in {
      val result = repository.delete(allocations.head :: Nil).failed.futureValue
      result mustBe a[model.Exceptions.NotFoundException]
    }

    "delete documents" in {
      storeAllocations
      val result = repository.delete(allocations.head :: Nil).futureValue
      result mustBe unit
      val docs = repository.allocationsForSession("eventId1", "sessionId1").futureValue
      docs.size mustBe 1
      docs.head mustBe allocations.tail.head
    }
  }
}
