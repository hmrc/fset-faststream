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

import config.MicroserviceAppConfig
import services.testdata.TestDataGeneratorService
import services.testdata.candidate.CandidateStatusGeneratorFactory
import factories.{ ITDateTimeFactoryMock, UUIDFactory }
import model.ApplicationRoute.{ apply => _ }
import model.ProgressStatuses.{ PREVIEW, SUBMITTED, PHASE1_TESTS_PASSED => _ }
import model._
import model.command.ProgressResponse
import model.command.testdata.CreateCandidateRequest.{ AdjustmentsRequest, AssistanceDetailsRequest, CreateCandidateRequest, StatusDataRequest }
import model.persisted._
import model.report.{ AdjustmentReportItem, CandidateProgressReportItem }
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import repositories.CollectionNames
import services.GBTimeZoneService2
import services.testdata.faker.DataFaker
import testkit.MongoRepositorySpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Await

class ReportingMongoRepositorySpec extends MongoRepositorySpec with UUIDFactory {

  val frameworkId = "FastStream-2016"

  val collectionName = CollectionNames.APPLICATION

  def repository = new ReportingMongoRepository(new GBTimeZoneService2, ITDateTimeFactoryMock, mongo, mock[MicroserviceAppConfig])

  def applicationRepo = new GeneralApplicationMongoRepository(ITDateTimeFactoryMock, appConfig, mongo)

  def testDataRepo = new TestDataMongoRepository(mongo)

  def testDataGeneratorService = app.injector.instanceOf(classOf[TestDataGeneratorService])

  def dataFaker = app.injector.instanceOf(classOf[DataFaker])

  implicit def blankedHeaderCarrier = HeaderCarrier()

  val getCandidateStatusGeneratorFactory = app.injector.instanceOf(classOf[CandidateStatusGeneratorFactory])

