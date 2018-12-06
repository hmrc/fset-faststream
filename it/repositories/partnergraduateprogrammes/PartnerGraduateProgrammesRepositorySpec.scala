package repositories.partnergraduateprogrammes

import model.Exceptions.PartnerGraduateProgrammesNotFound
import model.persisted.PartnerGraduateProgrammesExamples
import reactivemongo.bson.{ BSONArray, BSONDocument }
import reactivemongo.play.json.ImplicitBSONHandlers
import repositories.CollectionNames
import testkit.MongoRepositorySpec

class PartnerGraduateProgrammesRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._

  override val collectionName = CollectionNames.APPLICATION

  def repository = new PartnerGraduateProgrammesMongoRepository

  "update" should {

    "create new partner graduate programmes if they do not exist" in {
      val result = (for {
        _ <- insert(minimumApplicationBSON(applicationId(5), userId(5)))
        _ <- repository.update(applicationId(5), PartnerGraduateProgrammesExamples.InterestedNotAll)
        pgp <- repository.find(applicationId(5))
      } yield pgp).futureValue

      result mustBe PartnerGraduateProgrammesExamples.InterestedNotAll
    }

    "update partner graduate programmes when they exist and find them successfully" in {
      val result = (for {
        _ <- insert(applicationBSONWithFullPartnerGraduateProgrammes(applicationId(7), userId(7)))
        _ <- repository.update(applicationId(7), PartnerGraduateProgrammesExamples.InterestedNotAll )
        pgp <- repository.find(applicationId(7))
      } yield pgp).futureValue

      result mustBe PartnerGraduateProgrammesExamples.InterestedNotAll
    }
  }

  "find" should {
    "return an exception when user does not exist" in {
      val result = repository.find(applicationId(6)).failed.futureValue
      result mustBe PartnerGraduateProgrammesNotFound(applicationId(6))
    }
  }

  private def insert(doc: BSONDocument) = repository.collection.insert(doc)

  private def userId(i: Int) = "UserId" + i
  private def applicationId(i: Int) = "AppId" + i

  private def minimumApplicationBSON(applicationId: String, userId: String) = BSONDocument(
    "applicationId" -> applicationId,
    "userId" -> userId,
    "frameworkId" -> FrameworkId
  )

  private def applicationBSONWithFullPartnerGraduateProgrammes(applicationId: String, userId: String) = BSONDocument(
    "applicationId" -> applicationId,
    "userId" -> userId,
    "frameworkId" -> FrameworkId,
    "partner-graduate-programmes" -> BSONDocument(
      "interested" -> true,
      "partnerGraduateProgrammes" -> BSONArray(
        "Entrepreneur First", "Frontline", "Lead First", "Police Now", "TeachFirst", "TeachFirst", "Year Here"
      )
    )
  )
}
