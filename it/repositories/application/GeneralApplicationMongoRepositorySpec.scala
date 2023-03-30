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

package repositories.application

import factories.{ITDateTimeFactoryMock, UUIDFactory}
import model.ApplicationRoute.{ApplicationRoute, apply => _}
import model.ApplicationStatus._
import model.EvaluationResults.{Green, Red}
import model.Exceptions.{toString, _}
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses.{PHASE1_TESTS_PASSED => _, PHASE3_TESTS_FAILED => _, SUBMITTED => _, _}
import model.command.{ProgressResponse, WithdrawScheme}
import model.exchange.CandidatesEligibleForEventResponse
import model.persisted._
import model.persisted.eventschedules.EventType
import model.persisted.fsac.{AnalysisExercise, AssessmentCentreTests}
import model.{ApplicationStatus, _}
import org.joda.time.LocalDate
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Projections
import repositories.{CollectionNames, SchemeRepository}
import repositories.assessmentcentre.AssessmentCentreMongoRepository
import repositories.onlinetesting.{Phase1TestMongoRepository, Phase2TestMongoRepository}
import scheduler.fixer.FixBatch
import scheduler.fixer.RequiredFixes.{PassToPhase2, ResetPhase1TestInvitedSubmitted}
import testkit.MongoRepositorySpec
import uk.gov.hmrc.mongo.play.json.Codecs

import scala.concurrent.{Await, Future}
import scala.util.Try

class GeneralApplicationMongoRepositorySpec extends MongoRepositorySpec with UUIDFactory {

  val collectionName = CollectionNames.APPLICATION

  def repository = new GeneralApplicationMongoRepository(ITDateTimeFactoryMock, appConfig, mongo)
  def phase1TestRepo = new Phase1TestMongoRepository(ITDateTimeFactoryMock, mongo)
  def phase2TestRepo = new Phase2TestMongoRepository(ITDateTimeFactoryMock, mongo)
  def testDataRepo = new TestDataMongoRepository(mongo)
  val schemeRepositoryMock = mock[SchemeRepository]
  def assessmentCentreRepo = new AssessmentCentreMongoRepository(ITDateTimeFactoryMock, schemeRepositoryMock, mongo)

  lazy val applicationCollection: MongoCollection[Document] = mongo.database.getCollection(collectionName)

  "General Application repository" should {
    "create indexes" in {
      val indexes = indexDetails(repository).futureValue
      indexes must contain theSameElementsAs
        Seq(
          IndexDetails(name = "_id_", keys = Seq(("_id", "Ascending")), unique = false),
          IndexDetails(name = "applicationId_1_userId_1", keys = Seq(("applicationId", "Ascending"), ("userId", "Ascending")), unique = true),
          IndexDetails(name = "userId_1_frameworkId_1", keys = Seq(("userId", "Ascending"), ("frameworkId", "Ascending")), unique = true),
          IndexDetails(name = "applicationStatus_1", keys = Seq(("applicationStatus", "Ascending")), unique = false),
          IndexDetails(name = "assistance-details.needsSupportForOnlineAssessment_1",
            keys = Seq(("assistance-details.needsSupportForOnlineAssessment", "Ascending")), unique = false),
          IndexDetails(name = "assistance-details.needsSupportAtVenue_1",
            keys = Seq(("assistance-details.needsSupportAtVenue", "Ascending")), unique = false),
          IndexDetails(name = "assistance-details.guaranteedInterview_1",
            keys = Seq(("assistance-details.guaranteedInterview", "Ascending")), unique = false)
        )
    }

    def isSdipDiversity(applicationId: String) = {
      applicationCollection.find[BsonDocument](Document("applicationId" -> applicationId))
        .projection(Projections.include("sdipDiversity")).headOption().map {
        _.flatMap { doc =>
          Try(doc.get("sdipDiversity").asBoolean().getValue).toOption
        }
      }
    }

    "create candidate" should {
      "create and fetch an sdip diversity candidate" in {
        val newCandidate = repository.create("userId", "frameworkId", ApplicationRoute.Faststream, sdipDiversityOpt = Some(true)).futureValue
        isSdipDiversity(newCandidate.applicationId).futureValue mustBe Some(true)
      }

      "create and fetch a non sdip diversity candidate" in {
        val newCandidate = repository.create("userId", "frameworkId", ApplicationRoute.Faststream).futureValue
        isSdipDiversity(newCandidate.applicationId).futureValue mustBe None
      }
    }

    "find application by userId and frameworkId" in {
      val userId = "fastPassUser"
      val appId = "fastPassApp"
      val testAccountId = "testAccountId"
      val frameworkId = "FastStream-2016"

      testDataRepo.createApplicationWithAllFields(userId, appId, testAccountId, frameworkId).futureValue

      val applicationResponse = repository.findByUserId(userId, frameworkId).futureValue

      applicationResponse.userId mustBe userId
      applicationResponse.applicationId mustBe appId
      applicationResponse.civilServiceExperienceDetails.get mustBe
        CivilServiceExperienceDetails(applicable = true,
          civilServantAndInternshipTypes = Some(List(
            CivilServantAndInternshipType.CivilServant,
            CivilServantAndInternshipType.EDIP,
            CivilServantAndInternshipType.SDIP,
            CivilServantAndInternshipType.OtherInternship
          )),
          edipYear = Some("2018"),
          sdipYear = Some("2019"),
          otherInternshipName = Some("other"),
          otherInternshipYear = Some("2020"),
          fastPassReceived = Some(true),
          certificateNumber = Some("1234567"))
    }

    "find application by userId only" in {
      val userId = "fastPassUser"
      val appId = "fastPassApp"
      val testAccountId = "testAccountId"
      val frameworkId = "FastStream-2016"

      testDataRepo.createApplicationWithAllFields(userId, appId, testAccountId, frameworkId).futureValue

      val applicationResponse = repository.findCandidateByUserId(userId).futureValue

      applicationResponse mustBe defined
      applicationResponse.get.applicationId mustBe Some(appId)
      applicationResponse.get.lastName mustBe Some("Jetson")
    }

    "find application by appId" in {
      val userId = "fastPassUser"
      val appId = "fastPassApp"
      val testAccountId = "testAccountId"
      val frameworkId = "FastStream-2016"

      testDataRepo.createApplicationWithAllFields(userId, appId, testAccountId, frameworkId).futureValue

      val applicationResponse = repository.find(appId).futureValue
      applicationResponse mustBe defined
      applicationResponse.get.applicationId mustBe Some(appId)
    }

    "return None when finding application by appId and the application does not exist" in {
      val applicationResponse = repository.find(AppId).futureValue
      applicationResponse mustBe None
    }

    "Find application status" in {
      val userId = "fastPassUser"
      val appId = "fastPassApp"
      val testAccountId = "testAccountId"
      val frameworkId = "FastStream-2016"

      testDataRepo.createApplicationWithAllFields(userId, appId, testAccountId, frameworkId, appStatus = SUBMITTED).futureValue

      val applicationStatusDetails = repository.findStatus(appId).futureValue

      applicationStatusDetails.status mustBe SUBMITTED.toString
      applicationStatusDetails.statusDate.get.toLocalDate mustBe LocalDate.now()
    }
  }

