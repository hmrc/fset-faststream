package repositories.application

import factories.{ITDateTimeFactoryMock, UUIDFactory}
import model.ApplicationRoute
import model.ApplicationRoute.ApplicationRoute
import model.EvaluationResults.{Green, Red, Withdrawn}
import model.persisted.SchemeEvaluationResult
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Projections
import repositories.{CollectionNames, SchemeRepository}
import testkit.MongoRepositorySpec

class PreviousYearCandidatesDetailsMongoRepositorySpec extends MongoRepositorySpec with UUIDFactory {

  val collectionName = CollectionNames.APPLICATION

  lazy val schemeRepository = app.injector.instanceOf(classOf[SchemeRepository])

  lazy val reportRepo = new PreviousYearCandidatesDetailsMongoRepository(ITDateTimeFactoryMock, appConfig, schemeRepository, mongo)

  val applicationCollection: MongoCollection[Document] = mongo.database.getCollection(collectionName)

  def appRepository = new GeneralApplicationMongoRepository(ITDateTimeFactoryMock, appConfig, mongo)

  "isSdipFsWithFsFailedAndSdipNotFailed" must {
    "return false if the candidate is a faststream candidate" in {
      val appId = createApplication(ApplicationRoute.Faststream)
      val evaluationResults = Seq(
        SchemeEvaluationResult("OperationalDelivery", Green.toString)
      )
      appRepository.updateCurrentSchemeStatus(appId, evaluationResults).futureValue

      val docOpt = fetchData(appId)
      docOpt mustBe defined
      reportRepo.isSdipFsWithFsFailedAndSdipNotFailed(docOpt.get) mustBe false
    }

    "return false if the candidate has green sdip and faststream schemes are green" in {
      val appId = createApplication(ApplicationRoute.SdipFaststream)
      val evaluationResults = Seq(
        SchemeEvaluationResult("OperationalDelivery", Green.toString),
        SchemeEvaluationResult("Sdip", Green.toString)
      )
      appRepository.updateCurrentSchemeStatus(appId, evaluationResults).futureValue

      val docOpt = fetchData(appId)
      docOpt mustBe defined
      reportRepo.isSdipFsWithFsFailedAndSdipNotFailed(docOpt.get) mustBe false
    }

    "return false if the candidate has green sdip and faststream schemes are green (multiple fs schemes)" in {
      val appId = createApplication(ApplicationRoute.SdipFaststream)
      val evaluationResults = Seq(
        SchemeEvaluationResult("Commercial", Green.toString),
        SchemeEvaluationResult("OperationalDelivery", Green.toString),
        SchemeEvaluationResult("Sdip", Green.toString)
      )
      appRepository.updateCurrentSchemeStatus(appId, evaluationResults).futureValue

      val docOpt = fetchData(appId)
      docOpt mustBe defined
      reportRepo.isSdipFsWithFsFailedAndSdipNotFailed(docOpt.get) mustBe false
    }

    "return true if the candidate has green sdip and faststream schemes are red" in {
      val appId = createApplication(ApplicationRoute.SdipFaststream)
      val evaluationResults = Seq(
        SchemeEvaluationResult("OperationalDelivery", Red.toString),
        SchemeEvaluationResult("Sdip", Green.toString)
      )
      appRepository.updateCurrentSchemeStatus(appId, evaluationResults).futureValue

      val docOpt = fetchData(appId)
      docOpt mustBe defined
      reportRepo.isSdipFsWithFsFailedAndSdipNotFailed(docOpt.get) mustBe true
    }

    "return true if the candidate has green sdip and faststream schemes are red (multiple fs schemes)" in {
      val appId = createApplication(ApplicationRoute.SdipFaststream)
      val evaluationResults = Seq(
        SchemeEvaluationResult("Commercial", Red.toString),
        SchemeEvaluationResult("OperationalDelivery", Red.toString),
        SchemeEvaluationResult("Sdip", Green.toString)
      )
      appRepository.updateCurrentSchemeStatus(appId, evaluationResults).futureValue

      val docOpt = fetchData(appId)
      docOpt mustBe defined
      reportRepo.isSdipFsWithFsFailedAndSdipNotFailed(docOpt.get) mustBe true
    }

    "return true if the candidate has green sdip and faststream schemes are withdrawn" in {
      val appId = createApplication(ApplicationRoute.SdipFaststream)
      val evaluationResults = Seq(
        SchemeEvaluationResult("OperationalDelivery", Withdrawn.toString),
        SchemeEvaluationResult("Sdip", Green.toString)
      )
      appRepository.updateCurrentSchemeStatus(appId, evaluationResults).futureValue

      val docOpt = fetchData(appId)
      docOpt mustBe defined
      reportRepo.isSdipFsWithFsFailedAndSdipNotFailed(docOpt.get) mustBe true
    }

    "return true if the candidate has green sdip and faststream schemes are withdrawn (multiple fs schemes)" in {
      val appId = createApplication(ApplicationRoute.SdipFaststream)
      val evaluationResults = Seq(
        SchemeEvaluationResult("Commercial", Withdrawn.toString),
        SchemeEvaluationResult("OperationalDelivery", Withdrawn.toString),
        SchemeEvaluationResult("Sdip", Green.toString)
      )
      appRepository.updateCurrentSchemeStatus(appId, evaluationResults).futureValue

      val docOpt = fetchData(appId)
      docOpt mustBe defined
      reportRepo.isSdipFsWithFsFailedAndSdipNotFailed(docOpt.get) mustBe true
    }

    "return true if the candidate has green sdip and faststream schemes are red or withdrawn" in {
      val appId = createApplication(ApplicationRoute.SdipFaststream)
      val evaluationResults = Seq(
        SchemeEvaluationResult("Commercial", Red.toString),
        SchemeEvaluationResult("OperationalDelivery", Withdrawn.toString),
        SchemeEvaluationResult("Sdip", Green.toString)
      )
      appRepository.updateCurrentSchemeStatus(appId, evaluationResults).futureValue

      val docOpt = fetchData(appId)
      docOpt mustBe defined
      reportRepo.isSdipFsWithFsFailedAndSdipNotFailed(docOpt.get) mustBe true
    }
  }

  def fetchData(appId: String) = {
    val query = Document("applicationId" -> appId)
    val projection = Projections.include("applicationRoute", "currentSchemeStatus")
    applicationCollection.find(query).projection(projection).headOption().futureValue
  }

  private def createApplication(applicationRoute: ApplicationRoute): String = {
    val applicationId = generateUUID()
    applicationCollection.insertOne(
      Document(
        "applicationId" -> applicationId,
        "userId" -> applicationId,
        "applicationRoute" -> applicationRoute.toBson
      )
    ).toFuture().futureValue
    applicationId
  }
}
