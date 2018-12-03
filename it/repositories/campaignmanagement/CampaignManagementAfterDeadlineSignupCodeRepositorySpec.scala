package repositories.campaignmanagement

import model.persisted.CampaignManagementAfterDeadlineCode
import org.joda.time.DateTime
import reactivemongo.bson.BSONDocument
import repositories.CollectionNames
import testkit.MongoRepositorySpec
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories._

class CampaignManagementAfterDeadlineSignupCodeRepositorySpec extends MongoRepositorySpec {

  override val collectionName: String = CollectionNames.CAMPAIGN_MANAGEMENT_AFTER_DEADLINE_CODE

  def repository = new CampaignManagementAfterDeadlineSignupCodeMongoRepository

  "save" should {
    "write a new code to the database" in {
      val newCode = CampaignManagementAfterDeadlineCode(
        "1234",
        "userId1",
        DateTime.now.plusDays(2),
        None
      )

      val result = (for {
        _ <- repository.save(newCode)
        code <- repository.collection.find(BSONDocument("code" -> "1234")).one[CampaignManagementAfterDeadlineCode]
      } yield code).futureValue.head

      result mustBe newCode
    }
  }

  "mark signup code as used" should {
    "mark a signup code as used" in {
      val expiryTime = DateTime.now.plusDays(2)

      val newCode = CampaignManagementAfterDeadlineCode(
        "1234",
        "userId1",
        expiryTime,
        None
      )

      val (codeUnusedAndValid, codeStillUnusedAndValid) = (for {
        _ <- repository.save(newCode)
        codeUnusedAndValid <- repository.findUnusedValidCode(newCode.code)
        _ <- repository.markSignupCodeAsUsed(newCode.code, "appId1")
        codeStillUnusedAndValid <- repository.findUnusedValidCode(newCode.code)
      } yield (codeUnusedAndValid, codeStillUnusedAndValid)).futureValue

      codeUnusedAndValid mustBe defined
      codeStillUnusedAndValid mustBe empty
    }
  }

  "find UnusedValidCode" should {
    "return Some if code is unexpired and unused" in {
      val expiryTime = DateTime.now.plusDays(2)

      val newCode = CampaignManagementAfterDeadlineCode(
        "1234",
        "userId1",
        expiryTime,
        None
      )

      val result = (for {
        _ <- repository.save(newCode)
        codeUnusedAndValid <- repository.findUnusedValidCode(newCode.code)
      } yield codeUnusedAndValid).futureValue

      result mustBe defined
    }

    "return None if code is expired and unused" in {
      val expiryTime = DateTime.now.minusDays(2)

      val newCode = CampaignManagementAfterDeadlineCode(
        "1234",
        "userId1",
        expiryTime,
        None
      )

      val result = (for {
        _ <- repository.save(newCode)
        codeUnusedAndValid <- repository.findUnusedValidCode(newCode.code)
      } yield codeUnusedAndValid).futureValue

      result mustBe empty
    }

    "return None if code is unexpired but used" in {
      val expiryTime = DateTime.now.plusDays(2)

      val newCode = CampaignManagementAfterDeadlineCode(
        "1234",
        "userId1",
        expiryTime,
        Some("appId1")
      )

      val result = (for {
        _ <- repository.save(newCode)
        codeUnusedAndValid <- repository.findUnusedValidCode(newCode.code)
      } yield codeUnusedAndValid).futureValue

      result mustBe empty
    }
  }

}
