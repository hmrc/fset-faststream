/*
 * Copyright 2016 HM Revenue & Customs
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

package repositories

import model.AssessmentScheduleCommands.ApplicationForAssessmentAllocationResult
import model.Commands._
import model.{ApplicationStatuses, EvaluationResults}
import model.EvaluationResults.AssessmentRuleCategoryResult
import model.Exceptions.ApplicationNotFound
import model.persisted.AssistanceDetails
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import repositories.application.{GeneralApplicationMongoRepository, TestDataMongoRepository}
import repositories.assistancedetails.AssistanceDetailsMongoRepository
import services.GBTimeZoneService
import testkit.MongoRepositorySpec

import scala.concurrent.Await


class ApplicationRepositorySpec extends MongoRepositorySpec {

  import ImplicitBSONHandlers._

  val frameworkId = "FastStream-2016"

  val collectionName = "application"

  def applicationRepo = new GeneralApplicationMongoRepository(GBTimeZoneService)
  def assistanceRepo = new AssistanceDetailsMongoRepository()

  "Application repository" should {
    "create indexes for the repository" in {
      val repo = repositories.applicationRepository

      val indexes = indexesWithFields(repo)
      indexes must contain (List("_id"))
      indexes must contain (List("applicationId", "userId"))
      indexes must contain (List("userId", "frameworkId"))
      indexes must contain (List("online-tests.token"))
      indexes must contain (List("applicationStatus"))
      indexes must contain (List("online-tests.invitationDate"))
      indexes.size must be (6)
    }

    "return the gis parameter" in {
      val userId = "userId9876"
      val applicationId = applicationRepo.create(userId, "frameworkId").futureValue.applicationId

      val details = AssistanceDetails("Yes", Some("disability"), Some(true), true, Some("adjustment online"), true, Some("adjustment venue"))
      assistanceRepo.update(applicationId, userId, details).futureValue

      applicationRepo.gisByApplication(applicationId).futureValue must be(true)
    }
  }

  "Finding an application by User Id" should {

    "throw a NotFound exception when application doesn't exists" in {
      applicationRepo.findByUserId("invalidUser", "invalidFramework")
      an[ApplicationNotFound] must be thrownBy Await.result(applicationRepo.findByUserId("invalidUser", "invalidFramework"), timeout)
    }

    "throw an exception not of the type ApplicationNotFound when application is corrupt" in {
      Await.ready(applicationRepo.collection.insert(BSONDocument(
        "userId" -> "validUser",
        "frameworkId" -> "validFrameworkField"
        // but application Id framework, which is mandatory, so will fail to deserialise
      )), timeout)

      val thrown = the[Exception] thrownBy Await.result(applicationRepo.findByUserId("validUser", "validFrameworkField"), timeout)
      thrown must not be an[ApplicationNotFound]
    }
  }

  "Finding applications by user id" should {
    "return an empty list when no records for an applicationid exist" in {
      applicationRepo.find(List("appid-1")).futureValue.size must be(0)
    }

    "return a list of Candidates when records for an applicationid exist" in {
      val appResponse = applicationRepo.create("userId1", "framework").futureValue

      val result = applicationRepo.find(List(appResponse.applicationId)).futureValue

      result.size must be(1)
      result.head.applicationId.get must be(appResponse.applicationId)
      result.head.userId must be("userId1")
    }
  }

  "Applications report" should {

    "return an empty list when there are no applications" in {
      applicationRepo.applicationsReport(frameworkId).futureValue mustBe empty
    }
  
    "return a list of non submitted applications when there are only non submitted applications" in {
      Await.ready({
        for {
          _ <- applicationRepo.create("userId1", frameworkId)
          _ <- applicationRepo.create("userId2", frameworkId)
        } yield {
          Unit
        }
      }, timeout)

      val results = applicationRepo.applicationsReport(frameworkId).futureValue
      results must have size 2
      results.foreach { case (userId, isNonSubmitted, _) =>
        isNonSubmitted must be(true)
        userId must startWith("userId")
      }
    }
  
    "return only submitted applications" in {
      Await.ready({
        for {
          app <- applicationRepo.create("userId1", frameworkId)
          _ <- applicationRepo.submit(app.applicationId)
          app2 <- applicationRepo.create("userId2", frameworkId)
          _ <- applicationRepo.submit(app2.applicationId)
        } yield {
          Unit
        }
      }, timeout)

    
      val results = applicationRepo.applicationsReport(frameworkId).futureValue
      results must have size 2
      results.foreach { case (_, isNonSubmitted, _) =>
        isNonSubmitted must be(false)
      }
    }
    
    "return only the applications in a specific framework id" in {
      Await.ready({
        for {
          app <- applicationRepo.create("userId1", frameworkId)
          app2 <- applicationRepo.create("userId2", "otherFramework")
        } yield {
          Unit
        }
      }, timeout)

      val results = applicationRepo.applicationsReport(frameworkId).futureValue
      results must have size 1
    }
    
    "return a list of non submitted applications with submitted applications" in {
      Await.ready({
        for {
          app1 <- applicationRepo.create("userId1", frameworkId)
          _ <- applicationRepo.submit(app1.applicationId)
          _ <- applicationRepo.create("userId2", frameworkId)
          app3 <- applicationRepo.create("userId3", frameworkId)
          _ <- applicationRepo.submit(app3.applicationId)
          app4 <- applicationRepo.create("userId4", frameworkId)
          _ <- applicationRepo.submit(app4.applicationId)
          _ <- applicationRepo.create("userId5", frameworkId)
        } yield {
          Unit
        }
      }, timeout)
    
      val results = applicationRepo.applicationsReport(frameworkId).futureValue
      results must have size 5
      results.filter { case (_, isNonSubmitted, _) => isNonSubmitted } must have size 2
      results.filter { case (_, isNonSubmitted, _) => !isNonSubmitted } must have size 3
    }
  }

  "return the adjustments report" should {
    "return a list of AdjustmentReports" in {
      val frameworkId = "FastStream-2016"

      lazy val testData = new TestDataMongoRepository()
      testData.createApplications(1000).futureValue

      val listFut = applicationRepo.adjustmentReport(frameworkId)

      val result = Await.result(listFut, timeout)

      result mustBe a[List[_]]
      result must not be empty
      result.head mustBe a[AdjustmentReport]
      result.head.userId must not be empty

      val adjustmentReport = result.head
      adjustmentReport.adjustments must not be empty
      //Making sure the separator of adjustments is a pipe instead of a new line
      adjustmentReport.adjustments.get.contains("|") mustBe true
    }
  }
  "find applications for assessment allocation" should {
    "return an empty list when there are no applications" in {
      lazy val testData = new TestDataMongoRepository()
      testData.createApplications(0, false).futureValue

      val result = applicationRepo.findApplicationsForAssessmentAllocation(List("London"), 0, 5).futureValue
      result mustBe a[ApplicationForAssessmentAllocationResult]
      result.result mustBe empty
    }
    "return an empty list when there are no applications awaiting for allocation" in {
      lazy val testData = new TestDataMongoRepository()
      testData.createApplications(200, false).futureValue

      val result = applicationRepo.findApplicationsForAssessmentAllocation(List("London"), 0, 5).futureValue
      result mustBe a[ApplicationForAssessmentAllocationResult]
      result.result mustBe empty
    }
    "return a one item list when there are applications awaiting for allocation and start item and end item is the same" in {
      lazy val testData = new TestDataMongoRepository()
      testData.createApplications(200, true).futureValue

      val result =  applicationRepo.findApplicationsForAssessmentAllocation(List("London"), 2, 2).futureValue
      result mustBe a[ApplicationForAssessmentAllocationResult]
      result.result must have size(1)
    }
    "return a non empty list when there are applications" in {
      lazy val testData = new TestDataMongoRepository()
      testData.createApplications(200, true).futureValue

      val result =  applicationRepo.findApplicationsForAssessmentAllocation(List("London"), 0, 5).futureValue
      result mustBe a[ApplicationForAssessmentAllocationResult]
      result.result must not be empty
    }
    "return an empty list when start is beyond the number of results" in {
      lazy val testData = new TestDataMongoRepository()
      testData.createApplications(200, true).futureValue

      val result =  applicationRepo.findApplicationsForAssessmentAllocation(List("London"), Int.MaxValue, Int.MaxValue).futureValue
      result mustBe a[ApplicationForAssessmentAllocationResult]
      result.result mustBe empty
    }
    "return an empty list when start is higher than end" in {
      lazy val testData = new TestDataMongoRepository()
      testData.createApplications(200, true).futureValue

      val result =  applicationRepo.findApplicationsForAssessmentAllocation(List("London"), 2, 1).futureValue
      result mustBe a[ApplicationForAssessmentAllocationResult]
      result.result mustBe empty
    }
  }

  "next application ready for assessment score evaluation" should {
    "return the only application ready for evaluation" in {
      createApplication("app1", ApplicationStatuses.AssessmentScoresAccepted)

      val result = applicationRepo.nextApplicationReadyForAssessmentScoreEvaluation("1").futureValue

      result must not be empty
      result.get must be ("app1")
    }

    "return the next application when the passmark is different" in {
      createApplicationWithPassmark("app1", ApplicationStatuses.AwaitingAssessmentCentreReevaluation, "1")

      val result = applicationRepo.nextApplicationReadyForAssessmentScoreEvaluation("2").futureValue

      result must not be empty
    }

    "return none when application has already passmark and it has not changed" in {
      createApplicationWithPassmark("app1", ApplicationStatuses.AwaitingAssessmentCentreReevaluation, "1")

      val result = applicationRepo.nextApplicationReadyForAssessmentScoreEvaluation("1").futureValue

      result must be (empty)
    }

    "return none when there is no candidates in ASSESSMENT_SCORES_ACCEPTED status" in {
      createApplication("app1", "ASSESSMENT_SCORES_UNACCEPTED")

      val result = applicationRepo.nextApplicationReadyForAssessmentScoreEvaluation("1").futureValue

      result must be (empty)
    }
  }

  "save assessment score evaluation" should {
    "save a score evaluation and update the application status when the application is in ASSESSMENT_SCORES_ACCEPTED status" in {
      createApplication("app1", ApplicationStatuses.AssessmentScoresAccepted)

      val result = AssessmentRuleCategoryResult(Some(true), Some(EvaluationResults.Amber), None, None, None, None, None, None)
      applicationRepo.saveAssessmentScoreEvaluation("app1", "1", result, ApplicationStatuses.AwaitingAssessmentCentreReevaluation).futureValue

      val status = getApplicationStatus("app1")
      status must be (ApplicationStatuses.AwaitingAssessmentCentreReevaluation)
    }

    "save a score evaluation and update the application status when the application is in AWAITING_ASSESSMENT_CENTRE_RE_EVALUATION" in {
      createApplication("app1", ApplicationStatuses.AwaitingAssessmentCentreReevaluation)

      val result = AssessmentRuleCategoryResult(Some(true), Some(EvaluationResults.Amber), None, None, None, None, None, None)
      applicationRepo.saveAssessmentScoreEvaluation("app1", "1", result, ApplicationStatuses.AssessmentScoresAccepted).futureValue

      val status = getApplicationStatus("app1")
      status must be (ApplicationStatuses.AssessmentScoresAccepted)
    }

    "fail to save a score evaluation when candidate has been withdrawn" in {
      createApplication("app1", ApplicationStatuses.Withdrawn)

      val result = AssessmentRuleCategoryResult(Some(true), Some(EvaluationResults.Amber), None, None, None, None, None, None)
      applicationRepo.saveAssessmentScoreEvaluation("app1", "1", result, ApplicationStatuses.AssessmentScoresAccepted).futureValue

      val status = getApplicationStatus("app1")
      status must be (ApplicationStatuses.Withdrawn)
    }

    def getApplicationStatus(appId: String) = {
      applicationRepo.collection.find(BSONDocument("applicationId" -> "app1")).one[BSONDocument].map { docOpt =>
        docOpt must not be empty
        val doc = docOpt.get
        doc.getAs[String]("applicationStatus").get
      }.futureValue
    }
  }

  "applications passed in assessment centre" should {
    "be returned" in {
      val scores = CandidateScoresSummary(Some(2d), Some(2d), Some(2d), Some(2d),Some(2d),Some(2d),Some(2d), Some(14d))
      createApplicationWithSummaryScoresAndSchemeEvaluations("app1",frameworkId,
        ApplicationStatuses.AssessmentCentrePassed,
        "1",
        scores,
        SchemeEvaluation(Some("Red"), Some("Green"), Some("Amber"), Some("Red"), Some("Green"))
      )

      val result = applicationRepo.applicationsPassedInAssessmentCentre(frameworkId).futureValue
      result.size must be (1)
      result.head.applicationId must be ("app1")
      result.head.scores must be (scores)
      result.head.passmarks must be (SchemeEvaluation(
        Some("Fail"),
        Some("Pass"),
        Some("Amber"),
        Some("Fail"),
        Some("Pass")))
    }
  }

  "online test results" should {
    "be returned" in {
      createApplicationWithFrameworkEvaluations("app1", frameworkId,
        ApplicationStatuses.AssessmentScoresAccepted,
        "1",
        OnlineTestPassmarkEvaluationSchemes(Some("Red"), Some("Green"), Some("Amber"), Some("Red"), Some("Green"))
      )

      val result = applicationRepo.applicationsWithAssessmentScoresAccepted(frameworkId).futureValue
      result.size must be (1)
      result.head.applicationId must be ("app1")
      result.head.onlineTestPassmarkEvaluations must be (OnlineTestPassmarkEvaluationSchemes(
        Some("Fail"),
        Some("Pass"),
        Some("Amber"),
        Some("Fail"),
        Some("Pass")))
    }
  }

  "manual assessment centre allocation report" should {
    "return all candidates that are in awaiting allocation state" in {
      val testData = new TestDataMongoRepository()
      testData.createApplications(10,true).futureValue

      val result = applicationRepo.candidatesAwaitingAllocation(frameworkId).futureValue
      result must have size 10
    }
    "not return candidates that are initially awaiting allocation but subsequently withdrawn" in {
      val testData = new TestDataMongoRepository()
      testData.createApplications(10,true).futureValue

      val result = applicationRepo.candidatesAwaitingAllocation(frameworkId).futureValue
      result.foreach{ c =>
        val appId = applicationRepo.findByUserId(c.userId, frameworkId).futureValue.applicationId
        applicationRepo.withdraw(appId,WithdrawApplicationRequest("testing", None,"Candidate")).futureValue
      }

      val updatedResult = applicationRepo.candidatesAwaitingAllocation(frameworkId).futureValue
      updatedResult must be (empty)
    }
  }

  def createApplication(appId: String, appStatus: String): Unit = {
    applicationRepo.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "applicationStatus" -> appStatus
    )).futureValue
  }

  def createApplicationWithPassmark(appId: String, appStatus: String, passmarkVersion: String): Unit = {
    applicationRepo.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "applicationStatus" -> appStatus,
      "assessment-centre-passmark-evaluation" -> BSONDocument(
        "passmarkVersion" -> passmarkVersion
      )
    )).futureValue
  }

  def createApplicationWithFrameworkEvaluations(appId: String,
                                                frameworkId: String,
                                                appStatus: String,
                                                passmarkVersion: String,
                                                frameworkSchemes: OnlineTestPassmarkEvaluationSchemes): Unit = {
    applicationRepo.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "frameworkId" -> frameworkId,
      "applicationStatus" -> appStatus,
      "progress-status" -> BSONDocument(
        appStatus.toLowerCase -> true
      ),
      "passmarkEvaluation" -> BSONDocument(
        "passmarkVersion" -> passmarkVersion,
        "location1Scheme1" -> frameworkSchemes.location1Scheme1.get,
        "location1Scheme2" -> frameworkSchemes.location1Scheme2.get,
        "location2Scheme1" -> frameworkSchemes.location2Scheme1.get,
        "location2Scheme2" -> frameworkSchemes.location2Scheme2.get,
        "alternativeScheme" -> frameworkSchemes.alternativeScheme.get
        )
      )).futureValue
  }
  def createApplicationWithSummaryScoresAndSchemeEvaluations(appId: String,
                                                             frameworkId: String,
                                                             appStatus: String,
                                                             passmarkVersion: String,
                                                             scores: CandidateScoresSummary,
                                                             scheme: SchemeEvaluation): Unit = {

    applicationRepo.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "frameworkId" -> frameworkId,
      "applicationStatus" -> appStatus,
      "progress-status" -> BSONDocument(
        "assessment_centre_passed" -> true
      ),
      "assessment-centre-passmark-evaluation" -> BSONDocument(
        "passmarkVersion" -> passmarkVersion,
        "competency-average" -> BSONDocument(
          "leadingAndCommunicatingAverage" -> scores.avgLeadingAndCommunicating.get,
          "collaboratingAndPartneringAverage" -> scores.avgCollaboratingAndPartnering.get,
          "deliveringAtPaceAverage" -> scores.avgDeliveringAtPace.get,
          "makingEffectiveDecisionsAverage" -> scores.avgMakingEffectiveDecisions,
          "changingAndImprovingAverage" -> scores.avgChangingAndImproving,
          "buildingCapabilityForAllAverage" -> scores.avgBuildingCapabilityForAll,
          "motivationFitAverage" -> scores.avgMotivationFit,
          "overallScore" -> scores.totalScore
        ),
        "schemes-evaluation" -> BSONDocument(
          "Commercial" -> scheme.commercial.get,
          "Digital and technology" -> scheme.digitalAndTechnology.get,
          "Business" -> scheme.business.get,
          "Project delivery" -> scheme.projectDelivery.get,
          "Finance" -> scheme.finance.get
        )
      )
    )).futureValue
  }
}
