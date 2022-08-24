package repositories

import model.ApplicationStatus._
import model.CivilServiceExperienceDetailsExamples._
import model.Exceptions.CannotUpdateCivilServiceExperienceDetails
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import repositories.civilserviceexperiencedetails.CivilServiceExperienceDetailsMongoRepository
import testkit.MongoRepositorySpec

class CivilServiceExperienceDetailsSpec extends MongoRepositorySpec {

  override val collectionName: String = CollectionNames.APPLICATION

  def repository = new CivilServiceExperienceDetailsMongoRepository(mongo)
  val applicationCollection: MongoCollection[Document] = mongo.database.getCollection(collectionName)
  def insert(doc: Document) = applicationCollection.insertOne(doc).toFuture()

  "update and find" should {
    "modify and find the civil service experience details successfully" in {
      val civilServiceExperienceDetails = (for {
        _ <- insert(Document("applicationId" -> AppId, "userId" -> UserId, "applicationStatus" -> CREATED.toBson))
        _ <- repository.update(AppId, civilServant)
        fpDetails <- repository.find(AppId)
      } yield fpDetails).futureValue
      civilServiceExperienceDetails mustBe Some(civilServant)
    }

    "return exception when civil service experience details do not exist" in {
      val exception = (for {
        _ <- repository.update(AppId, civilServant)
        fpDetails <- repository.find(AppId)
      } yield fpDetails).failed.futureValue
      exception mustBe CannotUpdateCivilServiceExperienceDetails(AppId)
    }
  }

  "find" should {
    "return None when civil service experience details not found" in {
      val civilServiceDetails = (for {
        _ <- insert(Document("applicationId" -> AppId, "userId" -> UserId, "applicationStatus" -> CREATED.toBson))
        civilServiceDetails <- repository.find(AppId)
      } yield civilServiceDetails).futureValue

      civilServiceDetails mustBe None
    }
  }

  "evaluate fast pass candidate" should {
    "return exception when application does not exist" in {
      val exception = repository.evaluateFastPassCandidate(AppId, accepted = true).failed.futureValue
      exception mustBe CannotUpdateCivilServiceExperienceDetails(AppId)
    }

    "return exception when application exists but civil service details do not exist" in {
      val exception = (for {
        _ <- insert(Document("applicationId" -> AppId, "userId" -> UserId, "applicationStatus" -> CREATED.toBson))
        _ <- repository.evaluateFastPassCandidate(AppId, accepted = true)
      } yield ()).failed.futureValue
      exception mustBe CannotUpdateCivilServiceExperienceDetails(AppId)
    }

    "update fast pass accepted to the expected value" in {
      val civilServiceExperienceDetails = (for {
        _ <- insert(Document("applicationId" -> AppId, "userId" -> UserId, "applicationStatus" -> CREATED.toBson))
        _ <- repository.update(AppId, civilServant)
        _ <- repository.evaluateFastPassCandidate(AppId, accepted = false)
        fpDetails <- repository.find(AppId)
      } yield fpDetails).futureValue
      civilServiceExperienceDetails.map { cc =>
        cc.fastPassAccepted mustBe Some(false)
      }
    }
  }
}
