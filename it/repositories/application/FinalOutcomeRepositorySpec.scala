package repositories.application

import factories.{ ITDateTimeFactoryMock, UUIDFactory }
import model.ProgressStatuses
import model.persisted.SchemeEvaluationResult
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories.CollectionNames
import repositories.fsb.FsbMongoRepository
import testkit.MongoRepositorySpec

class FinalOutcomeRepositorySpec extends MongoRepositorySpec with UUIDFactory {

  val collectionName = CollectionNames.APPLICATION
//  lazy val repository = repositories.finalOutcomeRepository
  lazy val repository = new FinaOutcomeMongoRepository(ITDateTimeFactoryMock, mongo)
//  lazy val fsbRepo = repositories.fsbRepository
  lazy val fsbRepo = new FsbMongoRepository(ITDateTimeFactoryMock, mongo)
//  lazy val applicationRepo = repositories.applicationRepository
  lazy val applicationRepo = new GeneralApplicationMongoRepository(ITDateTimeFactoryMock, appConfig, mongo)

  "next final failed" must {
    "return only application for final fsb failed with no green schemes" in {
      val redApp = createFailedRedApp()
      createFailedRedWithGreenScheme()
      val res = repository.nextApplicationForFinalFailureNotification(10).futureValue
      res.size mustBe 1
      res.head.applicationId mustBe redApp

      repository.progressToFinalFailureNotified(res.head).futureValue
      repository.nextApplicationForFinalFailureNotification(10).futureValue.size mustBe 0
    }
  }

  "notifications on job offer" must {
    "be triggered for ready applications and only once" in {
      val appId = createEligibleForJobOffer()
      val res = repository.nextApplicationForFinalSuccessNotification(10).futureValue
      res.size mustBe 1
      res.head.applicationId mustBe appId
      repository.progressToJobOfferNotified(res.head).futureValue
      repository.nextApplicationForFinalSuccessNotification(10).futureValue.size mustBe 0
    }
  }

  private def createFailedRedApp(): String = {
    val redApp = createApplication()
    val res = SchemeEvaluationResult("GovernmentOperationalResearchService", "Red")
    fsbRepo.saveResult(redApp, res).futureValue
    fsbRepo.updateCurrentSchemeStatus(redApp, Seq(res))
    applicationRepo.addProgressStatusAndUpdateAppStatus(redApp, ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED).futureValue
    redApp
  }

  private def createFailedRedWithGreenScheme(): String = {
    val redApp = createApplication()
    val s1 = SchemeEvaluationResult("GovernmentOperationalResearchService", "Red")
    fsbRepo.saveResult(redApp, s1).futureValue
    val s2 = SchemeEvaluationResult("DiplomaticService", "Green")
    fsbRepo.saveResult(redApp, s2).futureValue
    fsbRepo.updateCurrentSchemeStatus(redApp, Seq(s1, s2))
    applicationRepo.addProgressStatusAndUpdateAppStatus(redApp, ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED).futureValue
    redApp
  }

  private def createEligibleForJobOffer(): String = {
    val redApp = createApplication()
    applicationRepo.addProgressStatusAndUpdateAppStatus(redApp, ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER).futureValue
    redApp
  }

  private def createApplication(): String = {
    val applicationId = generateUUID()
    applicationRepo.collection.insert(ordered = false).one(
      BSONDocument(
        "applicationId" -> applicationId,
        "userId" -> applicationId
      )
    ).futureValue
    applicationId
  }
}
