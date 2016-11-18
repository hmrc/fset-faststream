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

import factories.{ DateTimeFactory, UUIDFactory }
import model.ApplicationStatus._
import model.ProgressStatuses.{ PHASE1_TESTS_PASSED => _, SUBMITTED => _, _ }
import model.SchemeType.SchemeType
import model.report.CandidateProgressReportItem
import model.{ ApplicationStatus, _ }
import org.joda.time.LocalDate
import reactivemongo.bson.{ BSONArray, BSONDocument }
import reactivemongo.json.ImplicitBSONHandlers
import services.GBTimeZoneService
import config.MicroserviceAppConfig._
import model.ApplicationRoute.{ apply => _ }
import model.Commands.Candidate
import model.command.ProgressResponse
import model.persisted._
import repositories.CommonBSONDocuments
import repositories.onlinetesting.Phase1TestMongoRepository
import scheduler.fixer.FixBatch
import scheduler.fixer.RequiredFixes.{ PassToPhase2, ResetPhase1TestInvitedSubmitted }
import testkit.MongoRepositorySpec

class GeneralApplicationMongoRepositorySpec extends MongoRepositorySpec with UUIDFactory with CommonBSONDocuments {

  import ImplicitBSONHandlers._

  val collectionName = "application"

  def repository = new GeneralApplicationMongoRepository(GBTimeZoneService, cubiksGatewayConfig, GeneralApplicationRepoBSONToModelHelper)
  def phase1TestRepo = new Phase1TestMongoRepository(DateTimeFactory)
  def testDataRepo = new TestDataMongoRepository()

  "General Application repository" should {
    "Find user by id" in {
      val userId = "fastPassUser"
      val appId = "fastPassApp"
      val frameworkId = "FastStream-2016"

      testDataRepo.createApplicationWithAllFields(userId, appId, frameworkId).futureValue

      val applicationResponse = repository.findByUserId(userId, frameworkId).futureValue

      applicationResponse.userId mustBe userId
      applicationResponse.applicationId mustBe appId
      applicationResponse.civilServiceExperienceDetails.get mustBe
        CivilServiceExperienceDetails(applicable = true, Some(CivilServiceExperienceType.CivilServant),
        Some(List(InternshipType.SDIPCurrentYear, InternshipType.EDIP)), fastPassReceived = Some(true),
        certificateNumber = Some("1234567"))
    }

    "Find application status" in {
      val userId = "fastPassUser"
      val appId = "fastPassApp"
      val frameworkId = "FastStream-2016"

      testDataRepo.createApplicationWithAllFields(userId, appId, frameworkId, appStatus = SUBMITTED).futureValue

      val applicationStatusDetails = repository.findStatus(appId).futureValue

      applicationStatusDetails.status mustBe SUBMITTED.toString
      applicationStatusDetails.statusDate.get mustBe LocalDate.now().toDateTimeAtStartOfDay

    }
  }

