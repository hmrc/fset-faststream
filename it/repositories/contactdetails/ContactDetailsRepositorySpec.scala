package repositories.contactdetails

import model.Address
import model.Exceptions.{ContactDetailsNotFound, ContactDetailsNotFoundForEmail}
import model.persisted.ContactDetailsExamples._
import model.persisted.{ContactDetails, UserIdWithEmail}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import repositories.CollectionNames
import testkit.MongoRepositorySpec
import uk.gov.hmrc.mongo.play.json.Codecs

import scala.concurrent.Await

class ContactDetailsRepositorySpec extends MongoRepositorySpec {

  override val collectionName = CollectionNames.CONTACT_DETAILS

  def repository = new ContactDetailsMongoRepository(mongo, appConfig)
  val contactDetailsCollection: MongoCollection[Document] = mongo.database.getCollection(collectionName)
  def insert(doc: Document) = contactDetailsCollection.insertOne(doc).toFuture()
  def insert(userId: String, cd: ContactDetails) = repository.update(userId, cd).futureValue

  "Contact details repository" should {
    "create the expected indexes" in {
      val indexes = indexDetails(repository).futureValue
      indexes must contain theSameElementsAs
        Seq(
          IndexDetails(name = "_id_", keys = Seq(("_id", "Ascending")), unique = false),
          IndexDetails(name = "userId_1", keys = Seq(("userId", "Ascending")), unique = true)
        )
    }
  }

  "update contact details" should {
    "update contact details and find them successfully" in {
      val UpdatedContactDetails = ContactDetailsUK.copy(email = "newemail@test.com", phone = "111333444")

      val result = (for {
        _ <- insert(Document("userId" -> "UserId", "contact-details" -> Codecs.toBson(ContactDetailsUK)))
        _ <- repository.update(UserId, UpdatedContactDetails)
        cd <- repository.find(UserId)
      } yield cd).futureValue

      result mustBe UpdatedContactDetails
    }

    "create new contact details if they do not exist" in {
      val result = (for {
        // Will perform an upsert (inserts a new doc) when no matching doc is found to update
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

    // This searches on a sub-doc element and returns a top level data item
    "return the user id when a user does exist with the given email" in {
      val result = (for {
        _ <- insert(Document("userId" -> UserId, "contact-details" -> Codecs.toBson(ContactDetailsUK)))
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
      insert("1", ContactDetails(outsideUk = false, Address("line1a"), postCode = Some("123"), Some("UK"), "email1@email.com", phone = "12345"))
      insert("2", ContactDetails(outsideUk = false, Address("line1b"), postCode = Some("456"), Some("UK"), "email2@email.com", phone = "67890"))

      val result = repository.findAll.futureValue
      result.size mustBe 2
    }

    "return only the first 10 documents if there are more than 10" in {
      for (i <- 1 to 11) {
        insert(
          i.toString, ContactDetails(
            outsideUk = false, Address(s"line$i"), postCode = Some(s"123$i"), Some("UK"), s"email$i@email.com", phone = s"12345$i"
          )
        )
      }

      val result = repository.findAll.futureValue
      result.size mustBe 10
    }
  }

  "find all postcodes" should {
    "return an empty map if no record is present" in {
      val result = repository.findAllPostcodes().futureValue
      result mustBe Map.empty
    }

    "return an empty map if the present records have no post code" in {
      val result = (for {
        _ <- insert(Document("userId" -> UserId, "contact-details" -> Codecs.toBson(ContactDetailsOutsideUK)))
        res <- repository.findAllPostcodes()
      } yield res).futureValue

      result.isEmpty mustBe true
    }

    "return the postcode for a given user Id if present" in {
      val result: Map[String, String] = (for {
        _ <- insert(Document("userId" -> UserId, "contact-details" -> Codecs.toBson(ContactDetailsUK)))
        res <- repository.findAllPostcodes()
      } yield res).futureValue

      result.get(UserId) mustBe Some("A1 B23")
    }
  }

  "find by postcode" should {
    "find contact details when the postcode matches" in {
      val result = (for {
        _ <- insert(Document("userId" -> UserId, "contact-details" -> Codecs.toBson(ContactDetailsUK)))
        cd <- repository.findByPostCode("A1 B23")
      } yield cd).futureValue

      result.head.userId mustBe UserId
    }
    "find no contact details when the postcode does not match" in {
      val result = (for {
        _ <- insert(Document("userId" -> UserId, "contact-details" -> Codecs.toBson(ContactDetailsUK)))
        cd <- repository.findByPostCode("BOOM")
      } yield cd).futureValue

      result.isEmpty mustBe true
    }
  }

  "find by userIds" should {
    "find contact details when the userIds match" in {
      val result = (for {
        _ <- insert(Document("userId" -> UserId, "contact-details" -> Codecs.toBson(ContactDetailsUK)))
        cd <- repository.findByUserIds(List(UserId))
      } yield cd).futureValue

      result.size mustBe 1
      result.head.userId mustBe UserId

      result.head.address mustBe ContactDetailsUK.address
      result.head.postCode mustBe ContactDetailsUK.postCode
      result.head.outsideUk mustBe ContactDetailsUK.outsideUk
      result.head.email mustBe ContactDetailsUK.email
      result.head.phone mustBe Some(ContactDetailsUK.phone)
    }

    "find no contact details when none of the userIds match" in {
      val result = (for {
        _ <- insert(Document("userId" -> "UserId", "contact-details" -> Codecs.toBson(ContactDetailsUK)))
        cd <- repository.findByUserIds(List("BOOM"))
      } yield cd).futureValue

      result.isEmpty mustBe true
    }
  }

  "archive" should {
    "archive the existing contact details" in {
      insert(Document("userId" -> UserId, "contact-details" -> Codecs.toBson(ContactDetailsUK))).futureValue

      val userIdToArchiveWith = "newUserId"
      repository.archive(UserId, userIdToArchiveWith).futureValue

      an[ContactDetailsNotFound] must be thrownBy Await.result(repository.find(UserId), timeout)

      repository.find(userIdToArchiveWith).futureValue mustBe ContactDetailsUK
    }
  }

  "find emails" should {
    "return an empty list if there are no candidates" in {
      val result = repository.findEmails.futureValue
      result mustBe Nil
    }

    "return list of users with emails" in {
      insert(Document("userId" -> "1", "contact-details" -> Codecs.toBson(ContactDetailsUK))).futureValue
      insert(Document("userId" -> "2", "contact-details" -> Codecs.toBson(ContactDetailsOutsideUK))).futureValue
      val result = repository.findEmails.futureValue

      result mustBe Seq(
        UserIdWithEmail("1", ContactDetailsUK.email),
        UserIdWithEmail("2", ContactDetailsOutsideUK.email)
      )
    }
  }

  "Remove contact details" should {
    "remove the matching contact details" in {
      val insertResult = (for {
        _ <- insert(Document("userId" -> UserId, "contact-details" -> Codecs.toBson(ContactDetailsUK)))
        userId <- repository.findUserIdByEmail(ContactDetailsUK.email)
      } yield userId).futureValue

      insertResult mustBe UserId

      repository.removeContactDetails(UserId).futureValue

      val result = repository.find(UserId).failed.futureValue
      result mustBe ContactDetailsNotFound(UserId)
    }
  }
}
