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
import model.Exceptions.{ApplicationNotFound, NotFoundException}
import model.ProgressStatuses.{PHASE1_TESTS_PASSED => _, PHASE3_TESTS_FAILED => _, SUBMITTED => _, _}
import model.command.ProgressResponse
import model.exchange.CandidatesEligibleForEventResponse
import model.persisted._
import model.persisted.eventschedules.EventType
import model.{ApplicationStatus, Candidate, _}
import org.joda.time.{DateTime, LocalDate}
import reactivemongo.api.indexes.IndexType.{Ascending, Descending}
import reactivemongo.bson.{BSONArray, BSONDocument}
import repositories.CollectionNames
import repositories.onlinetesting.{Phase1TestMongoRepository, Phase2TestMongoRepository}
import scheduler.fixer.FixBatch
import scheduler.fixer.RequiredFixes.{AddMissingPhase2ResultReceived, PassToPhase1TestPassed, PassToPhase2, ResetPhase1TestInvitedSubmitted}
import testkit.MongoRepositorySpec

import scala.concurrent.{Await, Future}

class GeneralApplicationMongoRepositorySpec extends MongoRepositorySpec with UUIDFactory {

  val collectionName = CollectionNames.APPLICATION

  def repository = new GeneralApplicationMongoRepository(ITDateTimeFactoryMock, appConfig, mongo)
  def phase1TestRepo = new Phase1TestMongoRepository(ITDateTimeFactoryMock, mongo)
  def phase2TestRepo = new Phase2TestMongoRepository(ITDateTimeFactoryMock, mongo)
  def testDataRepo = new TestDataMongoRepository(mongo)

