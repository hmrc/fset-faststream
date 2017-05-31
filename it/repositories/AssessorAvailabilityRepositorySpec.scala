package repositories

import model.persisted.AssessorAvailability
import org.joda.time.LocalDate
import testkit.MongoRepositorySpec

class AssessorAvailabilityRepositorySpec extends MongoRepositorySpec {

  override val collectionName = CollectionNames.ASSESSOR_AVAILABILITY

  def repository = new AssessorAvailabilityMongoRepository()

  "Assessor availability repository" should {
    "create indexes for the repository" in {
      val repo = repositories.assessorAvailabilityRepository

      val indexes = indexesWithFields(repo)
      indexes must contain (List("_id"))
      indexes must contain (List("userId"))
      indexes.size mustBe 2
    }

    "create and fetch the assessor availability" in {
      val userId = "123"
      val availability = AssessorAvailability(userId, Map(
        "london" -> List(new LocalDate(2017, 9, 1), new LocalDate(2017, 9, 2)),
        "newcastle" -> List(new LocalDate(2017, 9, 10), new LocalDate(2017, 9, 11)))
      )
      repository.save(availability).futureValue

      val result = repository.find(userId).futureValue
      result.get mustBe availability
    }

    "update the assessor availability" in {
      val userId = "123"
      val availability = AssessorAvailability(userId,
        Map("london" -> List(new LocalDate(2017, 9, 11)), "newcastle" -> List(new LocalDate(2017, 9, 12))))
      repository.save(availability).futureValue

      val result = repository.find(userId).futureValue
      result.get mustBe availability

      val updated = AssessorAvailability(userId,
        Map("london" -> List(new LocalDate(2017, 9, 11)), "newcastle" -> List(new LocalDate(2017, 9, 12))))
      repository.save(updated).futureValue

      val updatedResult = repository.find(userId).futureValue
      updatedResult.get mustBe updated
    }

    "count submitted availabilities" in {
      val availability = AssessorAvailability("user1",
        Map("london" -> List(new LocalDate(2017, 9, 11)), "newcastle" -> List(new LocalDate(2017, 9, 12))))
      val availability2 = availability.copy(userId = "user2")

      repository.save(availability).futureValue
      repository.save(availability2).futureValue

      val result = repository.countSubmitted.futureValue

      result mustBe 2
    }
  }
}
