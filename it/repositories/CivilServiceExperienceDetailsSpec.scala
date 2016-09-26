package repositories

import model.ApplicationStatus._
import model.Exceptions.{ CannotUpdateCivilServiceExperienceDetails, CivilServiceExperienceDetailsNotFound }
import model.CivilServiceExperienceDetailsExamples._
import reactivemongo.bson._
import reactivemongo.json._
import testkit.MongoRepositorySpec

class CivilServiceExperienceDetailsSpec extends MongoRepositorySpec {

  import ImplicitBSONHandlers._

  override val collectionName: String = "application"

  def repository = new CivilServiceExperienceDetailsMongoRepository

  "update and find" should {
    "modify and find the fast pass details successfully" in {
      val civilServiceExperienceDetails = (for {
        _ <- insert(BSONDocument("applicationId" -> AppId, "userId" -> UserId, "applicationStatus" -> CREATED))
        _ <- repository.update(AppId, civilServant)
        fpDetails <- repository.find(AppId)
      } yield fpDetails).futureValue
      civilServiceExperienceDetails mustBe civilServant
    }

    "return exception when fast pass details does not exist" in {
      val exception = (for {
        _ <- repository.update(AppId, civilServant)
        fpDetails <- repository.find(AppId)
      } yield fpDetails).failed.futureValue
      exception mustBe CannotUpdateCivilServiceExperienceDetails(AppId)
    }
  }

  "find" should {
    "return exception when fast pass details not found" in {
      val exception = (for {
        _ <- insert(BSONDocument("applicationId" -> AppId, "userId" -> UserId, "applicationStatus" -> CREATED))
        _ <- repository.find(AppId)
      } yield ()).failed.futureValue

      exception mustBe CivilServiceExperienceDetailsNotFound(AppId)
    }
  }

  def insert(doc: BSONDocument) = repository.collection.insert(doc)
}