  "General Application repository" should {
    "create indexes" in {
      val indexes = indexesWithFields(repository)
      indexes must contain(IndexDetails(key = Seq(("_id", Ascending)), unique = false))
      indexes must contain(IndexDetails(key = Seq(("applicationId", Ascending), ("userId", Ascending)), unique = true))
      indexes must contain(IndexDetails(key = Seq(("userId", Ascending), ("frameworkId", Ascending)), unique = true))
      indexes must contain(IndexDetails(key = Seq(("applicationStatus", Ascending)), unique = false))
      indexes must contain(IndexDetails(key = Seq(("assistance-details.needsSupportForOnlineAssessment", Ascending)), unique = false))
      indexes must contain(IndexDetails(key = Seq(("assistance-details.needsSupportAtVenue", Ascending)), unique = false))
      indexes must contain(IndexDetails(key = Seq(("assistance-details.guaranteedInterview", Ascending)), unique = false))
      indexes.size mustBe 7
    }

    "Find user by id" in {
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

    "Find application status" in {
      val userId = "fastPassUser"
      val appId = "fastPassApp"
      val testAccountId = "testAccountId"
      val frameworkId = "FastStream-2016"

      testDataRepo.createApplicationWithAllFields(userId, appId, testAccountId, frameworkId, appStatus = SUBMITTED).futureValue

      val applicationStatusDetails = repository.findStatus(appId).futureValue

      applicationStatusDetails.status mustBe SUBMITTED.toString
      applicationStatusDetails.statusDate.get mustBe LocalDate.now().toDateTimeAtStartOfDay
    }
  }

  "Find by criteria" should {
    "find by first name" in {
      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId", "FastStream-2016").futureValue

      val applicationResponse = repository.findByCriteria(
        Some(testCandidate("firstName")), None, None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find by preferred name" in {
      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId", "FastStream-2016").futureValue

      val applicationResponse = repository.findByCriteria(
        Some(testCandidate("preferredName")), None, None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find by first/preferred name with special regex character" in {
      testDataRepo.createApplicationWithAllFields(userId = "userId", appId = "appId123", testAccountId = "testAccountId",
        frameworkId = "FastStream-2016", firstName = Some("Char+lie.+x123")).futureValue

      val applicationResponse = repository.findByCriteria(
        Some("Char+lie.+x123"), None, None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find by lastname" in {
      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId", "FastStream-2016").futureValue

      val applicationResponse = repository.findByCriteria(
        None, Some(testCandidate("lastName")), None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find by lastname with special regex character" in {
      testDataRepo.createApplicationWithAllFields(userId = "userId", appId = "appId123", testAccountId = "testAccountId",
        frameworkId = "FastStream-2016", lastName = Some("Barr+y.+x123")).futureValue

      val applicationResponse = repository.findByCriteria(
        None, Some("Barr+y.+x123"), None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find date of birth" in {
      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId", "FastStream-2016").futureValue

      val dobParts = testCandidate("dateOfBirth").split("-").map(_.toInt)
      val (dobYear, dobMonth, dobDay) = (dobParts.head, dobParts(1), dobParts(2))

      val applicationResponse = repository.findByCriteria(
        None, None, Some(new LocalDate(
          dobYear,
          dobMonth,
          dobDay
        ))
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "Return an empty candidate list when there are no results" in {
      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId", "FastStream-2016").futureValue

      val applicationResponse = repository.findByCriteria(
        Some("UnknownFirstName"), None, None
      ).futureValue

      applicationResponse.size mustBe 0
    }

    "filter by provided user Ids" in {
      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId", "FastStream-2016").futureValue
      val matchResponse = repository.findByCriteria(
        None, None, None, List("userId")
      ).futureValue

      matchResponse.size mustBe 1

      val noMatchResponse = repository.findByCriteria(
        None, None, None, List("unknownUser")
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

    "NO update performed if, in the meanwhile, the pre-conditions of the update have changed" in {
      // no "PHASE2_TESTS_INVITED" -> true (which is a pre-condition)
      import ProgressStatuses._
      val statuses = List(SUBMITTED, PHASE1_TESTS_INVITED, PHASE1_TESTS_STARTED, PHASE1_TESTS_COMPLETED,
        PHASE1_TESTS_RESULTS_READY, PHASE1_TESTS_RESULTS_RECEIVED, PHASE1_TESTS_PASSED)
        .map(_ -> true)

      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId", "FastStream-2016", ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = statuses).futureValue

      val matchResponse = repository.fix(candidate, FixBatch(PassToPhase2, 1)).futureValue
      matchResponse.isDefined mustBe false

      val applicationResponse = repository.findByUserId("userId", "FastStream-2016").futureValue
      applicationResponse.userId mustBe "userId"
      applicationResponse.applicationId mustBe "appId123"
      applicationResponse.applicationStatus mustBe ApplicationStatus.PHASE1_TESTS.toString
    }
  }

  //TODO: cubiks this uses phase1TestGroup which is cubiks specific
  /*
  "fix a ResetPhase1TestInvitedSubmitted issue" should {
    "update the remove PHASE1_TESTS_INVITED and the test group" in {

      val statuses = (ProgressStatuses.SUBMITTED, true) :: (ProgressStatuses.PHASE1_TESTS_INVITED, true) :: Nil

      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId","FastStream-2016", ApplicationStatus.SUBMITTED,
        additionalProgressStatuses = statuses, additionalDoc = phase1TestGroup).futureValue

      val matchResponse = repository.fix(candidate, FixBatch(ResetPhase1TestInvitedSubmitted, 1)).futureValue
      matchResponse.isDefined mustBe true

      val applicationResponse = repository.findByUserId("userId", "FastStream-2016").futureValue
      applicationResponse.userId mustBe "userId"
      applicationResponse.applicationId mustBe "appId123"
      applicationResponse.applicationStatus mustBe ApplicationStatus.SUBMITTED.toString
      applicationResponse.progressResponse.phase1ProgressResponse.phase1TestsInvited mustBe false

      val testGroup: Option[Phase1TestProfile2] = phase1TestRepo.getTestGroup("appId123").futureValue
      testGroup mustBe None
    }
  }*/

  "findAdjustments" should {
    "return None if assistance-details does not exist" in {
      val result = repository.create("userId", "frameworkId", ApplicationRoute.Faststream).futureValue
      repository.findAdjustments(result.applicationId).futureValue mustBe None
    }
  }

  //TODO cubiks - all these tests use phase1TestGroup which is cubiks specific
  /*
  "fix PassToPhase1TestPassed" should {
    "get None for PHASE1_TESTS_PASSED but with PHASE2_TESTS_INVITED" in {
      val statuses = (ProgressStatuses.PHASE1_TESTS_PASSED, true) :: (ProgressStatuses.PHASE2_TESTS_INVITED, true) :: Nil
      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId", "FastStream-2016", ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = statuses, additionalDoc = phase1TestGroup).futureValue
      val candidates = repository.getApplicationsToFix(FixBatch(PassToPhase1TestPassed, 1)).futureValue
      candidates mustBe empty
    }

    "get a candidate for PHASE1_TESTS_PASSED and without PHASE2_TESTS_INVITED" in {
      val statuses = (ProgressStatuses.PHASE1_TESTS_PASSED, true) :: Nil
      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId", "FastStream-2016", ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = statuses, additionalDoc = phase1TestGroup).futureValue
      val candidates = repository.getApplicationsToFix(FixBatch(PassToPhase1TestPassed, 1)).futureValue
      candidates.headOption.flatMap(_.applicationId) mustBe Some("appId123")
    }

    "move PHASE1_TESTS to PHASE1_TESTS_PASSED if PHASE1_TESTS_PASSED exists without PHASE2_TESTS_INVITED" in {
      val statuses = (ProgressStatuses.PHASE1_TESTS_PASSED, true) :: Nil
      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId", "FastStream-2016", ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = statuses, additionalDoc = phase1TestGroup).futureValue
      val matchResponse = repository.fix(candidate, FixBatch(PassToPhase1TestPassed, 1)).futureValue
      matchResponse mustBe defined

      val applicationResponse = repository.findByUserId("userId", "FastStream-2016").futureValue
      applicationResponse.userId mustBe "userId"
      applicationResponse.applicationId mustBe "appId123"
      applicationResponse.applicationStatus mustBe ApplicationStatus.PHASE1_TESTS_PASSED.toString
    }

    "do not change application status for non PHASE1_TESTS" in {
      val statuses = (ProgressStatuses.PHASE1_TESTS_PASSED, true) :: Nil
      testDataRepo.createApplicationWithAllFields("userId", "appId123", "testAccountId", "FastStream-2016", ApplicationStatus.PHASE2_TESTS,
        additionalProgressStatuses = statuses, additionalDoc = phase1TestGroup).futureValue
      val matchResponse = repository.fix(candidate, FixBatch(PassToPhase1TestPassed, 1)).futureValue
      matchResponse mustNot be(defined)

      val applicationResponse = repository.findByUserId("userId", "FastStream-2016").futureValue
      applicationResponse.applicationStatus mustBe ApplicationStatus.PHASE2_TESTS.toString
    }
  }*/

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
    "return not found if the application route is not Faststream" in {
      testDataRepo.createApplicationWithAllFields(UserId, AppId, TestAccountId, FrameworkId,
        applicationRoute = Some(ApplicationRoute.Edip)).futureValue

      val result = repository.updateApplicationRoute(AppId, ApplicationRoute.Faststream, ApplicationRoute.SdipFaststream).failed.futureValue

      result mustBe a[Exceptions.NotFoundException]
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

      an[NotFoundException] must be thrownBy Await.result(repository.archive(AppId, UserId, userIdToArchiveWith,
        FrameworkId, ApplicationRoute.Faststream), timeout)
    }
  }

  // TODO: cubiks looks like this is cubiks specific. Should we delete this?
/*
  "AddMissingPhase2ResultReceived" should {
    val testResult = TestResult("Ready", "norm", Some(10.0), Some(20.0), Some(30.0), Some(40.0))

    "get an application with missing result received status and with a phase 2 result" in {
      createAppWithTestResult(List((ProgressStatuses.PHASE2_TESTS_RESULTS_READY, true)), Some(testResult))
      val result = repository.getApplicationsToFix(FixBatch(AddMissingPhase2ResultReceived, 1)).futureValue
      result.flatMap(_.applicationId) mustBe List(AppId)
    }

    "get nothing when the result received status is already there" in {
      createAppWithTestResult(List(
        (ProgressStatuses.PHASE2_TESTS_RESULTS_READY, true),
        (ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED, true)
      ), Some(testResult))
      val result = repository.getApplicationsToFix(FixBatch(AddMissingPhase2ResultReceived, 1)).futureValue
      result mustBe empty
    }

    "get nothing when the status is missing, but the result is not there yet" in {
      createAppWithTestResult(List((ProgressStatuses.PHASE2_TESTS_RESULTS_READY, true)), testResult = None)
      val result = repository.getApplicationsToFix(FixBatch(AddMissingPhase2ResultReceived, 1)).futureValue
      result mustBe empty
    }

    "add the missing result received status" in {
      createAppWithTestResult(List((ProgressStatuses.PHASE2_TESTS_RESULTS_READY, true)), Some(testResult))
      val application = candidate.copy(applicationId = Some(AppId))
      val matchResponse = repository.fix(application, FixBatch(AddMissingPhase2ResultReceived, 1)).futureValue
      matchResponse mustBe defined

      val applicationResponse = repository.findByUserId(UserId, FrameworkId).futureValue
      applicationResponse.progressResponse.phase2ProgressResponse.phase2TestsResultsReceived mustBe true
    }
  }*/

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

  private def findFsacCandidatesCall = repository.findCandidatesEligibleForEventAllocation(List("London"), EventType.FSAC, None).futureValue

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
    appStatus1: ApplicationStatus,
    additionalProgressStatuses: List[(ProgressStatus, Boolean)],
    schemes: List[SchemeEvaluationResult] = List.empty): Future[Unit] = {
    val additionalDoc = BSONDocument(
      "fsac-indicator" -> BSONDocument(
        "area" -> "London",
        "assessmentCentre" -> "London",
        "version" -> "1"
      ),
      "currentSchemeStatus" -> schemes
    )

    Future.sequence(
      (0 until num).map { i =>
        testDataRepo.createApplicationWithAllFields(
          UserId + (i + 1), AppId + (i + 1), TestAccountId + (i + 1), FrameworkId, appStatus = appStatus1,
          firstName = Some("George" + f"${i + 1}%02d"), lastName = Some("Jetson" + f"${i + 1}%02d"),
          additionalDoc = additionalDoc, additionalProgressStatuses = additionalProgressStatuses
        )
      }
    ).map(_ => ())
  }

  // TODO: cubiks specific. Looks like this should be deleted
  /*
  private def createAppWithTestResult(progressStatuses: List[(ProgressStatus, Boolean)], testResult: Option[TestResult]) = {
    testDataRepo.createApplicationWithAllFields(UserId, AppId, TestAccountId, FrameworkId, ApplicationStatus.PHASE2_TESTS,
      additionalProgressStatuses = progressStatuses).futureValue
    val test = CubiksTest(1, usedForResults = true, 1, "cubiks", "token", "testUrl", DateTime.now, 1, testResult = testResult)
    val phase2TestGroup = Phase2TestGroup(DateTime.now, List(test))
    phase2TestRepo.insertOrUpdateTestGroup(AppId, phase2TestGroup).futureValue
  }*/

  val candidate = Candidate("userId", Some("appId123"), Some("testAccountId"), Some("test@test123.com"), None, None, None, None,
    None, None, None, None, None)

  val testCandidate = Map(
    "firstName" -> "George",
    "lastName" -> "Jetson",
    "preferredName" -> "Georgy",
    "dateOfBirth" -> "1986-05-01"
  )

  //TODO: cubiks specific
  /*
  val phase1TestGroup = BSONDocument (
    "testGroups" -> BSONDocument(
      "PHASE1" -> BSONDocument(
        "tests" -> BSONArray(
          BSONDocument(
            "scheduleId" -> "16196",
            "usedForResults" -> true,
            "cubiksUserId" -> "180055",
            "testProvider" -> "cubiks",
            "token" -> "6ry6reyr6hrhttrhtr",
            "testUrl" -> "https://dsfgsgdfugdsifugdsu.com",
            "participantScheduleId" -> "216679",
            "resultsReadyToDownload" -> "false",
            "reportLinkURL" -> "https://dsfgsgdfugdsifugdsu.com",
            "reportId" -> "86830"
          ),
          BSONDocument(
            "scheduleId" -> "34543",
            "usedForResults" -> "true",
            "cubiksUserId" -> "180436",
            "testProvider" -> "cubiks",
            "token" -> "reytryteryerty6yry6",
            "testUrl" -> "https://dsfgsgdfugdsifugdef.com",
            "participantScheduleId" -> "435435",
            "resultsReadyToDownload" -> "false",
            "reportLinkURL" -> "https://gergtrhtrhtrhtrhtr.com",
            "reportId" -> "546456"
          )
        )
      )
    )
  )*/

  val phase3TestGroup = BSONDocument (
    "testGroups" -> BSONDocument(
      "PHASE3" -> BSONDocument(
        "evaluation" -> BSONDocument(
          "passmarkVersion" -> java.util.UUID.randomUUID().toString
        )
      )
    )
  )
}
