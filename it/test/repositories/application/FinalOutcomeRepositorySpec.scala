/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package repositories.application

import factories.{ITDateTimeFactoryMock, UUIDFactory}
import model.EvaluationResults.{Green, Red}
import model.ProgressStatuses
import model.command.ApplicationForProgression
import model.persisted.SchemeEvaluationResult
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.{MongoCollection, SingleObservableFuture}
import repositories.CollectionNames
import repositories.fsb.FsbMongoRepository
import testkit.MongoRepositorySpec

class FinalOutcomeRepositorySpec extends MongoRepositorySpec with UUIDFactory {

  val collectionName = CollectionNames.APPLICATION
  lazy val repository = new FinalOutcomeMongoRepository(ITDateTimeFactoryMock, mongo)
  lazy val fsbRepo = new FsbMongoRepository(ITDateTimeFactoryMock, mongo)
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
      val result = applicationRepo.findProgress(appId).futureValue
      result.assessmentCentre.failedSdipGreen mustBe true
      result.assessmentCentre.failedSdipGreenNotified mustBe true
    }
  }

  private def createFailedRedApp: String = {
    val redAppId = createApplication
    val res = SchemeEvaluationResult("GovernmentOperationalResearchService", Red.toString)
    fsbRepo.saveResult(redAppId, res).futureValue
    fsbRepo.updateCurrentSchemeStatus(redAppId, Seq(res))
    applicationRepo.addProgressStatusAndUpdateAppStatus(redAppId, ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED).futureValue
    redAppId
  }

  private def createFailedRedWithGreenScheme: String = {
    val redAppId = createApplication
    val s1 = SchemeEvaluationResult("GovernmentOperationalResearchService", Red.toString)
    fsbRepo.saveResult(redAppId, s1).futureValue
    val s2 = SchemeEvaluationResult("DiplomaticAndDevelopment", Green.toString)
    fsbRepo.saveResult(redAppId, s2).futureValue
    fsbRepo.updateCurrentSchemeStatus(redAppId, Seq(s1, s2))
    applicationRepo.addProgressStatusAndUpdateAppStatus(redAppId, ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED).futureValue
    redAppId
  }

  private def createEligibleForJobOffer: String = {
    val greenAppId = createApplication
    val s1 = SchemeEvaluationResult("DiplomaticService", Green.toString)
    fsbRepo.saveResult(greenAppId, s1).futureValue
    fsbRepo.updateCurrentSchemeStatus(greenAppId, Seq(s1))
    applicationRepo.addProgressStatusAndUpdateAppStatus(greenAppId, ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER).futureValue
    greenAppId
  }

  private def createAssessmentCentreFailedSdipGreen: (String, Seq[SchemeEvaluationResult]) = {
    val sdipAppId = createApplication
    val s1 = SchemeEvaluationResult("DiplomaticService", Red.toString)
    val s2 = SchemeEvaluationResult("Sdip", Green.toString)
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
