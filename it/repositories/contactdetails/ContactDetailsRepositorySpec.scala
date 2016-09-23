package repositories.contactdetails

import model.Exceptions.ContactDetailsNotFound
import model.persisted.ContactDetailsExamples._
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import testkit.MongoRepositorySpec

class ContactDetailsRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._

  override val collectionName = "contact-details"

  def repository = new ContactDetailsMongoRepository

  "update contact details" should {
    "update contact details and find them successfully" in {
      val UpdatedContactDetails = ContactDetailsUK.copy(email = "newemail@test.com", phone = "111333444")
      val result = (for {
        _ <- insert(BSONDocument(collectionName -> ContactDetailsUK))
        _ <- repository.update(UserId, UpdatedContactDetails)
        cd <- repository.find(UserId)
      } yield cd).futureValue

      result mustBe UpdatedContactDetails
    }

    "create new contact details if they does not exist" in {
      val result = (for {
        _ <- repository.update(UserId, ContactDetailsUK)
        cd <- repository.find(UserId)
      } yield cd).futureValue

      result mustBe ContactDetailsUK

    }
  }

  "find contact details" should {
    "return an exception when user does not exist" in {
      val result = repository.find("IdWhichDoesNotExistForSure").failed.futureValue
      result mustBe ContactDetailsNotFound(UserId)
    }
  }

  def insert(doc: BSONDocument) = repository.collection.insert(doc)
}
