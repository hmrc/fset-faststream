package repositories.schemepreferences

import model.ApplicationStatus._
import model.Exceptions.{CannotUpdateSchemePreferences, SchemePreferencesNotFound}
import model.SelectedSchemesExamples._
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import repositories.application.{GeneralApplicationMongoRepository, GeneralApplicationRepoBSONToModelHelper}
import services.GBTimeZoneService
import config.MicroserviceAppConfig._
import testkit.MongoRepositorySpec

class SchemePreferencesRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._

  val collectionName: String = "application"

  def repository = new SchemePreferencesMongoRepository
  def applicationRepository = new GeneralApplicationMongoRepository(GBTimeZoneService, cubiksGatewayConfig,
    GeneralApplicationRepoBSONToModelHelper)

  "save and find" should {
    "save and return scheme preferences" in {
      val (persistedSchemes, application) = (for {
        _ <- insert(BSONDocument("applicationId" -> AppId, "userId" -> UserId, "applicationStatus" -> CREATED, "frameworkId" -> FrameworkId))
        _ <- repository.save(AppId, TwoSchemes)
        appResponse <- applicationRepository.findByUserId(UserId, FrameworkId)
        schemes <- repository.find(AppId)
      } yield (schemes, appResponse)).futureValue

      persistedSchemes mustBe TwoSchemes
      application.progressResponse.schemePreferences mustBe true
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