  "Find by criteria" should {
    "find by first name" in {
      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId", "FastStream-2016").futureValue

      val applicationResponse = repository.findByCriteria(
        firstOrPreferredNameOpt = Some(testCandidate("firstName")), lastNameOpt = None, dateOfBirthOpt = None
      ).futureValue

      applicationResponse.size mustBe 1

      val expectedCandidate = Candidate("userId", Some("appId123"), testAccountId = None,
        email = None, firstName = Some("George"), lastName = Some("Jetson"),
        preferredName = Some("Georgy"), dateOfBirth = Some(java.time.LocalDate.of(1986, 5, 1)),
        address = None, postCode = None, country = None,
        applicationRoute = Some(ApplicationRoute.Faststream),
        applicationStatus = Some(ApplicationStatus.IN_PROGRESS)
      )
      applicationResponse.head mustBe expectedCandidate
    }

    "find by preferred name" in {
      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId", "FastStream-2016").futureValue

      val applicationResponse = repository.findByCriteria(
        firstOrPreferredNameOpt = Some(testCandidate("preferredName")), lastNameOpt = None, dateOfBirthOpt = None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find by first/preferred name with special regex character" in {
      testDataRepo.createApplicationWithAllFields(userId = "userId", appId = "appId123", testAccountId = "testAccountId",
        frameworkId = "FastStream-2016", firstName = Some("Char+lie.+x123")).futureValue

      val applicationResponse = repository.findByCriteria(
        firstOrPreferredNameOpt = Some("Char+lie.+x123"), lastNameOpt = None, dateOfBirthOpt = None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find by lastname" in {
      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId", "FastStream-2016").futureValue
      val applicationResponse = repository.findByCriteria(
        firstOrPreferredNameOpt = None, lastNameOpt = Some(testCandidate("lastName")), dateOfBirthOpt = None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find by lastname with special regex character" in {
      testDataRepo.createApplicationWithAllFields(userId = "userId", appId = "appId123", testAccountId = "testAccountId",
        frameworkId = "FastStream-2016", lastName = Some("Barr+y.+x123")).futureValue
      val applicationResponse = repository.findByCriteria(
        firstOrPreferredNameOpt = None, lastNameOpt = Some("Barr+y.+x123"), dateOfBirthOpt = None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find by date of birth" in {
      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId", "FastStream-2016").futureValue
      val dobParts = testCandidate("dateOfBirth").split("-").map(_.toInt)
      val (dobYear, dobMonth, dobDay) = (dobParts.head, dobParts(1), dobParts(2))

      val applicationResponse = repository.findByCriteria(
        firstOrPreferredNameOpt = None, lastNameOpt = None, dateOfBirthOpt = Some(java.time.LocalDate.of(dobYear, dobMonth, dobDay))
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "Return an empty candidate list when there are no results" in {
      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId", "FastStream-2016").futureValue
      val applicationResponse = repository.findByCriteria(
        firstOrPreferredNameOpt = Some("UnknownFirstName"), lastNameOpt = None, dateOfBirthOpt = None
      ).futureValue

      applicationResponse.size mustBe 0
    }

    "filter by provided user Ids" in {
      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId", "FastStream-2016").futureValue
      val matchResponse = repository.findByCriteria(
        firstOrPreferredNameOpt = None, lastNameOpt = None, dateOfBirthOpt = None, filterByUserIds = List("userId")
      ).futureValue

      matchResponse.size mustBe 1

      val noMatchResponse = repository.findByCriteria(
        firstOrPreferredNameOpt = None, lastNameOpt = None, dateOfBirthOpt = None, filterByUserIds = List("unknownUser")
      ).futureValue

      noMatchResponse.size mustBe 0
    }
  }

  "non-submitted status" should {
    val emptyProgressResponse = ProgressResponse("1")

    "be true for non submitted progress" in {
      repository.isNonSubmittedStatus(emptyProgressResponse.copy(submitted = false, withdrawn = false)) mustBe true
    }

    "be false for withdrawn progress" in {
      repository.isNonSubmittedStatus(emptyProgressResponse.copy(submitted = true, withdrawn = true)) mustBe false
      repository.isNonSubmittedStatus(emptyProgressResponse.copy(submitted = false, withdrawn = true)) mustBe false
    }

    "be false for submitted but not withdrawn progress" in {
      repository.isNonSubmittedStatus(emptyProgressResponse.copy(submitted = true, withdrawn = false)) mustBe false
    }
  }

  "Get Application to Fix for PassToPhase2 fix" should {
    "return 1 result if the application is in PHASE2_TESTS_INVITED and PHASE1_TESTS_PASSED" in {
      val statuses: Seq[(ProgressStatuses.ProgressStatus, Boolean)] = (ProgressStatuses.PHASE1_TESTS_INVITED, true) ::
        (ProgressStatuses.PHASE1_TESTS_STARTED, true) :: (ProgressStatuses.PHASE1_TESTS_COMPLETED, true) ::
        (ProgressStatuses.PHASE1_TESTS_RESULTS_READY, true) :: (ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED, true) ::
        (ProgressStatuses.PHASE1_TESTS_PASSED, true) :: (ProgressStatuses.PHASE2_TESTS_INVITED, true) :: Nil

      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId", "FastStream-2016", ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = statuses.toList).futureValue

      val matchResponse = repository.getApplicationsToFix(FixBatch(PassToPhase2, 1)).futureValue
      matchResponse.size mustBe 1
    }

    "return 0 result if the application is in PHASE1_TESTS_PASSED but not yet invited to PHASE 2" in {
      val statuses: Seq[(ProgressStatuses.ProgressStatus, Boolean)] = (ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED, true) ::
        (ProgressStatuses.PHASE1_TESTS_PASSED, true) :: Nil

      testDataRepo.createApplicationWithAllFields("userId","appId123", "testAccountId","FastStream-2016", ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = statuses.toList).futureValue

      val matchResponse = repository.getApplicationsToFix(FixBatch(PassToPhase2, 1)).futureValue
      matchResponse.size mustBe 0
    }

    "return 0 result if the application is in PHASE2_TESTS_INVITED and PHASE1_TESTS_PASSED but already in PHASE 2" in {
      val statuses: Seq[(ProgressStatuses.ProgressStatus, Boolean)] = (ProgressStatuses.PHASE1_TESTS_INVITED, true) ::
        (ProgressStatuses.PHASE1_TESTS_STARTED, true) :: (ProgressStatuses.PHASE1_TESTS_COMPLETED, true) ::
        (ProgressStatuses.PHASE1_TESTS_RESULTS_READY, true) :: (ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED, true) ::
        (ProgressStatuses.PHASE1_TESTS_PASSED, true) :: (ProgressStatuses.PHASE1_TESTS_PASSED, true) :: Nil

      testDataRepo.createApplicationWithAllFields("userId","appId123", "testAccountId","FastStream-2016", ApplicationStatus.PHASE2_TESTS,
        additionalProgressStatuses = statuses.toList).futureValue

      val matchResponse = repository.getApplicationsToFix(FixBatch(PassToPhase2, 1)).futureValue
      matchResponse.size mustBe 0
    }

    "return no result if the application is in PHASE2_TESTS_INVITED but not PHASE1_TESTS_PASSED" in {
      // This would be an inconsistent state and we don't want to make things worse.
      val statuses: Seq[(ProgressStatuses.ProgressStatus, Boolean)] = (ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED, true) ::
        (ProgressStatuses.PHASE2_TESTS_INVITED, true) :: Nil

      testDataRepo.createApplicationWithAllFields("userId","appId123", "testAccountId","FastStream-2016", ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = statuses.toList).futureValue

      val matchResponse = repository.getApplicationsToFix(FixBatch(PassToPhase2, 1)).futureValue
      matchResponse.size mustBe 0
    }
  }

  "Get Application to Fix for ResetPhase1TestInvitedSubmitted fix" should {
    "return 1 result if the application is in PHASE1_TESTS_INVITED and SUBMITTED" in {
      val statuses = (ProgressStatuses.SUBMITTED, true) ::
        (ProgressStatuses.PHASE1_TESTS_INVITED, true) :: Nil

      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId","FastStream-2016", ApplicationStatus.SUBMITTED,
        additionalProgressStatuses = statuses).futureValue

      val matchResponse = repository.getApplicationsToFix(FixBatch(ResetPhase1TestInvitedSubmitted, 1)).futureValue
      matchResponse.size mustBe 1

    }

    "return 0 results if the application is in PHASE1_TESTS_INVITED and SUBMITTED" in {
      val statuses = (ProgressStatuses.SUBMITTED, true) ::
        (ProgressStatuses.PHASE1_TESTS_INVITED, true) :: Nil

      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId","FastStream-2016", ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = statuses).futureValue

      val matchResponse = repository.getApplicationsToFix(FixBatch(ResetPhase1TestInvitedSubmitted, 1)).futureValue
      matchResponse.size mustBe 0

    }
  }

  "fix a PassToPhase2 issue" should {
    "update the application status from PHASE1_TESTS to PHASE2_TESTS" in {
      import ProgressStatuses._
      val statuses = List(SUBMITTED, PHASE1_TESTS_INVITED, PHASE1_TESTS_STARTED, PHASE1_TESTS_COMPLETED,
        PHASE1_TESTS_RESULTS_READY, PHASE1_TESTS_RESULTS_RECEIVED, PHASE1_TESTS_PASSED, PHASE2_TESTS_INVITED)
        .map(_ -> true)

      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId", "FastStream-2016", ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = statuses).futureValue

      val matchResponse = repository.fix(candidate, FixBatch(PassToPhase2, 1)).futureValue
      matchResponse.isDefined mustBe true

      val applicationResponse = repository.findByUserId("userId", "FastStream-2016").futureValue
      applicationResponse.userId mustBe "userId"
      applicationResponse.applicationId mustBe "appId123"
      applicationResponse.applicationStatus mustBe ApplicationStatus.PHASE2_TESTS.toString
    }

    "perform no update if, in the meanwhile, the pre-conditions of the update have changed" in {
      // no "PHASE2_TESTS_INVITED" -> true (which is a pre-condition)
      import ProgressStatuses._
      val statuses = List(SUBMITTED, PHASE1_TESTS_INVITED, PHASE1_TESTS_STARTED, PHASE1_TESTS_COMPLETED,
        PHASE1_TESTS_RESULTS_READY, PHASE1_TESTS_RESULTS_RECEIVED, PHASE1_TESTS_PASSED)
        .map(_ -> true)

      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId", "FastStream-2016", ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = statuses).futureValue

      // TODO: mongo this has changed:
      val matchResponse = repository.fix(candidate, FixBatch(PassToPhase2, 1)).futureValue
      //      matchResponse.isDefined mustBe false

      val applicationResponse = repository.findByUserId("userId", "FastStream-2016").futureValue
      applicationResponse.userId mustBe "userId"
      applicationResponse.applicationId mustBe "appId123"
      applicationResponse.applicationStatus mustBe ApplicationStatus.PHASE1_TESTS.toString
    }
  }

  "findAdjustments" should {
    "return None if assistance-details does not exist" in {
      val result = repository.create("userId", "frameworkId", ApplicationRoute.Faststream).futureValue
      repository.findAdjustments(result.applicationId).futureValue mustBe None
    }

    "save and fetch adjustments" in {
      val result = repository.create("userId", "frameworkId", ApplicationRoute.Faststream).futureValue

      val adjustments = Adjustments(
        adjustments = Some(List("Test adjustment")),
        adjustmentsConfirmed = Some(true),
        etray = Some(AdjustmentDetail(timeNeeded = Some(10), percentage = Some(10))),
        video = Some(AdjustmentDetail(timeNeeded = Some(10), percentage = Some(10)))
      )
      repository.confirmAdjustments(result.applicationId, adjustments).futureValue
      repository.findAdjustments(result.applicationId).futureValue mustBe Some(adjustments)
    }

    "save adjustments twice and then fetch them" in {
      val result = repository.create("userId", "frameworkId", ApplicationRoute.Faststream).futureValue

      val adjustments = Adjustments(
        adjustments = Some(List("Test adjustment")),
        adjustmentsConfirmed = Some(true),
        etray = Some(AdjustmentDetail(timeNeeded = Some(20), percentage = Some(20))),
        video = Some(AdjustmentDetail(timeNeeded = Some(20), percentage = Some(20)))
      )
      repository.confirmAdjustments(result.applicationId, adjustments).futureValue

      val updatedAdjustments = adjustments.copy(
        etray = None,
        video = None
      )
      repository.confirmAdjustments(result.applicationId, updatedAdjustments).futureValue
      repository.findAdjustments(result.applicationId).futureValue mustBe Some(updatedAdjustments)
    }
  }

  "findTestForNotification" should {
    "find an edip candidate that needs to be notified of successful phase1 test results" in new NewApplication {
      val appStatus = ApplicationStatus.PHASE1_TESTS_PASSED
      val appRoute = Some(ApplicationRoute.Edip)
      createApplication()

      val notification = repository.findTestForNotification(Phase1SuccessTestType).futureValue
      notification mustBe Some(TestResultNotification(AppId, UserId, testDataRepo.testCandidate("preferredName")))
    }

    "find an sdip candidate that needs to be notified of successful phase1 test results" in new NewApplication {
      val appStatus = ApplicationStatus.PHASE1_TESTS_PASSED
      val appRoute = Some(ApplicationRoute.Sdip)
      createApplication()

      val notification = repository.findTestForNotification(Phase1SuccessTestType).futureValue
      notification mustBe Some(TestResultNotification(AppId, UserId, testDataRepo.testCandidate("preferredName")))
    }

    "find nothing if only faststream candidates passed phase1 test" in new NewApplication {
      val appStatus = ApplicationStatus.PHASE1_TESTS_PASSED
      val appRoute = Some(ApplicationRoute.Faststream)
      createApplication()

      val notification = repository.findTestForNotification(Phase1SuccessTestType).futureValue
      notification mustBe empty
    }

    "find a candidate that needs to be notified of successful phase3 test results" in new NewApplication {
      val appStatus = ApplicationStatus.PHASE3_TESTS_PASSED
      val appRoute = Some(ApplicationRoute.Faststream)
      createApplication()

      val applicationResponse = repository.findTestForNotification(Phase3SuccessTestType).futureValue
      applicationResponse mustBe Some(TestResultNotification(AppId, UserId, testDataRepo.testCandidate("preferredName")))
    }

    "do NOT find a edip candidate that has already been notified of successful phase1 test results" in {
      val progressStatuses = (ProgressStatuses.PHASE1_TESTS_PASSED_NOTIFIED, true) :: Nil

      testDataRepo.createApplicationWithAllFields(UserId, AppId, TestAccountId, FrameworkId, appStatus = ApplicationStatus.PHASE1_TESTS_PASSED,
        additionalProgressStatuses = progressStatuses, applicationRoute = Some(ApplicationRoute.Edip)).futureValue
      val applicationResponse = repository.findTestForNotification(Phase1SuccessTestType).futureValue
      applicationResponse mustBe None
    }

    "do NOT find a sdip candidate that has already been notified of successful phase1 test results" in {
      val progressStatuses = (ProgressStatuses.PHASE1_TESTS_PASSED_NOTIFIED, true) :: Nil

      testDataRepo.createApplicationWithAllFields(UserId, AppId, TestAccountId, FrameworkId, appStatus = ApplicationStatus.PHASE1_TESTS_PASSED,
        additionalProgressStatuses = progressStatuses, applicationRoute = Some(ApplicationRoute.Sdip)).futureValue
      val applicationResponse = repository.findTestForNotification(Phase1SuccessTestType).futureValue
      applicationResponse mustBe None
    }

    "find a candidate that needs to be notified of failed phase1 test results" in {
      val progressStatuses = (PHASE1_TESTS_RESULTS_RECEIVED, true) :: Nil

      testDataRepo.createApplicationWithAllFields(UserId, AppId, TestAccountId, FrameworkId, appStatus = ApplicationStatus.PHASE1_TESTS_FAILED,
        additionalProgressStatuses = progressStatuses).futureValue
      val applicationResponse = repository.findTestForNotification(Phase1FailedTestType).futureValue
      applicationResponse mustBe Some(TestResultNotification(AppId, UserId, testDataRepo.testCandidate("preferredName")))
    }

    "find a candidate that needs to be notified of failed phase2 test results" in {
      val progressStatuses = (PHASE2_TESTS_RESULTS_RECEIVED, true) :: Nil

      testDataRepo.createApplicationWithAllFields(UserId, AppId, TestAccountId, FrameworkId, appStatus = ApplicationStatus.PHASE2_TESTS_FAILED,
        additionalProgressStatuses = progressStatuses).futureValue

      val applicationResponse = repository.findTestForNotification(Phase2FailedTestType).futureValue
      applicationResponse mustBe Some(TestResultNotification(AppId, UserId, testDataRepo.testCandidate("preferredName")))
    }
    "find a candidate that needs to be notified of failed phase3 test results" in {
      val progressStatuses = (PHASE3_TESTS_RESULTS_RECEIVED, true) :: Nil

      testDataRepo.createApplicationWithAllFields(UserId, AppId, TestAccountId, FrameworkId, appStatus = ApplicationStatus.PHASE3_TESTS_FAILED,
        additionalProgressStatuses = progressStatuses).futureValue

      val applicationResponse = repository.findTestForNotification(Phase3FailedTestType).futureValue
      applicationResponse mustBe Some(TestResultNotification(AppId, UserId, testDataRepo.testCandidate("preferredName")))
    }

    "do NOT find a candidate that has already been notified of successful phase3 test results" in {
      val progressStatuses = (ProgressStatuses.PHASE3_TESTS_PASSED_NOTIFIED, true) :: Nil

      testDataRepo.createApplicationWithAllFields(UserId, AppId, TestAccountId, FrameworkId, appStatus = ApplicationStatus.PHASE3_TESTS_PASSED,
        additionalProgressStatuses = progressStatuses).futureValue
      val applicationResponse = repository.findTestForNotification(Phase3SuccessTestType).futureValue
      applicationResponse mustBe None
    }

    "do NOT find a candidate that has already been notified of failed phase1 test results" in {
      val progressStatuses = (PHASE1_TESTS_FAILED_NOTIFIED, true) :: Nil

      testDataRepo.createApplicationWithAllFields(UserId, AppId, TestAccountId, FrameworkId, appStatus = ApplicationStatus.PHASE1_TESTS_FAILED,
        additionalProgressStatuses = progressStatuses).futureValue

      val applicationResponse = repository.findTestForNotification(Phase1FailedTestType).futureValue
      applicationResponse mustBe None
    }

    "do NOT find a candidate that has already been notified of failed phase2 test results" in {
      val progressStatuses = (PHASE2_TESTS_FAILED_NOTIFIED, true) :: Nil

      testDataRepo.createApplicationWithAllFields(UserId, AppId, TestAccountId, FrameworkId, appStatus = ApplicationStatus.PHASE2_TESTS_FAILED,
        additionalProgressStatuses = progressStatuses).futureValue

      val applicationResponse = repository.findTestForNotification(Phase2FailedTestType).futureValue
      applicationResponse mustBe None
    }

    "do NOT find a candidate that has already been notified of failed phase3 test results" in {
      val progressStatuses = (PHASE3_TESTS_FAILED_NOTIFIED, true) :: Nil

      testDataRepo.createApplicationWithAllFields(UserId, AppId, TestAccountId, FrameworkId, appStatus = ApplicationStatus.PHASE3_TESTS_FAILED,
        additionalProgressStatuses = progressStatuses).futureValue

      val applicationResponse = repository.findTestForNotification(Phase3FailedTestType).futureValue
      applicationResponse mustBe None
    }

    abstract class NewApplication {
      val appStatus: ApplicationStatus
      val appRoute: Option[ApplicationRoute]

      def createApplication() = {
        testDataRepo.createApplicationWithAllFields(UserId, AppId, TestAccountId, FrameworkId, appStatus,
          applicationRoute = appRoute).futureValue
      }
    }
  }

  "Update application route" should {
    "return cannot update if the application route is not Faststream" in {
      testDataRepo.createApplicationWithAllFields(UserId, AppId, TestAccountId, FrameworkId,
        applicationRoute = Some(ApplicationRoute.Edip)).futureValue

      val result = repository.updateApplicationRoute(AppId, ApplicationRoute.Faststream, ApplicationRoute.SdipFaststream).failed.futureValue
      result mustBe a[Exceptions.CannotUpdateRecord]
    }

    "update the Faststream application when application route is Faststream" in {
      testDataRepo.createApplicationWithAllFields(UserId, AppId, TestAccountId, FrameworkId,
        applicationRoute = Some(ApplicationRoute.Faststream)).futureValue

      repository.updateApplicationRoute(AppId, ApplicationRoute.Faststream, ApplicationRoute.SdipFaststream).futureValue

      val applicationResponse = repository.findByUserId(UserId, FrameworkId).futureValue
      applicationResponse.applicationRoute mustBe ApplicationRoute.SdipFaststream
    }

    "update the application without application route" in {
      testDataRepo.createApplicationWithAllFields(UserId, AppId, TestAccountId, FrameworkId).futureValue

      repository.updateApplicationRoute(AppId, ApplicationRoute.Faststream, ApplicationRoute.SdipFaststream).futureValue

      val applicationResponse = repository.findByUserId(UserId, FrameworkId).futureValue
      applicationResponse.applicationRoute mustBe ApplicationRoute.SdipFaststream
    }
  }

  "Archive" should {
    "archive the existing application" in {
      testDataRepo.createApplicationWithAllFields(UserId, AppId, TestAccountId, FrameworkId,
        applicationRoute = Some(ApplicationRoute.Faststream)
      ).futureValue

      val userIdToArchiveWith = "newUserId"
      repository.archive(AppId, UserId, userIdToArchiveWith, FrameworkId, ApplicationRoute.Faststream).futureValue

      val archivedApplication = repository.findByUserId(userIdToArchiveWith, FrameworkId).futureValue
      archivedApplication.applicationRoute mustBe ApplicationRoute.Faststream
      archivedApplication.applicationId mustBe AppId
      archivedApplication.userId mustBe userIdToArchiveWith
      archivedApplication.progressResponse.applicationArchived mustBe true
      archivedApplication.applicationStatus mustBe ApplicationStatus.ARCHIVED.toString

      an[ApplicationNotFound] must be thrownBy Await.result(repository.findByUserId(UserId, FrameworkId), timeout)
    }

    "archive the existing application when application route is absent" in {
      testDataRepo.createApplicationWithAllFields(UserId, AppId, TestAccountId, FrameworkId).futureValue

      val userIdToArchiveWith = "newUserId"
      repository.archive(AppId, UserId, userIdToArchiveWith, FrameworkId, ApplicationRoute.Faststream).futureValue

      val archivedApplication = repository.findByUserId(userIdToArchiveWith, FrameworkId).futureValue
      archivedApplication.applicationRoute mustBe ApplicationRoute.Faststream
      archivedApplication.applicationId mustBe AppId
      archivedApplication.userId mustBe userIdToArchiveWith
      archivedApplication.progressResponse.applicationArchived mustBe true
      archivedApplication.applicationStatus mustBe ApplicationStatus.ARCHIVED.toString

      an[ApplicationNotFound] must be thrownBy Await.result(repository.findByUserId(UserId, FrameworkId), timeout)
    }

    "return not found when application route is not faststream" in {
      testDataRepo.createApplicationWithAllFields(UserId, AppId, TestAccountId, FrameworkId,
        applicationRoute = Some(ApplicationRoute.Edip)).futureValue
      val userIdToArchiveWith = "newUserId"

      an[CannotUpdateRecord] must be thrownBy Await.result(repository.archive(AppId, UserId, userIdToArchiveWith,
        FrameworkId, ApplicationRoute.Faststream), timeout)
    }
  }

  "Remove video interview failed" should {
    "Remove evaluation section, progress failed statuses and update application status" in {
      import ProgressStatuses._
      val progressStatuses = (PHASE3_TESTS_RESULTS_RECEIVED, true) :: (PHASE3_TESTS_FAILED, true) ::
        (PHASE3_TESTS_FAILED_NOTIFIED, true) :: Nil
      testDataRepo.createApplicationWithAllFields(
        UserId, AppId, TestAccountId, FrameworkId, appStatus = ApplicationStatus.PHASE3_TESTS_FAILED,
        additionalProgressStatuses = progressStatuses, additionalDoc = phase3TestGroup).futureValue

      repository.fixDataByRemovingVideoInterviewFailed(AppId).futureValue

      val applicationResponse = repository.findByUserId(UserId, FrameworkId).futureValue
      applicationResponse.applicationStatus mustBe ApplicationStatus.PHASE3_TESTS.toString
      applicationResponse.progressResponse.phase3ProgressResponse.phase3TestsResultsReceived mustBe true
      applicationResponse.progressResponse.phase3ProgressResponse.phase3TestsFailed mustBe false
      applicationResponse.progressResponse.phase3ProgressResponse.phase3TestsFailedNotified mustBe false
    }
  }

  private def findFsacCandidatesCall = repository.findCandidatesEligibleForEventAllocation(
    List("London"), EventType.FSAC, schemeId = None
  ).futureValue

  private def findFsbCandidatesCall(scheme: SchemeId) = {
    repository.findCandidatesEligibleForEventAllocation(List("London"), EventType.FSB, Some(scheme)).futureValue
  }

  "Find candidates eligible for event allocation" should {
    "return an empty list when there are no FSAC applications" in {
      createUnAllocatedFSACApplications(0).futureValue
      val result = findFsacCandidatesCall
      result mustBe a[CandidatesEligibleForEventResponse]
      result.candidates mustBe empty
    }

    "return an empty list when there are no FSAC eligible candidates" in {
      testDataRepo.createApplications(10).futureValue
      findFsacCandidatesCall.candidates mustBe empty
    }

    "return a ten item list when there are FSAC eligible candidates" in {
      createUnAllocatedFSACApplications(10).futureValue
      findFsacCandidatesCall.candidates must have size 10
    }

    "return an empty item when all schemes are red" in {
      createUnAllocatedFSBApplications(1,
        List(
          SchemeEvaluationResult("HumanResources", Red.toString),
          SchemeEvaluationResult("DigitalDataTechnologyAndCyber", Red.toString)
        )).futureValue
      findFsbCandidatesCall(SchemeId("DigitalDataTechnologyAndCyber")).candidates mustBe empty
    }

    "return an empty item when there are no FSB eligible candidates for first residual preference" in {
      createUnAllocatedFSBApplications(1,
        List(
          SchemeEvaluationResult("HumanResources", Green.toString),
          SchemeEvaluationResult("DigitalDataTechnologyAndCyber", Green.toString)
        )).futureValue
      findFsbCandidatesCall(SchemeId("DigitalDataTechnologyAndCyber")).candidates mustBe empty
    }

    "return an item when there are FSB eligible candidates" in {
      createUnAllocatedFSBApplications(1,
        List(
          SchemeEvaluationResult("HumanResources", Red.toString),
          SchemeEvaluationResult("DigitalDataTechnologyAndCyber", Green.toString)
        )).futureValue

      findFsbCandidatesCall(SchemeId("DigitalDataTechnologyAndCyber")).candidates must have size 1
    }
  }

  "find allocated applications" should {
    "return eligible candidates" in {
      createUnAllocatedFSACApplications(1).futureValue
      val candidate = findFsacCandidatesCall.candidates.head

      val result = repository.findAllocatedApplications(List(candidate.applicationId)).futureValue
      result.candidates.size mustBe 1
    }
  }

  "reset application status" should {
    "set progress status to awaiting allocation" in {
      createUnAllocatedFSACApplications(10).futureValue
      val unallocatedCandidates = findFsacCandidatesCall.candidates
      unallocatedCandidates.size mustBe 10

      val (candidatesToAllocate, _) = unallocatedCandidates.splitAt(4)

      // allocate 4 candidates
      candidatesToAllocate.foreach { candidate =>
        repository.addProgressStatusAndUpdateAppStatus(
          candidate.applicationId,
          ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED).futureValue
      }
      findFsacCandidatesCall.candidates.size mustBe 6

      // reset the allocated candidates
      val result = candidatesToAllocate.map(_.applicationId).foreach {
        appId => repository.resetApplicationAllocationStatus(appId, EventType.FSAC).futureValue
      }
      result mustBe unit

      val eligibleCandidatesAfterReset = findFsacCandidatesCall.candidates
      eligibleCandidatesAfterReset.size mustBe 10
    }
  }

  "get current scheme status" should {
    "return an empty list if the candidate does not exist" in {
      val result = repository.getCurrentSchemeStatus("appId").futureValue
      result mustBe Nil
    }

    "return an empty list if the candidate has no css" in {
      val newCandidate = repository.create("userId", "frameworkId", ApplicationRoute.Faststream).futureValue
      val result = repository.getCurrentSchemeStatus(newCandidate.applicationId).futureValue
      result mustBe Nil
    }

    "return css when the candidate has one" in {
      val newCandidate = repository.create("userId", "frameworkId", ApplicationRoute.Faststream).futureValue

      val css = Seq(SchemeEvaluationResult("Commercial", Green.toString))
      repository.updateCurrentSchemeStatus(newCandidate.applicationId, css).futureValue

      val result = repository.getCurrentSchemeStatus(newCandidate.applicationId).futureValue
      result mustBe css
    }
  }

  "remove current scheme status" should {
    "throw an exception if the application does not exist" in {
      val result = repository.removeCurrentSchemeStatus("appId").failed.futureValue
      result mustBe a[CannotUpdateRecord]
    }

    "remove the css" in {
      val newCandidate = repository.create("userId", "frameworkId", ApplicationRoute.Faststream).futureValue

      val css = Seq(SchemeEvaluationResult("Commercial", Green.toString))
      repository.updateCurrentSchemeStatus(newCandidate.applicationId, css).futureValue

      val result = repository.getCurrentSchemeStatus(newCandidate.applicationId).futureValue
      result mustBe css

      repository.removeCurrentSchemeStatus(newCandidate.applicationId).futureValue
      repository.getCurrentSchemeStatus(newCandidate.applicationId).futureValue mustBe Nil
    }
  }

  def findWithdrawReasonForScheme(applicationId: String, scheme: SchemeId) = {
    applicationCollection.find[BsonDocument](Document("applicationId" -> applicationId))
      .projection(Projections.include("withdraw")).headOption().map {
      _.flatMap { doc =>
        Try(doc.get("withdraw").asDocument().get("schemes").asDocument().get(scheme.toString).asString().getValue).toOption
      }
    }
  }

  "withdraw scheme" should {
    "add the withdraw section to the application" in {
      val newCandidate = repository.create("userId", "frameworkId", ApplicationRoute.Faststream).futureValue

      val css = Seq(SchemeEvaluationResult("Commercial", Green.toString), SchemeEvaluationResult("Generalist", Green.toString))
      repository.updateCurrentSchemeStatus(newCandidate.applicationId, css).futureValue

      val result = repository.getCurrentSchemeStatus(newCandidate.applicationId).futureValue
      result mustBe css

      val commercial = SchemeId("Commercial")
      repository.withdrawScheme(
        newCandidate.applicationId, WithdrawScheme(commercial, reason = "test withdraw reason", withdrawer = "test withdrawer"),
        Seq(SchemeEvaluationResult(commercial, Green.toString))
      ).futureValue

      findWithdrawReasonForScheme(newCandidate.applicationId, SchemeId("Generalist")).futureValue mustBe None
      findWithdrawReasonForScheme(newCandidate.applicationId, commercial).futureValue mustBe Some("test withdraw reason")
    }
  }

  /**
    * Handle this json:
    *
    * "progress-status" : {
    *   "questionnaire" : {
    *     "section" : true
    *   }
    * }
    */
  def findQuestionnaireSection(applicationId: String, section: String) = {
    applicationCollection.find[BsonDocument](Document("applicationId" -> applicationId))
      .projection(Projections.include("progress-status")).headOption().map {
      _.flatMap { doc =>
        Try(doc.get("progress-status").asDocument().get("questionnaire").asDocument().get(section).asBoolean().getValue).toOption
      }.getOrElse(false)
    }
  }

  "update questionnaire status" should {
    "store the questionnaire progress status as expected" in {
      val sectionKey = "testSection"
      val newCandidate = repository.create("userId", "frameworkId", ApplicationRoute.Faststream).futureValue
      repository.updateQuestionnaireStatus(newCandidate.applicationId, sectionKey)
      findQuestionnaireSection(newCandidate.applicationId, "missing").futureValue mustBe false
      findQuestionnaireSection(newCandidate.applicationId, sectionKey).futureValue mustBe true
    }
  }

  "update status" should {
    "store the new application status as expected" in {
      val newCandidate = repository.create("userId", "frameworkId", ApplicationRoute.Faststream).futureValue
      val result = repository.find(newCandidate.applicationId).futureValue
      result mustBe defined
      result.get.applicationStatus mustBe Some(ApplicationStatus.CREATED.toString)

      repository.updateStatus(newCandidate.applicationId, ApplicationStatus.IN_PROGRESS).futureValue
      val afterUpdateResult = repository.find(newCandidate.applicationId).futureValue
      afterUpdateResult mustBe defined
      afterUpdateResult.get.applicationStatus mustBe Some(ApplicationStatus.IN_PROGRESS.toString)
    }

    "throw an exception if the application does not exist" in {
      val result = repository.updateStatus(AppId, ApplicationStatus.IN_PROGRESS).failed.futureValue
      result mustBe a[CannotUpdateRecord]
    }
  }

  "update application status only" should {
    "store the new application status as expected" in {
      val newCandidate = repository.create("userId", "frameworkId", ApplicationRoute.Faststream).futureValue
      val result = repository.find(newCandidate.applicationId).futureValue
      result mustBe defined
      result.get.applicationStatus mustBe Some(ApplicationStatus.CREATED.toString)

      repository.updateApplicationStatusOnly(newCandidate.applicationId, ApplicationStatus.IN_PROGRESS).futureValue
      val afterUpdateResult = repository.find(newCandidate.applicationId).futureValue
      afterUpdateResult mustBe defined
      afterUpdateResult.get.applicationStatus mustBe Some(ApplicationStatus.IN_PROGRESS.toString)
    }

    "throw an exception if the application does not exist" in {
      val result = repository.updateApplicationStatusOnly(AppId, ApplicationStatus.IN_PROGRESS).failed.futureValue
      result mustBe a[CannotUpdateRecord]
    }
  }

  "remove candidate" should {
    "remove a candidate as expected" in {
      val newCandidate = repository.create("userId", "frameworkId", ApplicationRoute.Faststream).futureValue
      val result = repository.find(newCandidate.applicationId).futureValue
      result mustBe defined

      repository.removeCandidate(newCandidate.applicationId).futureValue
      val resultAfterDeletion = repository.find(newCandidate.applicationId).futureValue
      resultAfterDeletion mustBe empty
    }

    "throw an exception if the application does not exist" in {
      val result = repository.removeCandidate(AppId).failed.futureValue
      result mustBe a[NotFoundException]
    }
  }

  "find adjustments comment" should {
    "throw an exception if there is no application" in {
      val result = repository.findAdjustmentsComment(AppId).failed.futureValue
      result mustBe an[ApplicationNotFound]
    }

    "throw an exception if there is an application but no assistance-details section" in {
      repository.create("userId", "frameworkId", ApplicationRoute.Faststream).futureValue
      val result = repository.findAdjustmentsComment(AppId).failed.futureValue
      result mustBe an[ApplicationNotFound]
    }

    "throw an exception if there is an application with assistance-details but no comment" in {
      val newCandidate = repository.create("userId", "frameworkId", ApplicationRoute.Faststream).futureValue

      val adjustments = Adjustments(
        adjustments = Some(List("Test adjustment")),
        adjustmentsConfirmed = Some(true),
        etray = Some(AdjustmentDetail(timeNeeded = Some(20), percentage = Some(20))),
        video = Some(AdjustmentDetail(timeNeeded = Some(20), percentage = Some(20)))
      )
      repository.confirmAdjustments(newCandidate.applicationId, adjustments).futureValue

      val result = repository.findAdjustmentsComment(newCandidate.applicationId).failed.futureValue
      result mustBe an[AdjustmentsCommentNotFound]
    }

    "save and fetch the comment" in {
      val newCandidate = repository.create("userId", "frameworkId", ApplicationRoute.Faststream).futureValue
      val comment = AdjustmentsComment("test comment")
      repository.updateAdjustmentsComment(newCandidate.applicationId, comment)
      val result = repository.findAdjustmentsComment(newCandidate.applicationId).futureValue
      result mustBe comment
    }
  }

  "remove adjustments comment" should {
    "throw an exception if we do not match an application document" in {
      val result = repository.removeAdjustmentsComment(AppId).failed.futureValue
      result mustBe an[CannotRemoveAdjustmentsComment]
    }

    "report success if there is no stored comment when we attempt to remove one but we do match an application document" in {
      val newCandidate = repository.create("userId", "frameworkId", ApplicationRoute.Faststream).futureValue
      val result = repository.removeAdjustmentsComment(newCandidate.applicationId).futureValue
      result mustBe unit
    }

    "remove the comment" in {
      val newCandidate = repository.create("userId", "frameworkId", ApplicationRoute.Faststream).futureValue
      val comment = AdjustmentsComment("test comment")
      repository.updateAdjustmentsComment(newCandidate.applicationId, comment)
      val result = repository.findAdjustmentsComment(newCandidate.applicationId).futureValue
      result mustBe comment

      repository.removeAdjustmentsComment(newCandidate.applicationId).futureValue
      val removalResult = repository.findAdjustmentsComment(newCandidate.applicationId).failed.futureValue
      removalResult mustBe an[AdjustmentsCommentNotFound]
    }
  }

  "get online test application" should {
    "return None if the application does not exist" in {
      repository.getOnlineTestApplication(AppId).futureValue mustBe None
    }

    "return data if a minimum application exists" in {
      createApplications(num = 1, ApplicationStatus.SUBMITTED, additionalProgressStatuses = Nil).futureValue

      val expected = OnlineTestApplication("AppId1", ApplicationStatus.SUBMITTED, "UserId1", "TestAccountId1",
        guaranteedInterview = false, needsOnlineAdjustments = false, needsAtVenueAdjustments = false,
        preferredName = "Georgy", lastName = "Jetson01", eTrayAdjustments = None, videoInterviewAdjustments = None)
      repository.getOnlineTestApplication("AppId1").futureValue mustBe Some(expected)
    }

    "return data if a fully populated application exists" in {
      createApplications(num = 1, ApplicationStatus.SUBMITTED, additionalProgressStatuses = Nil).futureValue

      val adjustments = Adjustments(
        adjustments = Some(List("Test adjustment")),
        adjustmentsConfirmed = Some(true),
        etray = Some(AdjustmentDetail(timeNeeded = Some(10), percentage = Some(20))),
        video = Some(AdjustmentDetail(timeNeeded = Some(30), percentage = Some(40)))
      )

      val appId = "AppId1"
      repository.confirmAdjustments(appId, adjustments).futureValue

      val expected = OnlineTestApplication(appId, ApplicationStatus.SUBMITTED, "UserId1", "TestAccountId1",
        guaranteedInterview = false, needsOnlineAdjustments = false, needsAtVenueAdjustments = false,
        preferredName = "Georgy", lastName = "Jetson01",
        eTrayAdjustments = Some(AdjustmentDetail(timeNeeded = Some(10), percentage = Some(20))),
        videoInterviewAdjustments = Some(AdjustmentDetail(timeNeeded = Some(30), percentage = Some(40)))
      )
      repository.getOnlineTestApplication(appId).futureValue mustBe Some(expected)
    }
  }

  "find next test for sdip faststream notification" should {
    "return None if there are no eligible candidates" in {
      val notificationType = FailedSdipFsTestType
      repository.findTestForSdipFsNotification(notificationType).futureValue mustBe None
    }

    "return an eligible candidate" in {
      val statuses: Seq[(ProgressStatuses.ProgressStatus, Boolean)] = (ProgressStatuses.PHASE1_TESTS_INVITED, true) ::
        (ProgressStatuses.PHASE1_TESTS_STARTED, true) :: (ProgressStatuses.PHASE1_TESTS_COMPLETED, true) ::
        (ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED, true) ::
        (ProgressStatuses.getProgressStatusForSdipFsFailed(ApplicationStatus.PHASE1_TESTS), true) :: Nil

      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId", "FastStream-2016",
        ApplicationStatus.PHASE1_TESTS, additionalProgressStatuses = statuses.toList,
        applicationRoute = Some(ApplicationRoute.SdipFaststream)).futureValue

      val notificationType = FailedSdipFsTestType
      val result = repository.findTestForSdipFsNotification(notificationType).futureValue
      result mustBe Some(TestResultSdipFsNotification("appId123", "userId", PHASE1_TESTS, "Georgy"))
    }
  }

  "get application route" should {
    "throw an exception if there is no application" in {
      val result = repository.getApplicationRoute(AppId).failed.futureValue
      result mustBe an[ApplicationNotFound]
    }

    "return the application route" in {
      testDataRepo.createApplicationWithAllFields("userId", AppId, "testAccountId", "FastStream-2016",
        applicationRoute = Some(ApplicationRoute.SdipFaststream)).futureValue
      repository.getApplicationRoute(AppId).futureValue mustBe ApplicationRoute.SdipFaststream
    }
  }

  "list collections" should {
    "return the collection names" in {
      val collections = repository.listCollections.futureValue
      collections must contain(collectionName)
    }
  }

  "find eligible for job offer candidates with fsb status" should {
    "return an empty collection when there are no eligible candidates" in {
      repository.findEligibleForJobOfferCandidatesWithFsbStatus.futureValue mustBe Nil
    }

    "return eligible candidates" in {
      val statuses: Seq[(ProgressStatuses.ProgressStatus, Boolean)] = (ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER -> true) :: Nil

      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId", "FastStream-2016",
        ApplicationStatus.FSB, additionalProgressStatuses = statuses.toList,
        applicationRoute = Some(ApplicationRoute.SdipFaststream)).futureValue

      repository.findEligibleForJobOfferCandidatesWithFsbStatus.futureValue.size mustBe 1
    }
  }

  "get progress status timestamps" should {
    "return the timestamps" in {
      testDataRepo.createApplicationWithAllFields("userId", AppId, "testAccountId",
        "FastStream-2016", ApplicationStatus.PHASE1_TESTS).futureValue
      val result = repository.getProgressStatusTimestamps(AppId).futureValue
      result.size mustBe 2
      result.head._1 mustBe "IN_PROGRESS"
      result(1)._1 mustBe "SUBMITTED"
    }
  }

  "find sdip faststream invited to video interview" should {
    "return an empty list if there are no candidates" in {
      repository.findSdipFaststreamInvitedToVideoInterview.futureValue mustBe Nil
    }

    "return candidates when they match" in {
      val statuses: Seq[(ProgressStatuses.ProgressStatus, Boolean)] = (ProgressStatuses.PHASE3_TESTS_INVITED -> true) :: Nil

      testDataRepo.createApplicationWithAllFields("userId", AppId, "testAccountId", "FastStream-2016",
        ApplicationStatus.FSB, additionalProgressStatuses = statuses.toList,
        applicationRoute = Some(ApplicationRoute.SdipFaststream)).futureValue

      val result = repository.findSdipFaststreamInvitedToVideoInterview.futureValue
      result.size mustBe 1
      result.head.applicationId mustBe Some(AppId)
    }
  }

  def savePassmarkEvaluation(applicationId: String, phase: String, evaluation: PassmarkEvaluation): Future[Unit] = {
    val filter = Document("applicationId" -> applicationId)
    val update = Document("$set" -> Document(s"testGroups.$phase.evaluation" -> evaluation.toBson))

    applicationCollection.updateOne(filter, update).toFuture() map( _ => () )
  }

  "find sdip faststream phase2 expired invited to sift" should {
    "return an empty list if there are no candidates" in {
      repository.findSdipFaststreamExpiredPhase2InvitedToSift.futureValue mustBe Nil
    }

    "return candidates when they match" in {
      val statuses: Seq[(ProgressStatuses.ProgressStatus, Boolean)] = (ProgressStatuses.PHASE2_TESTS_EXPIRED -> true) :: Nil

      testDataRepo.createApplicationWithAllFields("userId", AppId, "testAccountId", "FastStream-2016",
        ApplicationStatus.SIFT, additionalProgressStatuses = statuses.toList,
        applicationRoute = Some(ApplicationRoute.SdipFaststream)).futureValue

      val resultToSave = List(SchemeEvaluationResult(SchemeId("Sdip"), Green.toString))
      val phase1Evaluation = PassmarkEvaluation(
        "phase1_version2", Some("phase1_version1"), resultToSave, "phase1-version1-res", previousPhaseResultVersion = None
      )
      savePassmarkEvaluation(AppId, "PHASE1", phase1Evaluation).futureValue

      val result = repository.findSdipFaststreamExpiredPhase2InvitedToSift.futureValue
      result.size mustBe 1
      result.head.applicationId mustBe Some(AppId)
    }
  }

  "find sdip faststream phase3 expired invited to sift" should {
    "return an empty list if there are no candidates" in {
      repository.findSdipFaststreamExpiredPhase3InvitedToSift.futureValue mustBe Nil
    }

    "return candidates when they match" in {
      val statuses: Seq[(ProgressStatuses.ProgressStatus, Boolean)] = (ProgressStatuses.PHASE3_TESTS_EXPIRED -> true) :: Nil

      testDataRepo.createApplicationWithAllFields("userId", AppId, "testAccountId", "FastStream-2016",
        ApplicationStatus.SIFT, additionalProgressStatuses = statuses.toList,
        applicationRoute = Some(ApplicationRoute.SdipFaststream)).futureValue

      val resultToSave = List(SchemeEvaluationResult(SchemeId("Sdip"), Green.toString))
      val phase1Evaluation = PassmarkEvaluation(
        "phase2_version2", Some("phase2_version1"), resultToSave, "phase2-version1-res", previousPhaseResultVersion = None
      )
      savePassmarkEvaluation(AppId, "PHASE2", phase1Evaluation).futureValue

      val result = repository.findSdipFaststreamExpiredPhase3InvitedToSift.futureValue
      result.size mustBe 1
      result.head.applicationId mustBe Some(AppId)
    }
  }

  "fix data by removing etray" should {
    "fix the data" in {
      val statuses: Seq[(ProgressStatuses.ProgressStatus, Boolean)] = (ProgressStatuses.PHASE1_TESTS_INVITED, true) ::
        (ProgressStatuses.PHASE1_TESTS_STARTED, true) :: (ProgressStatuses.PHASE1_TESTS_COMPLETED, true) ::
        (ProgressStatuses.PHASE1_TESTS_RESULTS_READY, true) :: (ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED, true) ::
        (ProgressStatuses.PHASE1_TESTS_PASSED, true) :: (ProgressStatuses.PHASE2_TESTS_INVITED, true) ::
        (ProgressStatuses.PHASE2_TESTS_STARTED, true) :: (ProgressStatuses.PHASE2_TESTS_FIRST_REMINDER, true) ::
        (ProgressStatuses.PHASE2_TESTS_SECOND_REMINDER, true) :: (ProgressStatuses.PHASE2_TESTS_EXPIRED, true) :: Nil

      testDataRepo.createApplicationWithAllFields("userId", AppId, "testAccountId",
        "FastStream-2016", ApplicationStatus.PHASE2_TESTS,
        additionalProgressStatuses = statuses.toList).futureValue

      repository.fixDataByRemovingETray(AppId).futureValue
      val progressResponse = repository.findProgress(AppId).futureValue
      progressResponse.phase2ProgressResponse.phase2TestsInvited mustBe false
      progressResponse.phase2ProgressResponse.phase2TestsStarted mustBe false
      progressResponse.phase2ProgressResponse.phase2TestsFirstReminder mustBe false
      progressResponse.phase2ProgressResponse.phase2TestsSecondReminder mustBe false
      progressResponse.phase2ProgressResponse.phase2TestsExpired mustBe false

      val ss = repository.getApplicationStatusForCandidates(Seq(AppId))
        .futureValue mustBe Seq(AppId -> ApplicationStatus.PHASE1_TESTS_PASSED)
    }
  }

  "fix data by removing progress status" should {
    "fix the data" in {
      val statuses: Seq[(ProgressStatuses.ProgressStatus, Boolean)] = (ProgressStatuses.PHASE1_TESTS_INVITED, true) ::
        (ProgressStatuses.PHASE1_TESTS_STARTED, true) :: (ProgressStatuses.PHASE1_TESTS_COMPLETED, true) ::
        (ProgressStatuses.PHASE1_TESTS_RESULTS_READY, true) :: (ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED, true) ::
        (ProgressStatuses.PHASE1_TESTS_PASSED, true) :: Nil

      testDataRepo.createApplicationWithAllFields("userId", AppId, "testAccountId",
        "FastStream-2016", ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = statuses.toList).futureValue

      val progressResponse = repository.findProgress(AppId).futureValue
      progressResponse.phase1ProgressResponse.phase1TestsPassed mustBe true

      repository.fixDataByRemovingETray(AppId).futureValue
      repository.fixDataByRemovingProgressStatus(AppId, ProgressStatuses.PHASE1_TESTS_PASSED.toString).futureValue

      val progressResponseAfterDeletion = repository.findProgress(AppId).futureValue
      progressResponseAfterDeletion.phase1ProgressResponse.phase1TestsPassed mustBe false
    }
  }


  "set failed to attend assessment status" should {
    "update the data for fsac candidate" in {
      val statuses: Seq[(ProgressStatuses.ProgressStatus, Boolean)] =
        (ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION, true) ::
        (ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_UNCONFIRMED, true) ::
        (ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_CONFIRMED, true) :: Nil

      testDataRepo.createApplicationWithAllFields("userId", AppId, "testAccountId",
        "FastStream-2016", ApplicationStatus.ASSESSMENT_CENTRE,
        additionalProgressStatuses = statuses.toList).futureValue

      val progressResponse = repository.findProgress(AppId).futureValue
      progressResponse.assessmentCentre.awaitingAllocation mustBe true
      progressResponse.assessmentCentre.allocationUnconfirmed mustBe true
      progressResponse.assessmentCentre.allocationConfirmed mustBe true
      progressResponse.assessmentCentre.failedToAttend mustBe false

      repository.setFailedToAttendAssessmentStatus(AppId, EventType.FSAC).futureValue

      val progressResponseAfterUpdate = repository.findProgress(AppId).futureValue
      progressResponseAfterUpdate.assessmentCentre.awaitingAllocation mustBe false
      progressResponseAfterUpdate.assessmentCentre.allocationUnconfirmed mustBe false
      progressResponseAfterUpdate.assessmentCentre.allocationConfirmed mustBe false
      progressResponseAfterUpdate.assessmentCentre.failedToAttend mustBe true
    }

    "update the data for fsb candidate" in {
      val statuses: Seq[(ProgressStatuses.ProgressStatus, Boolean)] =
        (ProgressStatuses.FSB_AWAITING_ALLOCATION, true) ::
        (ProgressStatuses.FSB_ALLOCATION_UNCONFIRMED, true) ::
        (ProgressStatuses.FSB_ALLOCATION_CONFIRMED, true) :: Nil

      testDataRepo.createApplicationWithAllFields("userId", AppId, "testAccountId",
        "FastStream-2016", ApplicationStatus.FSB,
        additionalProgressStatuses = statuses.toList).futureValue

      val progressResponse = repository.findProgress(AppId).futureValue
      progressResponse.fsb.awaitingAllocation mustBe true
      progressResponse.fsb.allocationUnconfirmed mustBe true
      progressResponse.fsb.allocationConfirmed mustBe true
      progressResponse.fsb.failedToAttend mustBe false

      repository.setFailedToAttendAssessmentStatus(AppId, EventType.FSB).futureValue

      val progressResponseAfterUpdate = repository.findProgress(AppId).futureValue
      progressResponseAfterUpdate.fsb.awaitingAllocation mustBe false
      progressResponseAfterUpdate.fsb.allocationUnconfirmed mustBe false
      progressResponseAfterUpdate.fsb.allocationConfirmed mustBe false
      progressResponseAfterUpdate.fsb.failedToAttend mustBe true
    }
  }

  "find all file info" should {
    "handle no eligible candidates" in {
      testDataRepo.createApplicationWithAllFields("userId", AppId, "testAccountId", "FastStream-2016",
        applicationRoute = Some(ApplicationRoute.Faststream)).futureValue
      repository.findAllFileInfo.futureValue mustBe Nil
    }

    "handle eligible candidates" in {
      testDataRepo.createApplicationWithAllFields("userId", AppId, "testAccountId", "FastStream-2016",
        applicationRoute = Some(ApplicationRoute.Faststream)).futureValue

      assessmentCentreRepo.updateTests(AppId, AssessmentCentreTests(Some(AnalysisExercise("testFileId")))).futureValue
      repository.findAllFileInfo.futureValue mustBe List(CandidateFileInfo(AppId, "testFileId"))
    }
  }

  "remove progress statuses" should {
    "throw an exception if there is no matching application" in {
      val result = repository.removeProgressStatuses(AppId, List(ProgressStatuses.SUBMITTED)).failed.futureValue
      result mustBe a[CannotUpdateRecord]
    }

    "remove the expected progress statuses" in {
      val statuses: Seq[(ProgressStatuses.ProgressStatus, Boolean)] = (ProgressStatuses.PHASE1_TESTS_INVITED, true) ::
        (ProgressStatuses.PHASE1_TESTS_STARTED, true) :: (ProgressStatuses.PHASE1_TESTS_COMPLETED, true) ::
        (ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED, true) ::
        (ProgressStatuses.PHASE1_TESTS_PASSED, true) :: Nil

      testDataRepo.createApplicationWithAllFields("userId", AppId, "testAccountId",
        "FastStream-2016", ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = statuses.toList).futureValue

      val progressResponse = repository.findProgress(AppId).futureValue
      progressResponse.phase1ProgressResponse.phase1TestsInvited mustBe true
      progressResponse.phase1ProgressResponse.phase1TestsStarted mustBe true
      progressResponse.phase1ProgressResponse.phase1TestsCompleted mustBe true
      progressResponse.phase1ProgressResponse.phase1TestsResultsReceived mustBe true
      progressResponse.phase1ProgressResponse.phase1TestsPassed mustBe true

      val progressStatuses = List(
        ProgressStatuses.PHASE1_TESTS_STARTED,
        ProgressStatuses.PHASE1_TESTS_COMPLETED,
        ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED,
        ProgressStatuses.PHASE1_TESTS_PASSED
      )
      repository.removeProgressStatuses(AppId, progressStatuses).futureValue

      val progressResponseAfterUpdate = repository.findProgress(AppId).futureValue
      progressResponseAfterUpdate.phase1ProgressResponse.phase1TestsInvited mustBe true
      progressResponseAfterUpdate.phase1ProgressResponse.phase1TestsStarted mustBe false
      progressResponseAfterUpdate.phase1ProgressResponse.phase1TestsCompleted mustBe false
      progressResponseAfterUpdate.phase1ProgressResponse.phase1TestsResultsReceived mustBe false
      progressResponseAfterUpdate.phase1ProgressResponse.phase1TestsPassed mustBe false
    }
  }

  private def createUnAllocatedFSACApplications(num: Int): Future[Unit] = {
    val additionalProgressStatuses = List(ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION -> true)
    createApplications(num, ApplicationStatus.ASSESSMENT_CENTRE, additionalProgressStatuses)
  }

  private def createAllocatedFSACApplications(num: Int): Future[Unit] = {
    val additionalProgressStatuses = List(ProgressStatuses.ASSESSMENT_CENTRE_ALLOCATION_UNCONFIRMED -> true)
    createApplications(num, ApplicationStatus.ASSESSMENT_CENTRE, additionalProgressStatuses)
  }

  private def createUnAllocatedFSBApplications(num: Int, schemes: List[SchemeEvaluationResult]): Future[Unit] = {
    val additionalProgressStatuses = List(ProgressStatuses.FSB_AWAITING_ALLOCATION -> true)
    createApplications(num, ApplicationStatus.FSB, additionalProgressStatuses, schemes)
  }

  private def createApplications(
    num: Int,
    appStatus: ApplicationStatus,
    additionalProgressStatuses: List[(ProgressStatus, Boolean)],
    schemes: List[SchemeEvaluationResult] = List.empty): Future[Unit] = {

    val additionalDoc = Document(
      "fsac-indicator" -> Document(
        "area" -> "London",
        "assessmentCentre" -> "London",
        "version" -> "1"
      ),
      "currentSchemeStatus" -> Codecs.toBson(schemes)
    )

    Future.sequence(
      (0 until num).map { i =>
        testDataRepo.createApplicationWithAllFields(
          UserId + (i + 1), AppId + (i + 1), TestAccountId + (i + 1), FrameworkId, appStatus,
          firstName = Some("George" + f"${i + 1}%02d"), lastName = Some("Jetson" + f"${i + 1}%02d"),
          additionalDoc = additionalDoc, additionalProgressStatuses = additionalProgressStatuses
        )
      }
    ).map(_ => ())
  }

  val candidate = Candidate("userId", Some("appId123"), Some("testAccountId"), Some("test@test123.com"), None, None, None, None,
    None, None, None, None, None)

  val testCandidate = Map(
    "firstName" -> "George",
    "lastName" -> "Jetson",
    "preferredName" -> "Georgy",
    "dateOfBirth" -> "1986-05-01"
  )

  val phase3TestGroup = Document (
    "testGroups" -> Document(
      "PHASE3" -> Document(
        "evaluation" -> Document(
          "passmarkVersion" -> java.util.UUID.randomUUID().toString
        )
      )
    )
  )
}
