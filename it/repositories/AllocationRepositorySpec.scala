package repositories

import model.AllocationStatuses
import model.persisted.eventschedules.SkillType
import model.persisted.{ Allocation, AssessorAllocation }
import org.scalatest.BeforeAndAfterEach
import testkit.MongoRepositorySpec

class AssessorAllocationRepositorySpec extends MongoRepositorySpec with AllocationRepositorySpec[AssessorAllocation] {

  def repository = new AssessorAllocationMongoRepository()
  val repoName = "AssessorAllocationRepository"

  val allocations: Seq[AssessorAllocation] = Seq(
    AssessorAllocation("assessorId1", "eventId1", AllocationStatuses.UNCONFIRMED, SkillType.ASSESSOR, "version1"),
    AssessorAllocation("assessorId2", "eventId1", AllocationStatuses.CONFIRMED, SkillType.ASSESSOR, "version1"),
    AssessorAllocation("assessorId3", "eventId1", AllocationStatuses.UNCONFIRMED, SkillType.ASSESSOR, "version1")
  )
}

trait AllocationRepositorySpec[T <: Allocation] { this: MongoRepositorySpec =>

  val collectionName = CollectionNames.ASSESSOR_ALLOCATION
  def repository: AllocationRepository[T]
  def repoName: String
  def allocations: Seq[T]

  s"$repoName" must {
    "create indexes for the repository" in {
      val indexes = indexesWithFields(repositories.assessorAllocationRepository)
      indexes must contain(List("_id"))
      indexes must contain(List("id", "eventId"))
      indexes.size mustBe 2
    }
    "correctly retrieve documents" in {
      repository.save(allocations).futureValue
      allocations.foreach { a =>
        val findResult = repository.find(a.id).futureValue
        findResult mustBe defined
        findResult.get mustBe a
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
