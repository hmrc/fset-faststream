package repositories.schemepreferences

import factories.ITDateTimeFactoryMock
import model.ApplicationStatus._
import model.Exceptions.{ CannotUpdateSchemePreferences, SchemePreferencesNotFound }
import model.SchemeId
import model.SelectedSchemesExamples._
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.ImplicitBSONHandlers
import repositories.CollectionNames
import repositories.application.GeneralApplicationMongoRepository
import testkit.MongoRepositorySpec

class SchemePreferencesRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._

  val collectionName: String = CollectionNames.APPLICATION

  def repository = new SchemePreferencesMongoRepository(mongo)
  def applicationRepository = new GeneralApplicationMongoRepository(ITDateTimeFactoryMock, appConfig, mongo)

  "save and find" should {
    "save and return scheme preferences" in {
      val (persistedSchemes, application) = (for {
        _ <- insert(BSONDocument("applicationId" -> AppId, "userId" -> UserId, "testAccountId" -> TestAccountId,
          "applicationStatus" -> CREATED, "frameworkId" -> FrameworkId))
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

  "add scheme" should {
    "add a new scheme to the application" in {
      val actualSchemes = createTwoSchemes()
      repository.add(AppId, SchemeId("Sdip")).futureValue

      val schemes = repository.find(AppId).futureValue
      schemes.schemes.size mustBe (actualSchemes.size + 1)
      schemes.schemes must contain theSameElementsAs (SchemeId("Sdip") :: actualSchemes)
    }

    "do not add duplications" in {
      val actualSchemes = createTwoSchemes()
      repository.add(AppId, SchemeId("Sdip")).futureValue
      repository.add(AppId, SchemeId("Sdip")).futureValue

      val schemes = repository.find(AppId).futureValue
      schemes.schemes.size mustBe (actualSchemes.size + 1)
      schemes.schemes must contain theSameElementsAs (SchemeId("Sdip") :: actualSchemes)
    }
  }

  private def createTwoSchemes(): List[SchemeId] = {
    insert(BSONDocument("applicationId" -> AppId, "userId" -> UserId,
      "applicationStatus" -> CREATED, "frameworkId" -> FrameworkId)).futureValue
    repository.save(AppId, TwoSchemes).futureValue
    TwoSchemes.schemes
  }

  private def insert(doc: BSONDocument) = repository.collection.insert(ordered = false).one(doc)
}
