package repositories.fileupload

import model.Exceptions.EventNotFoundException
import model.persisted.EventExamples
import model.persisted.eventschedules.{ EventType, SkillType, VenueType }
import play.api.libs.iteratee.{ Enumerator, Iteratee }
import repositories.CollectionNames
import testkit.MongoRepositorySpec

class FileUploadRepositorySpec extends MongoRepositorySpec {

  override val collectionName: String = CollectionNames.FILE_UPLOAD
  lazy val repository = repositories.fileUploadRepository

  private def storeAndRetrieveFile = {
    val testContent = "Test contents".toCharArray.map(_.toByte)
    val testContentType = "application/pdf"
    val id = repository.add(testContentType, testContent).futureValue
    val retrievedFile = repository.retrieve(id).futureValue
    retrievedFile.contentType mustBe testContentType
    retrievedFile.fileContents.run(Iteratee.consume[Array[Byte]]()).futureValue mustBe testContent
  }

  "add" should {
    "store a file with contentType" in {
      storeAndRetrieveFile
    }
  }

  "retrieve" should {
    "retrieve a file by id" in {
      storeAndRetrieveFile
    }
  }
}
