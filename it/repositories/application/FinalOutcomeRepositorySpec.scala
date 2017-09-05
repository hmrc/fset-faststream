package repositories.application

import factories.UUIDFactory
import reactivemongo.json.ImplicitBSONHandlers._
import model.ProgressStatuses
import model.persisted.SchemeEvaluationResult
import reactivemongo.bson.BSONDocument
import repositories.CollectionNames
import testkit.MongoRepositorySpec

class FinalOutcomeRepositorySpec extends MongoRepositorySpec with UUIDFactory {

  val collectionName = CollectionNames.APPLICATION
  lazy val repository = repositories.finalOutcomeRepository
  lazy val fsbRepo = repositories.fsbRepository
  lazy val applicationRepo = repositories.applicationRepository

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
    fsbRepo.save(redApp, SchemeEvaluationResult("GovernmentOperationalResearchService", "Red")).futureValue
    applicationRepo.addProgressStatusAndUpdateAppStatus(redApp, ProgressStatuses.FSB_FAILED).futureValue
    redApp
  }

  private def createFailedRedWithGreenScheme(): String = {
    val redApp = createApplication()
    fsbRepo.save(redApp, SchemeEvaluationResult("GovernmentOperationalResearchService", "Red")).futureValue
    fsbRepo.save(redApp, SchemeEvaluationResult("DiplomaticService", "Green")).futureValue
    applicationRepo.addProgressStatusAndUpdateAppStatus(redApp, ProgressStatuses.FSB_FAILED).futureValue
    redApp
  }

  private def createEligibleForJobOffer(): String = {
    val redApp = createApplication()
    applicationRepo.addProgressStatusAndUpdateAppStatus(redApp, ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER).futureValue
    redApp
  }

  private def createApplication(): String = {
    val applicationId = generateUUID()
    applicationRepo.collection.insert(
      BSONDocument(
        "applicationId" -> applicationId,
        "userId" -> applicationId
      )
    ).futureValue
    applicationId
  }

}
