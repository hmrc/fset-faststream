package repositories

import model.AllocationStatuses
import model.persisted.eventschedules.SkillType
import model.persisted.{ Allocation, AssessorAllocation }
import testkit.MongoRepositorySpec

class AssessorAllocationRepositorySpec extends MongoRepositorySpec with AllocationRepositorySpec[AssessorAllocation] {
  val repository = new AssessorAllocationMongoRepository()
  val repoName = "Blah"//repository.getClass.getSimpleName

  val allocations: Seq[AssessorAllocation] = Seq(
    AssessorAllocation("assessorId1", "eventId1", AllocationStatuses.UNCONFIRMED, SkillType.ASSESSOR, "version1"),
    AssessorAllocation("assessorId2", "eventId1", AllocationStatuses.CONFIRMED, SkillType.ASSESSOR, "version1"),
    AssessorAllocation("assessorId3", "eventId1", AllocationStatuses.UNCONFIRMED, SkillType.ASSESSOR, "version1")
  )
}

trait AllocationRepositorySpec[T <: Allocation] { this: MongoRepositorySpec =>

  val collectionName = CollectionNames.ALLOCATION
  def repository: AllocationRepository[T]
  def repoName: String
  def allocations: Seq[T]

  s"$repoName" must {
    "correctly save and retrieve documents" in {
      val saveResult = repository.save(allocations).futureValue
      saveResult mustBe unit

      allocations.foreach { a =>
        val findResult = repository.find(a.id).futureValue
        findResult mustBe defined
        findResult.get mustBe a
      }
    }
  }
}