  "Candidate Progress Report" must {
    "work for an application with all fields" in {
      val userId = generateUUID()
      val appId = generateUUID()
      val testAccountId = generateUUID()
      testDataRepo.createApplicationWithAllFields(userId, appId, testAccountId, "FastStream-2016").futureValue

      val result = repository.candidateProgressReport("FastStream-2016").futureValue

      result must not be empty
      result.head mustBe CandidateProgressReportItem(userId = userId, applicationId = appId, progress = Some("submitted"),
        schemes = List(SchemeId("DiplomaticAndDevelopment"), SchemeId("GovernmentOperationalResearchService")), disability = Some("Yes"),
        onlineAdjustments = Some("No"), assessmentCentreAdjustments = Some("No"), phoneAdjustments = None, gis = Some("No"),
        civilServant = Some("Yes"), edip = Some("Yes"), sdip = Some("Yes"), otherInternship = Some("Yes"),
        fastPassCertificate = Some("1234567"), assessmentCentre = None, applicationRoute = ApplicationRoute.Faststream)
    }

    "work for the minimum application" ignore {
      val userId = generateUUID()
      val appId = generateUUID()
//      testDataRepo.createMinimumApplication(userId, appId, "FastStream-2016").futureValue

      val result = repository.candidateProgressReport("FastStream-2016").futureValue

      result must not be empty
      result.head mustBe CandidateProgressReportItem(userId = userId, applicationId = appId, progress = Some("registered"),
        schemes = List.empty[SchemeId], disability = None, onlineAdjustments = None, assessmentCentreAdjustments = None,
        phoneAdjustments = None, gis = None, civilServant = None, edip = None, sdip = None, otherInternship = None,
        fastPassCertificate = None, assessmentCentre = None, applicationRoute = ApplicationRoute.Faststream)
    }
  }
/*
  "Diversity Report" must {
    "work for for the minimum application" ignore {
      val userId = generateUUID()
      val appId = generateUUID()
//      testDataRepo.createMinimumApplication(userId, appId, "FastStream-2016").futureValue

      val result = repository.diversityReport("FastStream-2016").futureValue

      result must not be empty
      result.head mustBe ApplicationForDiversityReport(appId, userId, ApplicationRoute.Faststream, Some("registered"),
        schemes = List.empty, disability = None, gis = None, onlineAdjustments = None, assessmentCentreAdjustments = None,
        civilServiceExperiencesDetails = Some(CivilServiceExperienceDetailsForDiversityReport(
          isCivilServant = Some("No"), isEDIP = Some("No"), edipYear = None, isSDIP = Some("No"),
          sdipYear = None, otherInternship = Some("No"), otherInternshipName = None, otherInternshipYear = None,
          fastPassCertificate = Some("No")
        )),
        currentSchemeStatus = List.empty)
    }

    "work for for an application with all fields" ignore {
      val userId1 = generateUUID()
      val userId2 = generateUUID()
      val userId3 = generateUUID()
      val appId1 = generateUUID()
      val appId2 = generateUUID()
      val appId3 = generateUUID()
      val testAccountId1 = generateUUID()
      val testAccountId2 = generateUUID()
      val testAccountId3 = generateUUID()

//      testDataRepo.createApplicationWithAllFields(userId1, appId1, testAccountId1,"FastStream-2016", guaranteedInterview = true,
//        needsSupportForOnlineAssessment = true).futureValue
//      testDataRepo.createApplicationWithAllFields(userId2, appId2, testAccountId2,"FastStream-2016", hasDisability = "No").futureValue
//      testDataRepo.createApplicationWithAllFields(userId3, appId3, testAccountId3,"FastStream-2016", needsSupportAtVenue = true).futureValue

      val result = repository.diversityReport("FastStream-2016").futureValue

      result must contain theSameElementsAs Seq(
        ApplicationForDiversityReport(
          applicationId = appId1, userId = userId1, applicationRoute = ApplicationRoute.Faststream, progress = Some("submitted"),
          schemes = List(SchemeId("DiplomaticAndDevelopment"), SchemeId("GovernmentOperationalResearchService")),
          disability = Some("Yes"), gis = Some(true), onlineAdjustments = Some("Yes"), assessmentCentreAdjustments = Some("No"),
          civilServiceExperiencesDetails = Some(CivilServiceExperienceDetailsForDiversityReport(
            isCivilServant = Some("Yes"), isEDIP = Some("Yes"), edipYear = Some("2018"), isSDIP = Some("Yes"), sdipYear = Some("2019"),
            otherInternship = Some("Yes"), otherInternshipName = Some("other"), otherInternshipYear = Some("2020"),
            fastPassCertificate = Some("1234567"))
          ),
          currentSchemeStatus = List(SchemeEvaluationResult(SchemeId("DiplomaticAndDevelopment"), EvaluationResults.Green.toString),
            SchemeEvaluationResult(SchemeId("GovernmentOperationalResearchService"), EvaluationResults.Green.toString))
        ),
        ApplicationForDiversityReport(
          applicationId = appId2, userId = userId2, applicationRoute = ApplicationRoute.Faststream, progress = Some("submitted"),
          schemes = List(SchemeId("DiplomaticAndDevelopment"), SchemeId("GovernmentOperationalResearchService")),
          disability = Some("Yes"), gis = Some(false), onlineAdjustments = Some("No"), assessmentCentreAdjustments = Some("No"),
          civilServiceExperiencesDetails = Some(CivilServiceExperienceDetailsForDiversityReport(
            isCivilServant = Some("Yes"), isEDIP = Some("Yes"), edipYear = Some("2018"), isSDIP = Some("Yes"), sdipYear = Some("2019"),
            otherInternship = Some("Yes"), otherInternshipName = Some("other"), otherInternshipYear = Some("2020"),
            fastPassCertificate = Some("1234567"))
          ),
          currentSchemeStatus = List(SchemeEvaluationResult(SchemeId("DiplomaticAndDevelopment"), EvaluationResults.Green.toString),
            SchemeEvaluationResult(SchemeId("GovernmentOperationalResearchService"), EvaluationResults.Green.toString))
        ),
        ApplicationForDiversityReport(
          applicationId = appId3, userId = userId3, applicationRoute = ApplicationRoute.Faststream, progress = Some("submitted"),
          schemes = List(SchemeId("DiplomaticAndDevelopment"), SchemeId("GovernmentOperationalResearchService")),
          disability = Some("Yes"), gis = Some(false), onlineAdjustments = Some("No"), assessmentCentreAdjustments = Some("Yes"),
          civilServiceExperiencesDetails = Some(CivilServiceExperienceDetailsForDiversityReport(
            isCivilServant = Some("Yes"), isEDIP = Some("Yes"), edipYear = Some("2018"), isSDIP = Some("Yes"), sdipYear = Some("2019"),
            otherInternship = Some("Yes"), otherInternshipName = Some("other"), otherInternshipYear = Some("2020"),
            fastPassCertificate = Some("1234567"))
          ),
          currentSchemeStatus = List(SchemeEvaluationResult(SchemeId("DiplomaticAndDevelopment"), EvaluationResults.Green.toString),
            SchemeEvaluationResult(SchemeId("GovernmentOperationalResearchService"), EvaluationResults.Green.toString))
        )
      )
    }
  }
*/
/*
  "non-submitted status" must {
    val emptyProgressResponse = ProgressResponse("1")

    "be true for non submitted progress" ignore {
      repository.isNonSubmittedStatus(emptyProgressResponse.copy(submitted = false, withdrawn = false)) mustBe true
    }

    "be false for withdrawn progress" ignore {
      repository.isNonSubmittedStatus(emptyProgressResponse.copy(submitted = true, withdrawn = true)) mustBe false
      repository.isNonSubmittedStatus(emptyProgressResponse.copy(submitted = false, withdrawn = true)) mustBe false
    }

    "be false for submitted but not withdrawn progress" ignore {
      repository.isNonSubmittedStatus(emptyProgressResponse.copy(submitted = true, withdrawn = false)) mustBe false
    }
  }
*/
/*
  "Adjustments report" must {
    "return a list of AdjustmentReports" ignore {
      val frameworkId = "FastStream-2016"

      lazy val testData = new TestDataMongoRepository(mongo)
      testData.createApplications(100).futureValue

      val listFut = repository.adjustmentReport(frameworkId)

      val result = Await.result(listFut, timeout)

      result mustBe a[List[_]]
      result must not be empty
      result.head mustBe a[AdjustmentReportItem]
      result.head.userId must not be empty
      result.head.applicationId must not be empty
    }

    // This test only works when run in isolation, change ignore to in for that
    "return candidate who requested online adjustments" ignore {
      val request = CreateCandidateRequest.create("SUBMITTED", None, None).copy(
        assistanceDetails = Some(AssistanceDetailsRequest(
          hasDisability = Some("Yes"),
          onlineAdjustments = Some(true),
          onlineAdjustmentsDescription = Some("I need a bigger screen")
        ))
      )

      testDataGeneratorService.createCandidates(1,
        getCandidateStatusGeneratorFactory.getGenerator, CreateCandidateData.apply("", request, dataFaker)
      )(HeaderCarrier(), EmptyRequestHeader)

      val result = repository.adjustmentReport(frameworkId).futureValue

      result mustBe a[List[_]]
      result must not be empty
      result.head mustBe a[AdjustmentReportItem]
      result.head.userId must not be empty
      result.head.applicationId must not be empty
      result.head.needsSupportForOnlineAssessmentDescription mustBe Some("I need a bigger screen")
    }

    // This test only works when run in isolation, change ignore to in for that
    "return candidate who requested at venue adjustments" ignore {
      val request = CreateCandidateRequest.create("SUBMITTED", None, None).copy(
        assistanceDetails = Some(AssistanceDetailsRequest(
          hasDisability = Some("Yes"),
          assessmentCentreAdjustments = Some(true),
          assessmentCentreAdjustmentsDescription = Some("I need a warm room and no sun light")
        ))
      )
      testDataGeneratorService.createCandidates(1,
        getCandidateStatusGeneratorFactory.getGenerator, CreateCandidateData.apply("", request, dataFaker)
      )(HeaderCarrier(), EmptyRequestHeader)

      val result = repository.adjustmentReport(frameworkId).futureValue

      result mustBe a[List[_]]
      result must not be empty
      result.head mustBe a[AdjustmentReportItem]
      result.head.userId must not be empty
      result.head.applicationId must not be empty
      result.head.needsSupportAtVenueDescription mustBe Some("I need a warm room and no sun light")
    }

    // This test only works when run in isolation, change ignore to in for that
    "return faststream candidate who did not requested adjustments but has adjustments confirmed" ignore {
      val request = CreateCandidateRequest.create("SUBMITTED", None, None).copy(
        assistanceDetails = Some(AssistanceDetailsRequest(
          hasDisability = Some("No"),
          assessmentCentreAdjustments = Some(false),
          onlineAdjustments = Some(false),
          setGis = Some(false)
        )),
        adjustmentInformation = Some(AdjustmentsRequest(
          adjustments = Some(List("other adjustments")),
          adjustmentsConfirmed = Some(true),
          etray = None,
          video = None)
        )
      )
      testDataGeneratorService.createCandidates(1,
        getCandidateStatusGeneratorFactory.getGenerator, CreateCandidateData.apply("", request, dataFaker)
      )(HeaderCarrier(), EmptyRequestHeader)

      val result = repository.adjustmentReport(frameworkId).futureValue

      result mustBe a[List[_]]
      result must not be empty
      result.head mustBe a[AdjustmentReportItem]
      result.head.userId must not be empty
      result.head.applicationId must not be empty
      result.head.adjustments mustBe Some(Adjustments(adjustments=Some(List("other adjustments")),
        adjustmentsConfirmed = Some(true), etray = None, video = None))
    }

    // This test only works when run in isolation, change ignore to in for that
    "do not return sdip candidate who requested adjustments and has confirmed adjustments" ignore {
      val request = CreateCandidateRequest.create("SUBMITTED", None, None).copy(
        statusData = StatusDataRequest(
          applicationStatus = "SUBMITTED",
          progressStatus = None,
          applicationRoute = Some(ApplicationRoute.Sdip.toString)),
        assistanceDetails = Some(AssistanceDetailsRequest(
          hasDisability = Some("Yes"),
          assessmentCentreAdjustments = Some(false),
          onlineAdjustments = Some(false),
          phoneAdjustments = Some(true),
          setGis = Some(false)
        )),
        adjustmentInformation = Some(AdjustmentsRequest(
          adjustments = Some(List("phone interview")),
          adjustmentsConfirmed = Some(true),
          etray = None,
          video = None)
        )
      )
      testDataGeneratorService.createCandidates(1,
        getCandidateStatusGeneratorFactory.getGenerator, CreateCandidateData.apply("", request, dataFaker)
      )(HeaderCarrier(), EmptyRequestHeader)

      val result = repository.adjustmentReport(frameworkId).futureValue

      result mustBe a[List[_]]
      result mustBe empty
    }
  }
*/
/*
  "Candidates for duplicate detection report" must {
    "return empty list when there is no candidates" ignore {
      val candidates = repository.candidatesForDuplicateDetectionReport.futureValue
      candidates mustBe empty
    }

    "return all candidates with personal-details" ignore {
      val user1 = UserApplicationProfile("1", ProgressResponse("1", submitted = true), SUBMITTED.key, "first1", "last1",
        factories.ITDateTimeFactoryMock.nowLocalDate, ApplicationRoute.Faststream)
      val user2 = UserApplicationProfile("2", ProgressResponse("2", submitted = true), SUBMITTED.key, "first2", "last2",
        factories.ITDateTimeFactoryMock.nowLocalDate, ApplicationRoute.Faststream)
      create(user1)
      create(user2)
      createWithoutPersonalDetails("3", PREVIEW.key)

      val candidates = repository.candidatesForDuplicateDetectionReport.futureValue
      candidates must contain theSameElementsAs List(
        user1.copy(latestProgressStatus = SUBMITTED.key.toLowerCase),
        user2.copy(latestProgressStatus = SUBMITTED.key.toLowerCase)
      )
    }
  }
*/
  private def create(application: UserApplicationProfile) = {
//    import repositories.BSONLocalDateHandler

/*
    repository.collection.insert(ordered = false).one(BSONDocument(
      "applicationId" -> application.userId,
      "applicationRoute" -> ApplicationRoute.Faststream,
      "userId" -> application.userId,
      "frameworkId" -> FrameworkId,
      "progress-status" -> BSONDocument(application.latestProgressStatus -> true),
      "personal-details" -> BSONDocument(
        "firstName" -> application.firstName,
        "lastName" -> application.lastName,
        "dateOfBirth" -> application.dateOfBirth
      )
    )).futureValue
 */
    ???
  }

  private def createWithoutPersonalDetails(userId: String, latestProgressStatus: String) = {
//    import repositories.BSONLocalDateHandler

/*
    repository.collection.insert(ordered = false).one(BSONDocument(
      "userId" -> userId,
      "frameworkId" -> FrameworkId,
      "progress-status" -> BSONDocument(latestProgressStatus -> true)
    )).futureValue
 */
    ???
  }
}
