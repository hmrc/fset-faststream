package repositories.schemepreferences

import model.ApplicationStatus._
import model.Exceptions.{CannotUpdateSchemePreferences, SchemePreferencesNotFound}
import model.SelectedSchemesExamples._
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import testkit.MongoRepositorySpec

class SchemePreferencesRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._

  val collectionName: String = "application"

  def repository = new SchemePreferencesMongoRepository

  "save and find" should {
    "save and return scheme preferences" in {
      val persistedSchemes = (for {
        _ <- insert(BSONDocument("applicationId" -> AppId, "userId" -> UserId, "applicationStatus" -> CREATED))
        _ <- repository.save(AppId, TwoSchemes)
        schemes <- repository.find(AppId)
      } yield schemes).futureValue

      persistedSchemes mustBe TwoSchemes
    }

    "return an exception when application does not exist" in {
      val exception = (for {
        _ <- repository.save(AppId, TwoSchemes)
        schemes <- repository.find(AppId)
      } yield schemes).failed.futureValue

      exception mustBe CannotUpdateSchemePreferences(AppId)
    }
  }

  "find" should {
    "return an exception when scheme-preferences does not exist" in {
      val exception = (for {
        _ <- insert(BSONDocument("applicationId" -> AppId, "userId" -> UserId, "applicationStatus" -> CREATED))
        _ <- repository.find(AppId)
      } yield ()).failed.futureValue

      exception mustBe SchemePreferencesNotFound(AppId)
    }
  }

  def insert(doc: BSONDocument) = repository.collection.insert(doc)
}
