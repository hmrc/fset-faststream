package repositories.testdata

import model.ApplicationStatus.{ApplicationStatus, CREATED}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import repositories.CollectionNames
import testkit.MongoRepositorySpec
import uk.gov.hmrc.mongo.play.json.Codecs

class ApplicationRemovalRepositorySpec extends MongoRepositorySpec {

  override val collectionName = CollectionNames.APPLICATION

  def repository = new ApplicationRemovalMongoRepository(mongo)
  val applicationCollection: MongoCollection[Document] = mongo.database.getCollection(collectionName)
  def insert(doc: Document) = applicationCollection.insertOne(doc).toFuture()
  def findByAppStatus(applicationStatus: ApplicationStatus) =
    applicationCollection.find(Document("applicationStatus" -> Codecs.toBson(applicationStatus))).toFuture()

  "Application removal repository" should {
    "remove the expected applications" in {
      val insertedDocs = (for {
        _ <- insert(Document("applicationId" -> s"$AppId-1", "userId" -> s"$UserId-1", "applicationStatus" -> CREATED.toBson))
        _ <- insert(Document("applicationId" -> s"$AppId-2", "userId" -> s"$UserId-2", "applicationStatus" -> CREATED.toBson))
        insertedDocs <- findByAppStatus(CREATED)
      } yield insertedDocs).futureValue

      insertedDocs.size mustBe 2

      val deletedUserIds = repository.remove(Some(CREATED)).futureValue
      deletedUserIds mustBe List(s"$UserId-1", s"$UserId-2")

      val docsAfterDeletion = findByAppStatus(CREATED).futureValue
      docsAfterDeletion.size mustBe 0
    }
  }
}
