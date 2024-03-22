/*
 * Copyright 2017 HM Revenue & Customs
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

package repositories.fsb

import factories.{ITDateTimeFactoryMock, UUIDFactory}
import model.EvaluationResults.{Amber, Green, Red, Withdrawn}
import model.Exceptions.{ApplicationNotFound, CannotUpdateRecord}
import model.command.ApplicationForProgression
import model.persisted._
import model.persisted.fsb.ScoresAndFeedback
import model._
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Projections
import repositories.application.GeneralApplicationMongoRepository
import repositories.{CollectionNames, CommonRepository}
import testkit.MongoRepositorySpec

import java.time.temporal.ChronoUnit
import java.time.{Instant, OffsetDateTime, ZoneOffset}
import scala.util.Try

class FsbRepositorySpec extends MongoRepositorySpec with UUIDFactory with CommonRepository with Schemes {

  val collectionName = CollectionNames.APPLICATION
//  lazy val repository = repositories.fsbRepository
  lazy val repository = new FsbMongoRepository(ITDateTimeFactoryMock, mongo)
//  lazy val applicationRepo = repositories.applicationRepository
  lazy val applicationRepo = new GeneralApplicationMongoRepository(ITDateTimeFactoryMock, appConfig, mongo)

  def insert(doc: Document) = applicationCollection.insertOne(doc).toFuture()

  def createApplication(applicationRouteOpt: Option[ApplicationRoute.Value] = None): String = {
      val applicationId = generateUUID()
      val applicationRoute = applicationRouteOpt.getOrElse(ApplicationRoute.Faststream)
      insert(Document(
        "applicationId" -> applicationId,
        "userId" -> generateUUID(),
        "applicationRoute" -> applicationRoute.toBson
      )).futureValue
      applicationId
  }

  "all failed at fsb" must {
    "select candidates that are all red at FSB" in {
      val evalResults = SchemeEvaluationResult("GovernmentOperationalResearchService", Red.toString) ::
        SchemeEvaluationResult("Commercial", Red.toString) :: Nil
      insertApplicationAtFsbWithStatus("appId", evalResults, ProgressStatuses.FSB_FAILED)

      whenReady(repository.nextApplicationFailedAtFsb(1)) { result =>
        result.size mustBe 1
        result.head mustBe ApplicationForProgression("appId", ApplicationStatus.FSB, evalResults)
      }
    }

    "select candidates that are red or withdrawn at FSB" in {
      val evalResults = SchemeEvaluationResult("GovernmentOperationalResearchService", Red.toString) ::
        SchemeEvaluationResult("Commercial", Withdrawn.toString) :: Nil
      insertApplicationAtFsbWithStatus("appId", evalResults, ProgressStatuses.FSB_FAILED)

      whenReady(repository.nextApplicationFailedAtFsb(1)) { result =>
        result.size mustBe 1
        result.head mustBe ApplicationForProgression("appId", ApplicationStatus.FSB, evalResults)
      }
    }

    "not select candidates that are red and green at FSB" in {
      val evalResults = SchemeEvaluationResult("GovernmentOperationalResearchService", Red.toString) ::
        SchemeEvaluationResult("Commercial", Green.toString) :: Nil
      insertApplicationAtFsbWithStatus("appId", evalResults, ProgressStatuses.FSB_FAILED)

      whenReady(repository.nextApplicationFailedAtFsb(1)) { result =>
        result.size mustBe 0
      }
    }

    "not select candidates that are red and amber at FSB" in {
      val evalResults = SchemeEvaluationResult("GovernmentOperationalResearchService", Red.toString) ::
        SchemeEvaluationResult("Commercial", Amber.toString) :: Nil
      insertApplicationAtFsbWithStatus("appId", evalResults, ProgressStatuses.FSB_FAILED)

      whenReady(repository.nextApplicationFailedAtFsb(1)) { result =>
        result.size mustBe 0
      }
    }
  }

  "save" must {
    "create new FSB entry in testGroup if it doesn't exist" in {
      val applicationId = createApplication()
      val schemeEvaluationResult = SchemeEvaluationResult("GovernmentOperationalResearchService", Green.toString)
      repository.saveResult(applicationId, schemeEvaluationResult).futureValue
      val Some(result) = repository.findByApplicationId(applicationId).futureValue
      val fsbTestGroup = FsbTestGroup(List(schemeEvaluationResult))
      result mustBe fsbTestGroup
    }

    "add to result array if result array already exists" in {
      val applicationId = createApplication()
      val schemeEvaluationResult1 = SchemeEvaluationResult("GovernmentOperationalResearchService", Red.toString)
      repository.saveResult(applicationId, schemeEvaluationResult1).futureValue

      val schemeEvaluationResult2 = SchemeEvaluationResult("GovernmentSocialResearchService", Green.toString)
      repository.saveResult(applicationId, schemeEvaluationResult2).futureValue

      val Some(result) = repository.findByApplicationId(applicationId).futureValue
      val expectedFsbTestGroup = FsbTestGroup(List(schemeEvaluationResult1, schemeEvaluationResult2))
      result mustBe expectedFsbTestGroup
    }

    "not overwrite existing value" in {
      val applicationId = createApplication()
      val scheme: String = "GovernmentSocialResearchService"
      repository.saveResult(applicationId, SchemeEvaluationResult(scheme, Green.toString)).futureValue

      val exception = intercept[Exception] {
        repository.saveResult(applicationId,  SchemeEvaluationResult(scheme, Red.toString)).futureValue
      }
      exception.getMessage must include(s"Fsb evaluation already done for application $applicationId for scheme $scheme")
    }
  }

  "findByApplicationId" must {
    "return the FsbTestGroup for the given applicationId" in {
      val applicationId = createApplication()
      val schemeEvaluationResult = SchemeEvaluationResult("GovernmentOperationalResearchService", Green.toString)
      repository.saveResult(applicationId, schemeEvaluationResult).futureValue
      val Some(result) = repository.findByApplicationId(applicationId).futureValue
      val fsbTestGroup = FsbTestGroup(List(schemeEvaluationResult))
      result mustBe fsbTestGroup
    }

    "return None if FsbTestGroup is not found for the given applicationId" in {
      val result = repository.findByApplicationId("appId-with-no-fsb-test-group").futureValue
      result mustBe None
    }

    "return None if the given applicationId does not exist" in {
      repository.findByApplicationId("appId-that-does-not-exist").futureValue mustBe None
    }
  }

  "findByApplicationIds" must {
    "return the FsbSchemeResult for the given applicationIds" in {
      val applicationId1 = createApplication()
      val applicationId2 = createApplication()
      val applicationId3 = createApplication()
      val schemeEvaluationResult = SchemeEvaluationResult("GovernmentOperationalResearchService", Green.toString)

      repository.saveResult(applicationId1, schemeEvaluationResult).futureValue
      repository.saveResult(applicationId2, schemeEvaluationResult.copy(result = Red.toString)).futureValue
      repository.saveResult(applicationId3, schemeEvaluationResult).futureValue

      val result = repository.findByApplicationIds(List(applicationId1, applicationId2, applicationId3), schemeId = None).futureValue

      val expectedResult = List(
        FsbSchemeResult(applicationId1, List(schemeEvaluationResult)),
        FsbSchemeResult(applicationId2, List(schemeEvaluationResult.copy(result = Red.toString))),
        FsbSchemeResult(applicationId3, List(schemeEvaluationResult))
      )

      result must contain theSameElementsAs expectedResult
    }

    "return the FsbSchemeResult for the given applicationIds filtered by scheme" in {
      val applicationId1 = createApplication()
      val applicationId2 = createApplication()

      repository.saveResult(applicationId1, SchemeEvaluationResult("GovernmentOperationalResearchService", Red.toString)).futureValue
      repository.saveResult(applicationId1, SchemeEvaluationResult("GovernmentSocialResearchService", Green.toString)).futureValue
      repository.saveResult(applicationId2, SchemeEvaluationResult("GovernmentOperationalResearchService", Green.toString)).futureValue

      val result = repository.findByApplicationIds(
        List(applicationId1, applicationId2), Some(GovernmentOperationalResearchService)).futureValue

      val expectedResult = List(
        FsbSchemeResult(applicationId1, List(SchemeEvaluationResult("GovernmentOperationalResearchService", Red.toString))),
        FsbSchemeResult(applicationId2, List(SchemeEvaluationResult("GovernmentOperationalResearchService", Green.toString)))
      )

      result must contain theSameElementsAs expectedResult
    }
  }

  "nextApplicationReadyForFsbEvaluation" must {
    "process no candidates" in {
      repository.nextApplicationReadyForFsbEvaluation.futureValue mustBe None
    }

    "process eligible candidates" in {
      val appId = createApplication()
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.FSB_RESULT_ENTERED).futureValue
      repository.nextApplicationReadyForFsbEvaluation.futureValue mustBe Some(UniqueIdentifier(appId))
    }

    "not process candidates who are eligible for job offer" in {
      val appId = createApplication()
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.FSB_RESULT_ENTERED).futureValue
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER).futureValue
      repository.nextApplicationReadyForFsbEvaluation.futureValue mustBe None
    }
  }

  "nextApplicationForFsbOrJobOfferProgression" must {
    "return no candidates" in {
      repository.nextApplicationForFsbOrJobOfferProgression(10).futureValue mustBe Seq.empty
    }

    "return assessment centre passed candidate" in {
      val appId = createApplication()
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.ASSESSMENT_CENTRE_PASSED).futureValue
      applicationRepo.updateCurrentSchemeStatus(appId, Seq(SchemeEvaluationResult(Commercial, Green.toString))).futureValue
      repository.nextApplicationForFsbOrJobOfferProgression(10)
        .futureValue mustBe Seq(
        ApplicationForProgression(
          appId,
          ApplicationStatus.ASSESSMENT_CENTRE,
          currentSchemeStatus = Seq(SchemeEvaluationResult(Commercial, Green.toString))
        )
      )
    }

    "return specific assessment centre passed candidate" in {
      val appId = createApplication()
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.ASSESSMENT_CENTRE_PASSED).futureValue
      applicationRepo.updateCurrentSchemeStatus(appId, Seq(SchemeEvaluationResult(Commercial, Green.toString))).futureValue
      repository.nextApplicationForFsbOrJobOfferProgression(appId)
        .futureValue mustBe Seq(
        ApplicationForProgression(
          appId,
          ApplicationStatus.ASSESSMENT_CENTRE,
          currentSchemeStatus = Seq(SchemeEvaluationResult(Commercial, Green.toString))
        )
      )
    }

    "not return specific candidate who doesn't exist" in {
      repository.nextApplicationForFsbOrJobOfferProgression("missingAppId").futureValue mustBe Seq.empty
    }

    "return fsb failed candidate" in {
      val appId = createApplication()
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.FSB_FAILED).futureValue
      applicationRepo.updateCurrentSchemeStatus(appId, Seq(SchemeEvaluationResult(Commercial, Red.toString))).futureValue
      repository.nextApplicationForFsbOrJobOfferProgression(10)
        .futureValue mustBe Seq(
        ApplicationForProgression(
          appId,
          ApplicationStatus.FSB,
          currentSchemeStatus = Seq(SchemeEvaluationResult(Commercial, Red.toString))
        )
      )
    }

    "return sdip faststream assessment centre failed sdip green candidate" in {
      val appId = createApplication(Some(ApplicationRoute.SdipFaststream))
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.ASSESSMENT_CENTRE_FAILED_SDIP_GREEN_NOTIFIED).futureValue
      applicationRepo.updateCurrentSchemeStatus(appId,
        Seq(SchemeEvaluationResult(Commercial, Red.toString), SchemeEvaluationResult(Sdip, Green.toString))
      ).futureValue
      repository.nextApplicationForFsbOrJobOfferProgression(10)
        .futureValue mustBe Seq(
        ApplicationForProgression(
          appId,
          ApplicationStatus.ASSESSMENT_CENTRE,
          currentSchemeStatus = Seq(
            SchemeEvaluationResult(Commercial, Red.toString),
            SchemeEvaluationResult(Sdip, Green.toString)
          )
        )
      )
    }

    "return sift faststream failed sdip green candidate" in {
      val appId = createApplication(Some(ApplicationRoute.SdipFaststream))
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_FASTSTREAM_FAILED_SDIP_GREEN).futureValue
      applicationRepo.updateCurrentSchemeStatus(appId,
        Seq(
          SchemeEvaluationResult(DiplomaticAndDevelopmentEconomics, Red.toString),
          SchemeEvaluationResult(Sdip, Green.toString)
        )
      ).futureValue
      repository.nextApplicationForFsbOrJobOfferProgression(10)
        .futureValue mustBe Seq(
        ApplicationForProgression(
          appId,
          ApplicationStatus.SIFT,
          currentSchemeStatus = Seq(
            SchemeEvaluationResult(DiplomaticAndDevelopmentEconomics, Red.toString),
            SchemeEvaluationResult(Sdip, Green.toString)
          )
        )
      )
    }

    // Candidate is not in the running for faststream before entering sift but is Green for Sdip so should be picked up and moved to FSB for Sdip
    "return sdip faststream sift completed sdip green faststream red candidate" in {
      val appId = createApplication(Some(ApplicationRoute.SdipFaststream))
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_COMPLETED).futureValue
      applicationRepo.updateCurrentSchemeStatus(appId,
        Seq(SchemeEvaluationResult(Commercial, Red.toString), SchemeEvaluationResult(Sdip, Green.toString))
      ).futureValue
      repository.nextApplicationForFsbOrJobOfferProgression(10)
        .futureValue mustBe Seq(
        ApplicationForProgression(
          appId,
          ApplicationStatus.SIFT,
          currentSchemeStatus = Seq(
            SchemeEvaluationResult(Commercial, Red.toString),
            SchemeEvaluationResult(Sdip, Green.toString)
          )
        )
      )
    }

    // Candidate is still in the running for faststream so should not be picked up by this job.
    // The fsac job should invite the candidate to assessment centre instead
    "not return sdip faststream sift completed sdip green faststream green candidate" in {
      val appId = createApplication(Some(ApplicationRoute.SdipFaststream))
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_COMPLETED).futureValue
      applicationRepo.updateCurrentSchemeStatus(appId,
        Seq(SchemeEvaluationResult(Commercial, Green.toString), SchemeEvaluationResult(Sdip, Green.toString))
      ).futureValue
      repository.nextApplicationForFsbOrJobOfferProgression(10)
        .futureValue mustBe Nil
    }

    "return sdip only sift completed sdip green candidate" in {
      val appId = createApplication(Some(ApplicationRoute.Sdip))
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_COMPLETED).futureValue
      applicationRepo.updateCurrentSchemeStatus(appId,
        Seq(SchemeEvaluationResult(Sdip, Green.toString))
      ).futureValue
      repository.nextApplicationForFsbOrJobOfferProgression(10)
        .futureValue mustBe Seq(
        ApplicationForProgression(
          appId,
          ApplicationStatus.SIFT,
          currentSchemeStatus = Seq(SchemeEvaluationResult(Sdip, Green.toString))
        )
      )
    }

    "not return sdip only sift completed sdip red candidate" in {
      val appId = createApplication(Some(ApplicationRoute.Sdip))
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_COMPLETED).futureValue
      applicationRepo.updateCurrentSchemeStatus(appId,
        Seq(SchemeEvaluationResult(Sdip, Red.toString))
      ).futureValue
      repository.nextApplicationForFsbOrJobOfferProgression(10).futureValue mustBe Nil
    }

    "return edip only sift completed edip green candidate" in {
      val appId = createApplication(Some(ApplicationRoute.Edip))
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_COMPLETED).futureValue
      applicationRepo.updateCurrentSchemeStatus(appId,
        Seq(SchemeEvaluationResult(Edip, Green.toString))
      ).futureValue
      repository.nextApplicationForFsbOrJobOfferProgression(10)
        .futureValue mustBe Seq(
        ApplicationForProgression(
          appId,
          ApplicationStatus.SIFT,
          currentSchemeStatus = Seq(SchemeEvaluationResult(Edip, Green.toString))
        )
      )
    }

    "not return edip only sift completed edip red candidate" in {
      val appId = createApplication(Some(ApplicationRoute.Edip))
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_COMPLETED).futureValue
      applicationRepo.updateCurrentSchemeStatus(appId,
        Seq(SchemeEvaluationResult(Edip, Red.toString))
      ).futureValue
      repository.nextApplicationForFsbOrJobOfferProgression(10).futureValue mustBe Nil
    }
  }

  "progressToFsb" must {
    "handle a candidate which does not exist" in {
      val appToProgress = ApplicationForProgression("appId", ApplicationStatus.ASSESSMENT_CENTRE, currentSchemeStatus = Nil)
      val result = repository.progressToFsb(appToProgress).failed.futureValue
      result mustBe a[CannotUpdateRecord]
    }

    "process a candidate" in {
      val appId = createApplication()
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.ASSESSMENT_CENTRE_PASSED).futureValue
      val progressResponse = applicationRepo.findProgress(appId).futureValue
      progressResponse.fsb.awaitingAllocation mustBe false
      val appToProgress = ApplicationForProgression(appId, ApplicationStatus.ASSESSMENT_CENTRE, currentSchemeStatus = Nil)
      repository.progressToFsb(appToProgress).futureValue
      val progressResponseAfterUpdate = applicationRepo.findProgress(appId).futureValue
      progressResponseAfterUpdate.fsb.awaitingAllocation mustBe true
    }
  }

  "progressToJobOffer" must {
    "handle a candidate which does not exist" in {
      val appToProgress = ApplicationForProgression("appId", ApplicationStatus.FSB, currentSchemeStatus = Nil)
      val result = repository.progressToJobOffer(appToProgress).failed.futureValue
      result mustBe a[CannotUpdateRecord]
    }

    "process a candidate" in {
      val appId = createApplication()
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.FSB_PASSED).futureValue
      val progressResponse = applicationRepo.findProgress(appId).futureValue
      progressResponse.eligibleForJobOffer.eligible mustBe false
      val appToProgress = ApplicationForProgression(appId, ApplicationStatus.FSB, currentSchemeStatus = Nil)
      repository.progressToJobOffer(appToProgress).futureValue
      val progressResponseAfterUpdate = applicationRepo.findProgress(appId).futureValue
      progressResponseAfterUpdate.eligibleForJobOffer.eligible mustBe true
    }
  }

  "nextApplicationFailedAtFsb" must {
    "deal with no eligible candidates" in {
      repository.nextApplicationFailedAtFsb(10).futureValue mustBe Nil
    }

    "deal with eligible fsb failed candidates" in {
      val appId = createApplication()
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.FSB_FAILED).futureValue
      applicationRepo.updateCurrentSchemeStatus(appId,
        Seq(SchemeEvaluationResult(Commercial, Red.toString))
      ).futureValue
      repository.nextApplicationFailedAtFsb(10)
        .futureValue mustBe Seq(
        ApplicationForProgression(
          appId,
          ApplicationStatus.FSB,
          currentSchemeStatus = Seq(SchemeEvaluationResult(Commercial, Red.toString))
        )
      )
    }

    "ignore candidates who are eligible for job offer" in {
      val appId = createApplication()
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.FSB_FAILED).futureValue
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER).futureValue
      applicationRepo.updateCurrentSchemeStatus(appId,
        Seq(SchemeEvaluationResult(Commercial, Green.toString))
      ).futureValue
      repository.nextApplicationFailedAtFsb(10).futureValue mustBe Nil
    }

    "ignore candidates who are all fsbs and fsacs failed" in {
      val appId = createApplication()
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.FSB_FAILED).futureValue
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED).futureValue
      applicationRepo.updateCurrentSchemeStatus(appId,
        Seq(SchemeEvaluationResult(Commercial, Red.toString))
      ).futureValue
      repository.nextApplicationFailedAtFsb(10).futureValue mustBe Nil
    }

    "ignore candidates who have a green in css but are fsb failed" in {
      val appId = createApplication()
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.FSB_FAILED).futureValue
      applicationRepo.updateCurrentSchemeStatus(appId,
        Seq(
          SchemeEvaluationResult(Commercial, Green.toString),
          SchemeEvaluationResult(DiplomaticAndDevelopment, Red.toString)
        )
      ).futureValue
      repository.nextApplicationFailedAtFsb(10).futureValue mustBe Nil
    }

    "ignore candidates who have an amber in css but are fsb failed" in {
      val appId = createApplication()
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.FSB_FAILED).futureValue
      applicationRepo.updateCurrentSchemeStatus(appId,
        Seq(
          SchemeEvaluationResult(Commercial, Amber.toString),
          SchemeEvaluationResult(DiplomaticAndDevelopment, Red.toString)
        )
      ).futureValue
      repository.nextApplicationFailedAtFsb(10).futureValue mustBe Nil
    }

    "handle a specific candidate who does not exist" in {
      repository.nextApplicationFailedAtFsb("appId").futureValue mustBe Nil
    }

    "handle a specific eligible candidate" in {
      val appId = createApplication()
      applicationRepo.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.FSB_FAILED).futureValue
      applicationRepo.updateCurrentSchemeStatus(appId,
        Seq(SchemeEvaluationResult(Commercial, Red.toString))
      ).futureValue
      repository.nextApplicationFailedAtFsb(appId)
        .futureValue mustBe Seq(
        ApplicationForProgression(
          appId,
          ApplicationStatus.FSB,
          currentSchemeStatus = Seq(SchemeEvaluationResult(Commercial, Red.toString))
        )
      )
    }
  }

  "removeTestGroup" must {
    "handle a candidate that does not exist" in {
      val result = repository.removeTestGroup("appId").failed.futureValue
      result mustBe an[ApplicationNotFound]
    }

    "remove as expected" in {
      val appId = createApplication()
      val schemeEvaluationResult = SchemeEvaluationResult("Commercial", Green.toString)
      repository.saveResult(appId, schemeEvaluationResult).futureValue
      repository.findByApplicationId(appId).futureValue mustBe Some(FsbTestGroup(List(schemeEvaluationResult)))
      repository.removeTestGroup(appId).futureValue
      repository.findByApplicationId(appId).futureValue mustBe None
    }
  }

  "saveScoresAndFeedback" must {
    "handle a candidate who does not exist" in {
      val result = repository.saveScoresAndFeedback("appId", ScoresAndFeedback(overallScore = 10.0d, feedback = "")).failed.futureValue
      result mustBe an[CannotUpdateRecord]
    }

    "handle a candidate who does exist" in {
      val appId = createApplication()
      val scoresAndFeedback = ScoresAndFeedback(overallScore = 10.0d, feedback = "test feedback")
      repository.saveScoresAndFeedback(appId, scoresAndFeedback).futureValue
      repository.findScoresAndFeedback(appId).futureValue mustBe Some(scoresAndFeedback)
    }
  }

  "findScoresAndFeedback" must {
    "handle a candidate who does not exist" in {
      repository.findScoresAndFeedback("appId").futureValue mustBe None
    }

    "fetch multiple candidates" in {
      val appId1 = createApplication()
      val appId2 = createApplication()
      val scoresAndFeedback = ScoresAndFeedback(overallScore = 10.0d, feedback = "test feedback")
      repository.saveScoresAndFeedback(appId1, scoresAndFeedback).futureValue
      val result = repository.findScoresAndFeedback(List(appId1, appId2)).futureValue
      result mustBe Map(appId1 -> Some(scoresAndFeedback), appId2 -> None)
    }
  }

  "updateResult" must {
    "update the data as expected" in {
      val appId = createApplication()
      val commercialPassed = SchemeEvaluationResult("Commercial", Green.toString)
      repository.saveResult(appId, commercialPassed).futureValue
      repository.findByApplicationId(appId).futureValue mustBe Some(FsbTestGroup(List(commercialPassed)))

      val commercialFailed = SchemeEvaluationResult("Commercial", Red.toString)
      repository.updateResult(appId, commercialFailed).futureValue
      repository.findByApplicationId(appId).futureValue mustBe Some(FsbTestGroup(List(commercialFailed)))
    }
  }

  "addFsbProgressStatuses" must {
    "must provide progress statuses" in {
      // Note failed.futureValue does not work for this scenario so use intercept instead
      intercept[IllegalArgumentException] {
        repository.addFsbProgressStatuses("appId", Nil)
      }
    }

    def isFsbProgressStatusStored(applicationId: String, progressStatus: String) = {
      applicationCollection.find[BsonDocument](Document("applicationId" -> applicationId))
        .projection(Projections.include("fsb-progress-status")).headOption().map {
        _.flatMap { doc =>
          Try(doc.get("fsb-progress-status").asDocument().get(progressStatus).asBoolean().getValue).toOption
        }.getOrElse(false)
      }
    }

    def getFsbProgressStatusTimestamp(applicationId: String, progressStatus: String) = {
      applicationCollection.find[BsonDocument](Document("applicationId" -> applicationId))
        .projection(Projections.include("fsb-progress-status-timestamp")).headOption().map {
        _.flatMap { doc =>
          Try(doc.get("fsb-progress-status-timestamp").asDocument().get(progressStatus).asDateTime().getValue).toOption
        }
      }.map( _.map (instant => OffsetDateTime.ofInstant(Instant.ofEpochMilli(instant), ZoneOffset.UTC)))
    }

    "add data as expected" in {
      val appId = createApplication()
      val now = OffsetDateTime.now.truncatedTo(ChronoUnit.MILLIS)
      repository.addFsbProgressStatuses(appId, List("test" -> now)).futureValue
      isFsbProgressStatusStored(appId, "test").futureValue mustBe true
      getFsbProgressStatusTimestamp(appId, "test").futureValue mustBe Some(now)
    }
  }
}
