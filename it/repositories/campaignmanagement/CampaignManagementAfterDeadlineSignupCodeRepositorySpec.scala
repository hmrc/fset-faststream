package repositories.campaignmanagement

import model.persisted.CampaignManagementAfterDeadlineCode

import java.time.{OffsetDateTime, ZoneId}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import repositories.CollectionNames
import testkit.MongoRepositorySpec
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.temporal.ChronoUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

class CampaignManagementAfterDeadlineSignupCodeRepositorySpec extends MongoRepositorySpec {

  override val collectionName: String = CollectionNames.CAMPAIGN_MANAGEMENT_AFTER_DEADLINE_CODE

  def repository = new CampaignManagementAfterDeadlineSignupCodeMongoRepository(mongo)
  val campaignManagementCollection: MongoCollection[Document] = mongo.database.getCollection(collectionName)

  // Just here so we can read back the deadline code
  @Singleton
  class CampaignManagementRepositoryForTest @Inject()(mongo: MongoComponent)
    extends PlayMongoRepository[CampaignManagementAfterDeadlineCode](
      collectionName = CollectionNames.CAMPAIGN_MANAGEMENT_AFTER_DEADLINE_CODE,
      mongoComponent = mongo,
      domainFormat = CampaignManagementAfterDeadlineCode.campaignManagementAfterDeadlineCodeFormat,
      indexes = Nil
    ) {
    def find(code: String): Future[Option[CampaignManagementAfterDeadlineCode]] = {
      collection.find(Document.empty).headOption()
    }
  }

  val campaignManagementTestRepo = new CampaignManagementRepositoryForTest(mongo)

  val now = OffsetDateTime.now(ZoneId.of("UTC")).truncatedTo(ChronoUnit.MILLIS)

  def campaignManagementAfterDeadlineCode(expires: OffsetDateTime, usedByApplicationId: Option[String]) =
    CampaignManagementAfterDeadlineCode(
      code = "1234",
      createdByUserId = "userId1",
      expires = expires,
      usedByApplicationId = usedByApplicationId
    )

  "the repository" should {
    "create indexes" in {
      val indexes = indexDetails(repository).futureValue

      indexes must contain theSameElementsAs
        Seq(
          IndexDetails(name = "_id_", keys = Seq(("_id", "Ascending")), unique = false),
          IndexDetails(name = "code_1", keys = Seq(("code", "Ascending")), unique = true),
          IndexDetails(name = "expires_-1", keys = Seq(("expires", "Descending")), unique = false)
        )
    }
  }

  "save" should {
    "write a new code to the database" in {
      val newCode = campaignManagementAfterDeadlineCode(
        expires = now.plusDays(2),
        usedByApplicationId = None
      )

      val result = (for {
        _ <- repository.save(newCode)
        code <- campaignManagementTestRepo.find("1234")
      } yield code).futureValue.head

      result mustBe newCode
    }
  }

  "mark signup code as used" should {
    "mark a signup code as used" in {
      val newCode = campaignManagementAfterDeadlineCode(
        expires = now.plusDays(2),
        usedByApplicationId = None
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
      val newCode = campaignManagementAfterDeadlineCode(
        expires = now.plusDays(2),
        usedByApplicationId = None
      )

      val result = (for {
        _ <- repository.save(newCode)
        codeUnusedAndValid <- repository.findUnusedValidCode(newCode.code)
      } yield codeUnusedAndValid).futureValue

      result mustBe defined
    }

    "return None if code is expired and unused" in {
      val newCode = campaignManagementAfterDeadlineCode(
        expires = now.minusDays(2),
        usedByApplicationId = None
      )

      val result = (for {
        _ <- repository.save(newCode)
        codeUnusedAndValid <- repository.findUnusedValidCode(newCode.code)
      } yield codeUnusedAndValid).futureValue

      result mustBe empty
    }

    "return None if code is unexpired but used" in {
      val newCode = campaignManagementAfterDeadlineCode(
        expires = now.plusDays(2),
        usedByApplicationId = Some("appId1")
      )

      val result = (for {
        _ <- repository.save(newCode)
        codeUnusedAndValid <- repository.findUnusedValidCode(newCode.code)
      } yield codeUnusedAndValid).futureValue

      result mustBe empty
    }
  }
}
