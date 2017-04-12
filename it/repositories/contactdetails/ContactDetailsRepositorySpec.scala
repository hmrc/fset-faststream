package repositories.contactdetails

import model.Address
import model.Exceptions.{ ContactDetailsNotFound, ContactDetailsNotFoundForEmail }
import model.persisted.{ ContactDetails, UserIdWithEmail }
import model.persisted.ContactDetailsExamples._
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import repositories.CollectionNames
import testkit.MongoRepositorySpec

import scala.concurrent.Await

class ContactDetailsRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._

  override val collectionName = CollectionNames.CONTACT_DETAILS

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
        _ <- insert(BSONDocument("userId" -> UserId, "contact-details" -> ContactDetailsUK))
        userId <- repository.findUserIdByEmail(ContactDetailsUK.email)
      } yield userId).futureValue

      result mustBe UserId
    }
  }

  "find all" should {
    "return empty list for empty contact details" in {
      repository.findAll.futureValue mustBe empty
    }

    "return list of contact details" in {
      insert("1", ContactDetails(outsideUk = false, Address("line1a"), Some("123"), Some("UK"), "email1@email.com", "12345"))
      insert("2", ContactDetails(outsideUk = false, Address("line1b"), Some("456"), Some("UK"), "email2@email.com", "67890"))

      val result = repository.findAll.futureValue
      result.size mustBe 2
    }

    "return only the first 10 documents if there is more than 10" in {
      for (i <- 1 to 11) {
        insert(i.toString, ContactDetails(outsideUk = false, Address(s"line$i"), Some(s"123$i"), Some("UK"), s"email$i@email.com", s"12345$i"))
      }

      val result = repository.findAll.futureValue
      result.size mustBe 10
    }
  }

  "find all PostCode" should {
    "return an empty map if no record is present" in {
      val result = repository.findAllPostcodes().futureValue
      result mustBe Map.empty
    }

    "return an empty map if the present records have no post code" in {
      val result = (for {
        _ <- insert(BSONDocument("userId" -> UserId, "contact-details" -> ContactDetailsOutsideUK))
        res <- repository.findAllPostcodes()
      } yield res).futureValue

      result.isEmpty mustBe true
    }

    "return the postcode for a given user Id if present" in {
      val result: Map[String, String] = (for {
        _ <- insert(BSONDocument("userId" -> UserId, "contact-details" -> ContactDetailsUK))
        res <- repository.findAllPostcodes()
      } yield res).futureValue

      result.get(UserId) mustBe Some("A1 B23")
    }
  }

  "Archive" should {
    "archive the existing contact details" in {
      insert(BSONDocument("userId" -> UserId, "contact-details" -> ContactDetailsUK)).futureValue

      val userIdToArchiveWith = "newUserId"

      repository.archive(UserId, userIdToArchiveWith).futureValue

      an[ContactDetailsNotFound] must be thrownBy Await.result(repository.find(UserId), timeout)

      repository.find(userIdToArchiveWith).futureValue mustBe ContactDetailsUK
    }
  }

  "find emails" should {
    "return an empty list if there is no candidates" in {
      val result = repository.findEmails.futureValue
      result mustBe Nil
    }

    "return list of users with emails" in {
      insert(BSONDocument("userId" -> "1", "contact-details" -> ContactDetailsUK)).futureValue
      insert(BSONDocument("userId" -> "2", "contact-details" -> ContactDetailsOutsideUK)).futureValue
      val result = repository.findEmails.futureValue

      result mustBe List(
        UserIdWithEmail("1", ContactDetailsUK.email),
        UserIdWithEmail("2", ContactDetailsOutsideUK.email)
      )
    }

  }

  def insert(doc: BSONDocument) = repository.collection.insert(doc)

  def insert(userId: String, cd: ContactDetails) = repository.update(userId, cd).futureValue

}
