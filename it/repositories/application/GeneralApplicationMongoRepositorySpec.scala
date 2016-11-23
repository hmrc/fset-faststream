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

  "General Application repository" should {
    "Get overall report for an application with all fields" in {
      val userId = generateUUID()
      val appId = generateUUID()
      createApplicationWithAllFields(userId, appId, "FastStream-2016")

      val result = repository.candidateProgressReport("FastStream-2016").futureValue

      result must not be empty
      result.head mustBe CandidateProgressReportItem(userId, appId, Some("submitted"),
        List(SchemeType.DiplomaticService, SchemeType.GovernmentOperationalResearchService), Some("Yes"),
        Some("No"), Some("No"), Some("No"), Some("Yes"), Some("No"), Some("Yes"), Some("No"), Some("Yes"), Some("1234567"), None)
    }

    "Get overall report for the minimum application" in {
      val userId = generateUUID()
      val appId = generateUUID()
      createMinimumApplication(userId, appId, "FastStream-2016")

      val result = repository.candidateProgressReport("FastStream-2016").futureValue

      result must not be empty
      result.head must be(CandidateProgressReportItem(userId, appId, Some("registered"),
        List.empty[SchemeType], None, None, None, None, None, None, None, None, None, None, None)
      )
    }

    "Get diversity report for the minimum application" in {
      val userId = generateUUID()
      val appId = generateUUID()
      createMinimumApplication(userId, appId, "FastStream-2016")

      val result = repository.diversityReport("FastStream-2016").futureValue

      result must not be empty
      result.head mustBe ApplicationForDiversityReport(appId, userId, Some("registered"),
        List.empty, None, None, None, None, None)
    }

    "Get diversity report for an application with all fields" in {
      val userId1 = generateUUID()
      val userId2 = generateUUID()
      val userId3 = generateUUID()
      val appId1 = generateUUID()
      val appId2 = generateUUID()
      val appId3 = generateUUID()
      createApplicationWithAllFields(userId1, appId1, "FastStream-2016", guaranteedInterview = true, needsSupportForOnlineAssessment = true)
      createApplicationWithAllFields(userId2, appId2, "FastStream-2016", hasDisability = "No")
      createApplicationWithAllFields(userId3, appId3, "FastStream-2016", needsSupportAtVenue = true)

      val result = repository.diversityReport("FastStream-2016").futureValue

      result must contain theSameElementsAs Seq(
        ApplicationForDiversityReport(appId1, userId1, Some("submitted"),
          List(SchemeType.DiplomaticService, SchemeType.GovernmentOperationalResearchService),
          Some("Yes"), Some(true), Some("Yes"), Some("No"), Some(CivilServiceExperienceDetailsForDiversityReport(Some("Yes"),
            Some("No"), Some("Yes"), Some("No"), Some("Yes"), Some("1234567")))),
        ApplicationForDiversityReport(
            appId2, userId2, Some("submitted"),
            List(SchemeType.DiplomaticService, SchemeType.GovernmentOperationalResearchService),
            Some("Yes"), Some(false), Some("No"), Some("No"), Some(CivilServiceExperienceDetailsForDiversityReport(Some("Yes"),
              Some("No"), Some("Yes"), Some("No"), Some("Yes"), Some("1234567")))),
        ApplicationForDiversityReport(
            appId3, userId3, Some("submitted"),
            List(SchemeType.DiplomaticService, SchemeType.GovernmentOperationalResearchService),
            Some("Yes"), Some(false), Some("No"), Some("Yes"), Some(CivilServiceExperienceDetailsForDiversityReport(Some("Yes"),
              Some("No"), Some("Yes"), Some("No"), Some("Yes"), Some("1234567"))))
        )
    }

    "Find user by id" in {
      val userId = "fastPassUser"
      val appId = "fastPassApp"
      val frameworkId = "FastStream-2016"
      createApplicationWithAllFields(userId, appId, frameworkId)

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
      createApplicationWithAllFields(userId, appId, frameworkId, appStatus = SUBMITTED)

      val applicationStatusDetails = repository.findStatus(appId).futureValue

      applicationStatusDetails.status mustBe SUBMITTED.toString
      applicationStatusDetails.statusDate.get mustBe LocalDate.now().toDateTimeAtStartOfDay

    }
  }

  "Find by criteria" should {
    "find by first name" in {
      createApplicationWithAllFields("userId", "appId123", "FastStream-2016")

      val applicationResponse = repository.findByCriteria(
        Some(testCandidate("firstName")), None, None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find by preferred name" in {
      createApplicationWithAllFields("userId", "appId123", "FastStream-2016")

      val applicationResponse = repository.findByCriteria(
        Some(testCandidate("preferredName")), None, None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find by first/preferred name with special regex character" in {
      createApplicationWithAllFields(userId = "userId", appId = "appId123", frameworkId = "FastStream-2016",
        firstName = Some("Char+lie.+x123"))

      val applicationResponse = repository.findByCriteria(
        Some("Char+lie.+x123"), None, None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find by lastname" in {
      createApplicationWithAllFields("userId", "appId123", "FastStream-2016")

      val applicationResponse = repository.findByCriteria(
        None, Some(testCandidate("lastName")), None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find by lastname with special regex character" in {
      createApplicationWithAllFields(userId = "userId", appId = "appId123", frameworkId = "FastStream-2016",
        lastName = Some("Barr+y.+x123"))

      val applicationResponse = repository.findByCriteria(
        None, Some("Barr+y.+x123"), None
      ).futureValue

      applicationResponse.size mustBe 1
      applicationResponse.head.applicationId mustBe Some("appId123")
    }

    "find date of birth" in {
      createApplicationWithAllFields("userId", "appId123", "FastStream-2016")

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
      createApplicationWithAllFields("userId", "appId123", "FastStream-2016")

      val applicationResponse = repository.findByCriteria(
        Some("UnknownFirstName"), None, None
      ).futureValue

      applicationResponse.size mustBe 0
    }

    "filter by provided user Ids" in {
      createApplicationWithAllFields("userId", "appId123", "FastStream-2016")
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

      createApplicationWithAllFields("userId", "appId123", "FastStream-2016", ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = statuses.toList)

      val matchResponse = repository.getApplicationsToFix(FixBatch(PassToPhase2, 1)).futureValue
      matchResponse.size mustBe 1
    }

    "return 0 result if the application is in PHASE1_TESTS_PASSED but not yet invited to PHASE 2" in {
      val statuses: Seq[(ProgressStatuses.ProgressStatus, Boolean)] = (ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED, true) ::
        (ProgressStatuses.PHASE1_TESTS_PASSED, true) :: Nil

      createApplicationWithAllFields("userId", "appId123", "FastStream-2016", ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = statuses.toList)

      val matchResponse = repository.getApplicationsToFix(FixBatch(PassToPhase2, 1)).futureValue
      matchResponse.size mustBe 0
    }

    "return 0 result if the application is in PHASE2_TESTS_INVITED and PHASE1_TESTS_PASSED but already in PHASE 2" in {
      val statuses: Seq[(ProgressStatuses.ProgressStatus, Boolean)] = (ProgressStatuses.PHASE1_TESTS_INVITED, true) ::
        (ProgressStatuses.PHASE1_TESTS_STARTED, true) :: (ProgressStatuses.PHASE1_TESTS_COMPLETED, true) ::
        (ProgressStatuses.PHASE1_TESTS_RESULTS_READY, true) :: (ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED, true) ::
        (ProgressStatuses.PHASE1_TESTS_PASSED, true) :: (ProgressStatuses.PHASE1_TESTS_PASSED, true) :: Nil

      createApplicationWithAllFields("userId", "appId123", "FastStream-2016", ApplicationStatus.PHASE2_TESTS,
        additionalProgressStatuses = statuses.toList)

      val matchResponse = repository.getApplicationsToFix(FixBatch(PassToPhase2, 1)).futureValue
      matchResponse.size mustBe 0
    }

    "return no result if the application is in PHASE2_TESTS_INVITED but not PHASE1_TESTS_PASSED" in {
      // This would be an inconsistent state and we don't want to make things worse.
      val statuses: Seq[(ProgressStatuses.ProgressStatus, Boolean)] = (ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED, true) ::
        (ProgressStatuses.PHASE2_TESTS_INVITED, true) :: Nil

      createApplicationWithAllFields("userId", "appId123", "FastStream-2016", ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = statuses.toList)
      val matchResponse = repository.getApplicationsToFix(FixBatch(PassToPhase2, 1)).futureValue
      matchResponse.size mustBe 0
    }
  }

  "Get Application to Fix for ResetPhase1TestInvitedSubmitted fix" should {
    "return 1 result if the application is in PHASE1_TESTS_INVITED and SUBMITTED" in {
      val statuses = (ProgressStatuses.SUBMITTED, true) ::
        (ProgressStatuses.PHASE1_TESTS_INVITED, true) :: Nil

      createApplicationWithAllFields("userId", "appId123", "FastStream-2016", ApplicationStatus.SUBMITTED,
        additionalProgressStatuses = statuses)

      val matchResponse = repository.getApplicationsToFix(FixBatch(ResetPhase1TestInvitedSubmitted, 1)).futureValue
      matchResponse.size mustBe 1

    }

    "return 0 results if the application is in PHASE1_TESTS_INVITED and SUBMITTED" in {
      val statuses = (ProgressStatuses.SUBMITTED, true) ::
        (ProgressStatuses.PHASE1_TESTS_INVITED, true) :: Nil

      createApplicationWithAllFields("userId", "appId123", "FastStream-2016", ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = statuses)

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

      createApplicationWithAllFields("userId", "appId123", "FastStream-2016", ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = statuses)

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

      createApplicationWithAllFields("userId", "appId123", "FastStream-2016", ApplicationStatus.PHASE1_TESTS,
        additionalProgressStatuses = statuses)

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

      createApplicationWithAllFields("userId", "appId123", "FastStream-2016", ApplicationStatus.SUBMITTED,
        additionalProgressStatuses = statuses, additionalDoc = phase1TestGroup)

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

  // scalastyle:off parameter.number
  def createApplicationWithAllFields(userId: String, appId: String, frameworkId: String,
    appStatus: ApplicationStatus = IN_PROGRESS, hasDisability: String = "Yes", needsSupportForOnlineAssessment: Boolean = false,
    needsSupportAtVenue: Boolean = false, guaranteedInterview: Boolean = false, lastName: Option[String] = None,
    firstName: Option[String] = None, preferredName: Option[String] = None, additionalProgressStatuses: List[(ProgressStatus, Boolean)] = Nil,
    additionalDoc: BSONDocument = BSONDocument()
  ) = {
    import repositories.BSONLocalDateHandler
    repository.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "applicationStatus" -> appStatus,
      "userId" -> userId,
      "frameworkId" -> frameworkId,
      "scheme-preferences" -> BSONDocument(
        "schemes" -> BSONArray(SchemeType.DiplomaticService, SchemeType.GovernmentOperationalResearchService)
      ),
      "personal-details" -> BSONDocument(
        "firstName" -> firstName.getOrElse(s"${testCandidate("firstName")}"),
        "lastName" -> lastName.getOrElse(s"${testCandidate("lastName")}"),
        "preferredName" -> preferredName.getOrElse(s"${testCandidate("preferredName")}"),
        "dateOfBirth" -> s"${testCandidate("dateOfBirth")}"
      ),
      "civil-service-experience-details" -> BSONDocument(
        "applicable" -> true,
        "fastPassReceived" -> true,
        "certificateNumber" -> "1234567",
        "civilServiceExperienceType" -> CivilServiceExperienceType.CivilServant,
        "internshipTypes" -> BSONArray(InternshipType.SDIPCurrentYear, InternshipType.EDIP)
      ),
      "assistance-details" -> BSONDocument(
        "hasDisability" -> "Yes",
        "needsSupportForOnlineAssessment" -> needsSupportForOnlineAssessment,
        "needsSupportAtVenue" -> needsSupportAtVenue,
        "guaranteedInterview" -> guaranteedInterview
      ),
      "issue" -> "this candidate has changed the email",
      "progress-status" -> progressStatus(additionalProgressStatuses),
      "progress-status-dates" -> BSONDocument(
        "submitted" -> LocalDate.now()
      )
    ) ++ additionalDoc).futureValue
  }
  // scalastyle:on parameter.number

  def progressStatus(args: List[(ProgressStatus, Boolean)] = List.empty): BSONDocument = {
    val baseDoc = BSONDocument(
      "personal-details" -> true,
      "in_progress" -> true,
      "scheme-preferences" -> true,
      "partner-graduate-programmes" -> true,
      "assistance-details" -> true,
      "questionnaire" -> BSONDocument(
        "start_questionnaire" -> true,
        "diversity_questionnaire" -> true,
        "education_questionnaire" -> true,
        "occupation_questionnaire" -> true
      ),
      "preview" -> true,
      "submitted" -> true
    )

    args.foldLeft(baseDoc)((acc, v) => acc ++ (v._1.toString -> v._2))
  }

  def createMinimumApplication(userId: String, appId: String, frameworkId: String) = {
    repository.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "userId" -> userId,
      "frameworkId" -> frameworkId
    )).futureValue
  }
}
