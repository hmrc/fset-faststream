package repositories.allocation

import model.AllocationStatuses
import model.AllocationStatuses._
import model.exchange.candidateevents.CandidateRemoveReason
import model.persisted.CandidateAllocation
import org.joda.time.LocalDate
import reactivemongo.api.indexes.IndexType.Ascending
import repositories.{ CandidateAllocationMongoRepository, CollectionNames }
import testkit.MongoRepositorySpec

class CandidateAllocationRepositorySpec extends MongoRepositorySpec {

  override val collectionName: String = CollectionNames.CANDIDATE_ALLOCATION
  def repository: CandidateAllocationMongoRepository = new CandidateAllocationMongoRepository(mongo)
  val allocations: Seq[CandidateAllocation] = Seq(
    CandidateAllocation("candId1", "eventId1", "sessionId1", UNCONFIRMED, "version1", None, LocalDate.now(), reminderSent = false),
    CandidateAllocation("candId2", "eventId1", "sessionId1", CONFIRMED, "version1", None, LocalDate.now(), reminderSent = false),
    CandidateAllocation("candId3", "eventId2", "sessionId2",  UNCONFIRMED, "version1", None, LocalDate.now().minusDays(6), reminderSent = false)
  )

  def storeAllocations = allocations.foreach(a => repository.save(Seq(a)).futureValue)

  "CandidateAllocationRepository" must {
    "create indexes for the repository" in {
      val indexes = indexesWithFields2(repository)
      indexes must contain(IndexDetails(key = Seq(("_id", Ascending)), unique = false))
      indexes must contain(IndexDetails(key = Seq(("id", Ascending), ("eventId", Ascending), ("sessionId", Ascending)), unique = false))
      indexes.size mustBe 2
    }

    "correctly find allocations by session" in {
      storeAllocations
      val result = repository.activeAllocationsForSession("eventId1", "sessionId1").futureValue
      result.size mustBe 2
      val expectedAllocations = allocations.filter(_.sessionId == "sessionId1")
      result mustBe expectedAllocations
    }

    "correctly find allocations by application" in {
      storeAllocations
      val result = repository.allocationsForApplication("candId1").futureValue
      result.size mustBe 1
      val expectedAllocations = allocations.filter(_.id == "candId1")
      result mustBe expectedAllocations
    }

    "return an empty list for sessions that don`t exist" in {
      val result = repository.activeAllocationsForSession("eventId1", "invalid_session_id").futureValue
      result.isEmpty mustBe true
    }

    "remove candidate allocations" in {
      storeAllocations
      val app = allocations.head
      val result = repository.removeCandidateAllocation(app).futureValue
      result mustBe unit

      val docs2 = repository.activeAllocationsForSession("eventId1", "sessionId1").futureValue
      docs2.size mustBe 1
    }

    "check allocation exists" in {
      storeAllocations
      val app = allocations.head
      val res = repository.isAllocationExists(app.id, app.eventId, app.sessionId, Some(app.version)).futureValue
      res mustBe true
      repository.removeCandidateAllocation(app).futureValue
      val res2 = repository.isAllocationExists(app.id, app.eventId, app.sessionId, Some(app.version)).futureValue
      res2 mustBe false
    }

    "remove candidate allocations and find it in removal list" in {
      storeAllocations
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

    "return an exception when no documents have been deleted" in {
      val result = repository.delete(allocations.head :: Nil).failed.futureValue
      result mustBe a[model.Exceptions.NotFoundException]
    }

    "delete documents" in {
      storeAllocations
      val result = repository.delete(allocations.head :: Nil).futureValue
      result mustBe unit
      val docs = repository.activeAllocationsForSession("eventId1", "sessionId1").futureValue
      docs.size mustBe 1
      docs.head mustBe allocations.tail.head
    }

    "find candidates to notify and mark as reminder sent" in {
      storeAllocations
      repository.findAllUnconfirmedAllocated(5).futureValue.size mustBe 1
      val res = repository.findAllUnconfirmedAllocated(0).futureValue
      res.size mustBe 2
      val first = res.head
      repository.markAsReminderSent(first.id, first.eventId, first.sessionId).futureValue
      repository.findAllUnconfirmedAllocated(0).futureValue.size mustBe 1
    }
  }
}
