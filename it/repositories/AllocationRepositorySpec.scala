package repositories

import model.AllocationStatuses
import model.persisted.eventschedules.SkillType
import model.persisted.{ Allocation, AssessorAllocation, CandidateAllocation }
import org.scalatest.BeforeAndAfterEach
import testkit.MongoRepositorySpec

class AssessorAllocationRepositorySpec extends MongoRepositorySpec with AllocationRepositorySpec[AssessorAllocation] {

  override val collectionName: String = CollectionNames.ASSESSOR_ALLOCATION
  def repository = new AssessorAllocationMongoRepository()
  val repoName = "AssessorAllocationRepository"

  val allocations: Seq[AssessorAllocation] = Seq(
    AssessorAllocation("assessorId1", "eventId1", AllocationStatuses.UNCONFIRMED, SkillType.ASSESSOR, "version1"),
    AssessorAllocation("assessorId2", "eventId1", AllocationStatuses.CONFIRMED, SkillType.ASSESSOR, "version1"),
    AssessorAllocation("assessorId3", "eventId1", AllocationStatuses.UNCONFIRMED, SkillType.ASSESSOR, "version1")
  )

  s"$repoName" must {
    "create indexes for the repository" in {
      val indexes = indexesWithFields(repositories.assessorAllocationRepository)
      indexes must contain(List("_id"))
      indexes must contain(List("id", "eventId"))
      indexes.size mustBe 2
    }
  }
}

class CandidateAllocationRepositorySpec extends MongoRepositorySpec with AllocationRepositorySpec[CandidateAllocation] {

  override val collectionName: String = CollectionNames.CANDIDATE_ALLOCATION
  def repository: AllocationRepository[CandidateAllocation] = new CandidateAllocationMongoRepository()
  val repoName = "CandidateAllocationRepository"
  val allocations: Seq[CandidateAllocation] = Seq(
    CandidateAllocation("candId1", "eventId1", "sessionId1", AllocationStatuses.UNCONFIRMED, "version1"),
    CandidateAllocation("candId2", "eventId1", "sessionId2", AllocationStatuses.CONFIRMED, "version1"),
    CandidateAllocation("candId3", "eventId1", "sessionId1",  AllocationStatuses.UNCONFIRMED, "version1")
  )

  s"$repoName" must {
    "create indexes for the repository" in {
      val indexes = indexesWithFields(repositories.candidateAllocationRepository)
      indexes must contain(List("_id"))
      indexes must contain(List("id", "eventId", "sessionId"))
      indexes.size mustBe 2
    }

    "correctly find allocations by session" in {
      repository.save(allocations).futureValue
      val result = repository.allocationsForSession("eventId1", "sessionId1").futureValue
      result.size mustBe 2
      val expectedAllocations = allocations.filter(_.sessionId == "sessionId1")
      result mustBe expectedAllocations
    }
  }
}

trait AllocationRepositorySpec[T <: Allocation] { this: MongoRepositorySpec =>

  val collectionName: String
  def repository: AllocationRepository[T]
  def repoName: String
  def allocations: Seq[T]

  s"$repoName" must {
    "correctly retrieve documents" in {
      repository.save(allocations).futureValue
      allocations.foreach { allocation =>
        val findResult = repository.find(allocation.userQueryValue).futureValue
        findResult mustBe allocation :: Nil
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
  }
}
