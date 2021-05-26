package repositories.application

import factories.{ITDateTimeFactoryMock, UUIDFactory}
import model.ProgressStatuses
import model.command.ApplicationForProgression
import model.persisted.SchemeEvaluationResult
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
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

  val applicationCollection: MongoCollection[Document] = mongo.database.getCollection(collectionName)

  "next final failed" must {
    "return only application for final fsb failed with no green schemes" in {
      val redApp = createFailedRedApp
      createFailedRedWithGreenScheme
      val res = repository.nextApplicationForFinalFailureNotification(10).futureValue
      res.size mustBe 1
      res.head.applicationId mustBe redApp

      repository.progressToFinalFailureNotified(res.head).futureValue
      repository.nextApplicationForFinalFailureNotification(10).futureValue.size mustBe 0
    }
  }

  "notifications on job offer" must {
    "be triggered for ready applications and only once" in {
      val appId = createEligibleForJobOffer
      val res = repository.nextApplicationForFinalSuccessNotification(10).futureValue
      res.size mustBe 1
      res.head.applicationId mustBe appId
      repository.progressToJobOfferNotified(res.head).futureValue
      repository.nextApplicationForFinalSuccessNotification(10).futureValue.size mustBe 0
    }
  }

  "updating an application to assessment centre failed sdip green notified" must {
    "work for the given application" in {
      val (appId, css) = createAssessmentCentreFailedSdipGreen
      val appToProgress = ApplicationForProgression(appId, ProgressStatuses.ASSESSMENT_CENTRE_FAILED_SDIP_GREEN.applicationStatus, css)

      repository.progressToAssessmentCentreFailedSdipGreenNotified(appToProgress).futureValue
      // TODO: mongo Now read back the data and verify the new ps has been added
    }
  }

  private def createFailedRedApp: String = {
    val redAppId = createApplication
    val res = SchemeEvaluationResult("GovernmentOperationalResearchService", "Red")
    fsbRepo.saveResult(redAppId, res).futureValue
    fsbRepo.updateCurrentSchemeStatus(redAppId, Seq(res))
    applicationRepo.addProgressStatusAndUpdateAppStatus(redAppId, ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED).futureValue
    redAppId
  }

  private def createFailedRedWithGreenScheme: String = {
    val redAppId = createApplication
    val s1 = SchemeEvaluationResult("GovernmentOperationalResearchService", "Red")
    fsbRepo.saveResult(redAppId, s1).futureValue
    val s2 = SchemeEvaluationResult("DiplomaticService", "Green")
    fsbRepo.saveResult(redAppId, s2).futureValue
    fsbRepo.updateCurrentSchemeStatus(redAppId, Seq(s1, s2))
    applicationRepo.addProgressStatusAndUpdateAppStatus(redAppId, ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED).futureValue
    redAppId
  }

  private def createEligibleForJobOffer: String = {
    val greenAppId = createApplication
    val s1 = SchemeEvaluationResult("DiplomaticService", "Green")
    fsbRepo.saveResult(greenAppId, s1).futureValue
    fsbRepo.updateCurrentSchemeStatus(greenAppId, Seq(s1))
    applicationRepo.addProgressStatusAndUpdateAppStatus(greenAppId, ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER).futureValue
    greenAppId
  }

  private def createAssessmentCentreFailedSdipGreen: (String, Seq[SchemeEvaluationResult]) = {
    val sdipAppId = createApplication
    val s1 = SchemeEvaluationResult("DiplomaticService", "Red")
    val s2 = SchemeEvaluationResult("Sdip", "Green")
    fsbRepo.updateCurrentSchemeStatus(sdipAppId, Seq(s1, s2))
    applicationRepo.addProgressStatusAndUpdateAppStatus(sdipAppId, ProgressStatuses.ASSESSMENT_CENTRE_FAILED_SDIP_GREEN).futureValue
    sdipAppId -> Seq(s1, s2)
  }

  private def createApplication: String = {
    val applicationId = generateUUID()
    applicationCollection.insertOne(
      Document(
        "applicationId" -> applicationId,
        "userId" -> applicationId
      )
    ).toFuture().futureValue
    applicationId
  }
}
