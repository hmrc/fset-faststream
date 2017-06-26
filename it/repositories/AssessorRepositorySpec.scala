package repositories

import model.persisted.eventschedules.Location
import model.persisted.{Assessor, AssessorAvailability}
import org.joda.time.LocalDate
import testkit.MongoRepositorySpec

class AssessorRepositorySpec extends MongoRepositorySpec {

  override val collectionName = CollectionNames.ASSESSOR

  def repository = new AssessorMongoRepository()

  "Assessor repository" should {
    "create indexes for the repository" in {
      val repo = repositories.assessorRepository

      val indexes = indexesWithFields(repo)
      indexes must contain(List("_id"))
      indexes must contain(List("userId"))
      indexes.size mustBe 2
    }

    "save and find the assessor" in {
      val userId = "123"
      val assessor = Assessor(userId, List("assessor", "qac"), civilServant = true,
        AssessorAvailability(Location("london"), new LocalDate(2017, 9, 1)) ::
        AssessorAvailability(Location("london"), new LocalDate(2017, 9, 2)) ::
        AssessorAvailability(Location("newcastle"), new LocalDate(2017, 9, 10)) ::
        AssessorAvailability(Location("newcastle"), new LocalDate(2017, 9, 11)) :: Nil
      )
      repository.save(assessor).futureValue

      val result = repository.find(userId).futureValue
      result.get mustBe assessor
    }

    "save assessor" in {
      val userId = "123"
      val assessor = Assessor(userId, List("assessor", "qac"), civilServant = true,
        AssessorAvailability(Location("london"), new LocalDate(2017, 9, 11)) ::
        AssessorAvailability(Location("newcastle"), new LocalDate(2017, 9, 12)) :: Nil
      )
      repository.save(assessor).futureValue

      val result = repository.find(userId).futureValue
      result.get mustBe assessor

      val updated = Assessor(userId, List("assessor", "qac", "chair"), civilServant = true,
        AssessorAvailability(Location("london"), new LocalDate(2017, 10, 11)) ::
          AssessorAvailability(Location("newcastle"), new LocalDate(2017, 10, 12)) :: Nil
      )
      repository.save(updated).futureValue

      val updatedResult = repository.find(userId).futureValue
      updatedResult.get mustBe updated
    }

    "count submitted availabilities" ignore {
      val availability = Assessor("user1", List("assessor", "qac"), civilServant = true,
        AssessorAvailability(Location("london"), new LocalDate(2017, 9, 12)) :: Nil
      )
      val availability2 = availability.copy(userId = "user2")

      repository.save(availability).futureValue
      repository.save(availability2).futureValue

      val result = repository.countSubmittedAvailability.futureValue

      // TODO fix this when the availability save is done
      result mustBe 0
    }
  }
}
