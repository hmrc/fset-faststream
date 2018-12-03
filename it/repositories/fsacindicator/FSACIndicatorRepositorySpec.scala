package repositories.fsacindicator

import model.Exceptions.{ FSACIndicatorNotFound }
import model.persisted.{ FSACIndicator }
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.ImplicitBSONHandlers
import repositories.CollectionNames
import testkit.MongoRepositorySpec

class FSACIndicatorRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._

  override val collectionName = CollectionNames.APPLICATION

  def repository = new FSACIndicatorMongoRepository()

  "update" should {

    val FSACIndicatorExample1 = FSACIndicator("London", "London", "1")
    val FSACIndicatorExample2 = FSACIndicator("Newcastle", "Newcastle", "1")

    "create new fsac indicator if they do not exist" in {
      val result = (for {
        _ <- insert(minimumApplicationBSON(applicationId(1), userId(1)))
        _ <- repository.update(applicationId(1), userId(1), FSACIndicatorExample1)
        ad <- repository.find(applicationId(1))
      } yield ad).futureValue

      result mustBe FSACIndicatorExample1
    }

    "update fsac indicator when they exist and find them successfully" in {
      val result = (for {
        _ <- insert(applicationBSONWithFullAssistanceDetails(applicationId(3), userId(3)))
        _ <- repository.update(applicationId(3), userId(3), FSACIndicatorExample2)
        ad <- repository.find(applicationId(3))
      } yield ad).futureValue

      result mustBe FSACIndicatorExample2
    }
  }

  "find" should {
    "return an exception when application does not exist" in {
      val result = repository.find(applicationId(4)).failed.futureValue
      result mustBe FSACIndicatorNotFound(applicationId(4))
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

  private def applicationBSONWithFullAssistanceDetails(applicationId: String, userId: String) = BSONDocument(
    "applicationId" -> applicationId,
    "userId" -> userId,
    "frameworkId" -> FrameworkId,
    "fsac-indicator" -> BSONDocument(
      "area" -> "London",
      "assessmentCentre" -> "London",
      "version" -> "1"
    )
  )
}
