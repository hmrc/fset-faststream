package repositories.contactdetails

import model.Exceptions.{ ContactDetailsNotFoundForEmail, ContactDetailsNotFound }
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
      result mustBe ContactDetailsNotFound("IdWhichDoesNotExistForSure")
    }
  }

  "find user id by email" should {
    "return an exception when user does not exist for given email" in {
      val result = repository.findUserIdByEmail("EmailWhichDoesNotExist").failed.futureValue
      result mustBe ContactDetailsNotFoundForEmail()
    }

    "return the user id when a user does exist with the given email" in {
      val result = (for {
        _ <- insert(BSONDocument("userId" -> UserId, collectionName -> ContactDetailsUK))
        userId <- repository.findUserIdByEmail(ContactDetailsUK.email)
      } yield userId).futureValue

      result mustBe UserId
    }
  }

  "find all PostCode" should {
    "return an empty map if no record is present" in {
      val result = repository.findAllPostCode.futureValue
      result mustBe Map.empty
    }

    "return an empty map if the present records have no post code" in {
      val result = (for {
        _ <- insert(BSONDocument("userId" -> UserId, collectionName -> ContactDetailsOutsideUK))
        res <- repository.findAllPostCode
      } yield res).futureValue

      result.isEmpty mustBe true
    }

    "return the postcode for a given user Id if present" in {
      val result: Map[String, String] = (for {
        _ <- insert(BSONDocument("userId" -> UserId, collectionName -> ContactDetailsUK))
        res <- repository.findAllPostCode
      } yield res).futureValue

      result.get(UserId) mustBe Some("A1 B23")
    }
  }

  def insert(doc: BSONDocument) = repository.collection.insert(doc)
}
