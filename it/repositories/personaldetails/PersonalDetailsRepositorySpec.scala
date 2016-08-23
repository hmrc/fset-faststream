package repositories.personaldetails

import model.ApplicationStatus._
import model.Exceptions.PersonalDetailsNotFound
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import testkit.MongoRepositorySpec
import model.persisted.PersonalDetailsExamples._

class PersonalDetailsRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._

  override val collectionName = "application"

  def repository = new PersonalDetailsMongoRepository

  "update candidate" should {
    "modify the details and find the personal details successfully" in {
      val personalDetails = (for {
        _ <- insert(BSONDocument("applicationId" -> AppId, "userId" -> UserId, "applicationStatus" -> CREATED))
        _ <- repository.update(AppId, UserId, JohnDoe, List(CREATED), IN_PROGRESS)
        pd <- repository.find(AppId)
      } yield pd).futureValue

      personalDetails mustBe JohnDoe
    }

    "do not update the application in different status than required" in {
      val actualException = (for {
        _ <- insert(BSONDocument("applicationId" -> AppId, "userId" -> UserId, "applicationStatus" -> SUBMITTED))
        _ <- repository.update(AppId, UserId, JohnDoe, List(CREATED), IN_PROGRESS)
        pd <- repository.find(AppId)
      } yield pd).failed.futureValue

      actualException mustBe PersonalDetailsNotFound(AppId)
    }

    "modify the details and find the personal details successfully without changing application status" in {
      val personalDetails = (for {
        _ <- insert(BSONDocument("applicationId" -> AppId, "userId" -> UserId, "applicationStatus" -> SUBMITTED))
        _ <- repository.updateWithoutStatusChange(AppId, UserId, JohnDoe)
        pd <- repository.find(AppId)
      } yield pd).futureValue

      personalDetails mustBe JohnDoe
    }
  }

  "find candidate" should {
    "throw an exception when it does not exist" in {
      val result = repository.find(AppId).failed.futureValue
      result mustBe PersonalDetailsNotFound(AppId)
    }
  }

  def insert(doc: BSONDocument) = repository.collection.insert(doc)
}
