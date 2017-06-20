package repositories

import model.persisted.assessor.{ Assessor, AssessorStatus }
import org.joda.time.LocalDate
import testkit.MongoRepositorySpec

class AssessorRepositorySpec extends MongoRepositorySpec {

  override val collectionName = CollectionNames.ASSESSOR

  def repository = new AssessorMongoRepository()

  private val userId = "123"
  private val AssessorWithAvailabilities = Assessor(userId,
    List("assessor", "qac"), true,
    Map("london" -> List(new LocalDate(2017, 9, 11)), "newcastle" -> List(new LocalDate(2017, 9, 12))),
    AssessorStatus.AVAILABILITIES_SUBMITTED
  )


  "Assessor repository" should {
    "create indexes for the repository" in {
      val repo = repositories.assessorRepository

      val indexes = indexesWithFields(repo)
      indexes must contain(List("_id"))
      indexes must contain(List("userId"))
      indexes.size mustBe 2
    }

    "save and find the assessor" in {

      repository.save(AssessorWithAvailabilities).futureValue

      val result = repository.find(userId).futureValue
      result.get mustBe AssessorWithAvailabilities
    }

    "save assessor and add availabilities" in {
      repository.save(AssessorWithAvailabilities).futureValue

      val result = repository.find(userId).futureValue
      result.get mustBe AssessorWithAvailabilities

      val updated = AssessorWithAvailabilities.copy(
        availability = Map("london" -> List(new LocalDate(2017, 9, 11), new LocalDate(2017, 10, 11)),
          "newcastle" -> List(new LocalDate(2017, 9, 12)))
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
  }
}
