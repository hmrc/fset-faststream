package repositories.fsacindicator

import model.Exceptions.FSACIndicatorNotFound
import model.persisted.FSACIndicator
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import repositories.CollectionNames
import testkit.MongoRepositorySpec

class FSACIndicatorRepositorySpec extends MongoRepositorySpec {

  override val collectionName: String = CollectionNames.APPLICATION

  def repository = new FSACIndicatorMongoRepository(mongo)

  "update" should {
    val FSACIndicatorExample1 = FSACIndicator("London", "London", "1")
    val FSACIndicatorExample2 = FSACIndicator("Newcastle", "Newcastle", "1")

    "create new fsac indicator if they do not exist" in {
      val result = (for {
        _ <- insert(minimumApplicationDocument(applicationId(1), userId(1)))
        _ <- repository.update(applicationId(1), userId(1), FSACIndicatorExample1)
        ad <- repository.find(applicationId(1))
      } yield ad).futureValue

      result mustBe FSACIndicatorExample1
    }

    "update fsac indicator when they exist and find them successfully" in {
      val result = (for {
        _ <- insert(applicationWithFullAssistanceDetails(applicationId(3), userId(3)))
        _ <- repository.update(applicationId(3), userId(3), FSACIndicatorExample2)
        ad <- repository.find(applicationId(3))
      } yield ad).futureValue

      result mustBe FSACIndicatorExample2
    }
  }

  "find" should {
    "throw an exception when application does not exist" in {
      val result = repository.find(applicationId(4)).failed.futureValue
      result mustBe FSACIndicatorNotFound(applicationId(4))
    }
  }

  val applicationCollection: MongoCollection[Document] = mongo.database.getCollection(collectionName)
  def insert(doc: Document) = applicationCollection.insertOne(doc).toFuture()

  private def userId(i: Int) = "UserId" + i
  private def applicationId(i: Int) = "AppId" + i

  private def minimumApplicationDocument(applicationId: String, userId: String) = Document(
    "applicationId" -> applicationId,
    "userId" -> userId,
    "frameworkId" -> FrameworkId
  )

  private def applicationWithFullAssistanceDetails(applicationId: String, userId: String) = Document(
    "applicationId" -> applicationId,
    "userId" -> userId,
    "frameworkId" -> FrameworkId,
    "fsac-indicator" -> Document(
      "area" -> "London",
      "assessmentCentre" -> "London",
      "version" -> "1"
    )
  )
}