  "Find by criteria" should {
    "find by first name" in {
      testDataRepo.createApplicationWithAllFields("userId", "appId123", "FastStream-2016").futureValue

      val applicationResponse = repository.findByCriteria(
        Some(testCandidate("firstName")), None, None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find by preferred name" in {
      testDataRepo.createApplicationWithAllFields("userId", "appId123", "FastStream-2016").futureValue

      val applicationResponse = repository.findByCriteria(
        Some(testCandidate("preferredName")), None, None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find by first/preferred name with special regex character" in {
      testDataRepo.createApplicationWithAllFields(userId = "userId", appId = "appId123", frameworkId = "FastStream-2016",
        firstName = Some("Char+lie.+x123")).futureValue

      val applicationResponse = repository.findByCriteria(
        Some("Char+lie.+x123"), None, None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find by lastname" in {
      testDataRepo.createApplicationWithAllFields("userId", "appId123", "FastStream-2016").futureValue

      val applicationResponse = repository.findByCriteria(
        None, Some(testCandidate("lastName")), None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find by lastname with special regex character" in {
      testDataRepo.createApplicationWithAllFields(userId = "userId", appId = "appId123", frameworkId = "FastStream-2016",
        lastName = Some("Barr+y.+x123")).futureValue

      val applicationResponse = repository.findByCriteria(
        None, Some("Barr+y.+x123"), None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find date of birth" in {
      testDataRepo.createApplicationWithAllFields("userId", "appId123", "FastStream-2016").futureValue

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
      testDataRepo.createApplicationWithAllFields("userId", "appId123", "FastStream-2016").futureValue

      val applicationResponse = repository.findByCriteria(
        Some("UnknownFirstName"), None, None
      ).futureValue

      applicationResponse.size mustBe 0
    }

    "filter by provided user Ids" in {
      testDataRepo.createApplicationWithAllFields("userId", "appId123", "FastStream-2016").futureValue
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

      testDataRepo.createApplicationWithAllFields("userId", "appId123", "FastStream-2016", ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = statuses.toList).futureValue

      val matchResponse = repository.getApplicationsToFix(FixBatch(PassToPhase2, 1)).futureValue
      matchResponse.size mustBe 1
    }

    "return 0 result if the application is in PHASE1_TESTS_PASSED but not yet invited to PHASE 2" in {
      val statuses: Seq[(ProgressStatuses.ProgressStatus, Boolean)] = (ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED, true) ::
        (ProgressStatuses.PHASE1_TESTS_PASSED, true) :: Nil

      testDataRepo.createApplicationWithAllFields("userId", "appId123", "FastStream-2016", ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = statuses.toList).futureValue

      val matchResponse = repository.getApplicationsToFix(FixBatch(PassToPhase2, 1)).futureValue
      matchResponse.size mustBe 0
    }

    "return 0 result if the application is in PHASE2_TESTS_INVITED and PHASE1_TESTS_PASSED but already in PHASE 2" in {
      val statuses: Seq[(ProgressStatuses.ProgressStatus, Boolean)] = (ProgressStatuses.PHASE1_TESTS_INVITED, true) ::
        (ProgressStatuses.PHASE1_TESTS_STARTED, true) :: (ProgressStatuses.PHASE1_TESTS_COMPLETED, true) ::
        (ProgressStatuses.PHASE1_TESTS_RESULTS_READY, true) :: (ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED, true) ::
        (ProgressStatuses.PHASE1_TESTS_PASSED, true) :: (ProgressStatuses.PHASE1_TESTS_PASSED, true) :: Nil

      testDataRepo.createApplicationWithAllFields("userId", "appId123", "FastStream-2016", ApplicationStatus.PHASE2_TESTS,
        additionalProgressStatuses = statuses.toList).futureValue

      val matchResponse = repository.getApplicationsToFix(FixBatch(PassToPhase2, 1)).futureValue
      matchResponse.size mustBe 0
    }

    "return no result if the application is in PHASE2_TESTS_INVITED but not PHASE1_TESTS_PASSED" in {
      // This would be an inconsistent state and we don't want to make things worse.
      val statuses: Seq[(ProgressStatuses.ProgressStatus, Boolean)] = (ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED, true) ::
        (ProgressStatuses.PHASE2_TESTS_INVITED, true) :: Nil

      testDataRepo.createApplicationWithAllFields("userId", "appId123", "FastStream-2016", ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = statuses.toList).futureValue
      val matchResponse = repository.getApplicationsToFix(FixBatch(PassToPhase2, 1)).futureValue
      matchResponse.size mustBe 0
    }
  }

  "Get Application to Fix for ResetPhase1TestInvitedSubmitted fix" should {
    "return 1 result if the application is in PHASE1_TESTS_INVITED and SUBMITTED" in {
      val statuses = (ProgressStatuses.SUBMITTED, true) ::
        (ProgressStatuses.PHASE1_TESTS_INVITED, true) :: Nil

      testDataRepo.createApplicationWithAllFields("userId", "appId123", "FastStream-2016", ApplicationStatus.SUBMITTED,
        additionalProgressStatuses = statuses).futureValue

      val matchResponse = repository.getApplicationsToFix(FixBatch(ResetPhase1TestInvitedSubmitted, 1)).futureValue
      matchResponse.size mustBe 1

    }

    "return 0 results if the application is in PHASE1_TESTS_INVITED and SUBMITTED" in {
      val statuses = (ProgressStatuses.SUBMITTED, true) ::
        (ProgressStatuses.PHASE1_TESTS_INVITED, true) :: Nil

      testDataRepo.createApplicationWithAllFields("userId", "appId123", "FastStream-2016", ApplicationStatus.PHASE1_TESTS,
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

      testDataRepo.createApplicationWithAllFields("userId", "appId123", "FastStream-2016", ApplicationStatus.PHASE1_TESTS,
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

      testDataRepo.createApplicationWithAllFields("userId", "appId123", "FastStream-2016", ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = statuses).futureValue

      val matchResponse = repository.fix(candidate, FixBatch(PassToPhase2, 1)).futureValue
      matchResponse.isDefined mustBe false

      val applicationResponse = repository.findByUserId("userId", "FastStream-2016").futureValue
      applicationResponse.userId mustBe "userId"
      applicationResponse.applicationId mustBe "appId123"
      applicationResponse.applicationStatus mustBe ApplicationStatus.PHASE1_TESTS.toString
    }
  }


  "fix a ResetPhase1TestInvitedSubmitted issue" should {
    "update the renove PHASE1_TESTS_INVITED and the test group" in {

      val statuses = (ProgressStatuses.SUBMITTED, true) :: (ProgressStatuses.PHASE1_TESTS_INVITED, true) :: Nil

      testDataRepo.createApplicationWithAllFields("userId", "appId123", "FastStream-2016", ApplicationStatus.SUBMITTED,
        additionalProgressStatuses = statuses, additionalDoc = phase1TestGroup).futureValue

      val matchResponse = repository.fix(candidate, FixBatch(ResetPhase1TestInvitedSubmitted, 1)).futureValue
      matchResponse.isDefined mustBe true

      val applicationResponse = repository.findByUserId("userId", "FastStream-2016").futureValue
      applicationResponse.userId mustBe "userId"
      applicationResponse.applicationId mustBe "appId123"
      applicationResponse.applicationStatus mustBe ApplicationStatus.SUBMITTED.toString
      applicationResponse.progressResponse.phase1ProgressResponse.phase1TestsInvited mustBe false

      val testGroup: Option[Phase1TestProfile] = phase1TestRepo.getTestGroup("appId123").futureValue
      testGroup mustBe None
    }
  }

  "findAdjustments" should {
    "return None if assistance-details does not exist" in {
      val result = repository.create("userId", "frameworkId", ApplicationRoute.Faststream).futureValue
      repository.findAdjustments(result.applicationId).futureValue mustBe None
    }
  }

  val candidate = Candidate("userId", Some("appId123"), Some("test@test123.com"), None, None, None, None, None, None, None, None)

  val testCandidate = Map(
    "firstName" -> "George",
    "lastName" -> "Jetson",
    "preferredName" -> "Georgy",
    "dateOfBirth" -> "1986-05-01"
  )

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
  )

}
