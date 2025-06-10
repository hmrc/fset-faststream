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
import factories.{ITDateTimeFactoryMock, UUIDFactory}
import model.*
import model.ApplicationRoute.apply as _
import model.ProgressStatuses.{PREVIEW, SUBMITTED, PHASE1_TESTS_PASSED as _}
import model.command.ProgressResponse
import model.command.testdata.CreateCandidateRequest.{AdjustmentsRequest, AssistanceDetailsRequest, CreateCandidateRequest, StatusDataRequest}
import model.persisted.*
import model.report.{AdjustmentReportItem, CandidateProgressReportItem}
import model.testdata.candidate.CreateCandidateData.CreateCandidateData
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.{MongoCollection, SingleObservableFuture}
import repositories.CollectionNames
import services.GBTimeZoneService
import services.testdata.TestDataGeneratorService
import services.testdata.candidate.CandidateStatusGeneratorFactory
import services.testdata.faker.DataFaker
import testkit.MongoRepositorySpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.play.json.Codecs

import scala.concurrent.Await

class ReportingMongoRepositorySpec extends MongoRepositorySpec with UUIDFactory with Schemes {

  val frameworkId = "FastStream-2016"

  val collectionName = CollectionNames.APPLICATION

  def repository = new ReportingMongoRepository(
    new GBTimeZoneService, ITDateTimeFactoryMock, mongo, mock[MicroserviceAppConfig])

  def applicationRepo = new GeneralApplicationMongoRepository(ITDateTimeFactoryMock, appConfig, mongo)

  def testDataRepo = new TestDataMongoRepository(mongo)

  def testDataGeneratorService = app.injector.instanceOf(classOf[TestDataGeneratorService])

  def dataFaker = app.injector.instanceOf(classOf[DataFaker])

  implicit def blankedHeaderCarrier: HeaderCarrier = HeaderCarrier()

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
        schemes = List(DiplomaticAndDevelopment, GovernmentOperationalResearchService), disability = Some("Yes"),
        assessmentCentreAdjustments = Some("No"), phoneAdjustments = None, gis = Some("No"),
        civilServant = Some("Yes"), edip = Some("Yes"), sdip = Some("Yes"), otherInternship = Some("Yes"),
        fastPassCertificate = Some("1234567"), assessmentCentre = None, applicationRoute = ApplicationRoute.Faststream)
    }

    "work for the minimum application" in {
      val userId = generateUUID()
      val appId = generateUUID()
      testDataRepo.createMinimumApplication(userId, appId, "FastStream-2016").futureValue

      val result = repository.candidateProgressReport("FastStream-2016").futureValue

      result must not be empty
      result.head mustBe CandidateProgressReportItem(userId = userId, applicationId = appId, progress = Some("registered"),
        schemes = List.empty[SchemeId], disability = None, assessmentCentreAdjustments = None,
        phoneAdjustments = None, gis = None, civilServant = None, edip = None, sdip = None, otherInternship = None,
        fastPassCertificate = None, assessmentCentre = None, applicationRoute = ApplicationRoute.Faststream)
    }
  }

  "Diversity Report" must {
    "work for for the minimum application" in {
      val userId = generateUUID()
      val appId = generateUUID()
      testDataRepo.createMinimumApplication(userId, appId, "FastStream-2016").futureValue

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

    "work for for an application with all fields" in {
      val userId1 = generateUUID()
      val userId2 = generateUUID()
      val userId3 = generateUUID()
      val appId1 = generateUUID()
      val appId2 = generateUUID()
      val appId3 = generateUUID()
      val testAccountId1 = generateUUID()
      val testAccountId2 = generateUUID()
      val testAccountId3 = generateUUID()

      testDataRepo.createApplicationWithAllFields(userId1, appId1, testAccountId1,"FastStream-2016", guaranteedInterview = true).futureValue
      testDataRepo.createApplicationWithAllFields(userId2, appId2, testAccountId2,"FastStream-2016", hasDisability = "No").futureValue
      testDataRepo.createApplicationWithAllFields(userId3, appId3, testAccountId3,"FastStream-2016", needsSupportAtVenue = true).futureValue

      val result = repository.diversityReport("FastStream-2016").futureValue

      result must contain theSameElementsAs Seq(
        ApplicationForDiversityReport(
          applicationId = appId1, userId = userId1, applicationRoute = ApplicationRoute.Faststream, progress = Some("submitted"),
          schemes = List(DiplomaticAndDevelopment, GovernmentOperationalResearchService),
          disability = Some("Yes"), gis = Some(true), onlineAdjustments = None, assessmentCentreAdjustments = Some("No"),
          civilServiceExperiencesDetails = Some(CivilServiceExperienceDetailsForDiversityReport(
            isCivilServant = Some("Yes"), isEDIP = Some("Yes"), edipYear = Some("2018"), isSDIP = Some("Yes"), sdipYear = Some("2019"),
            otherInternship = Some("Yes"), otherInternshipName = Some("other"), otherInternshipYear = Some("2020"),
            fastPassCertificate = Some("1234567"))
          ),
          currentSchemeStatus = List(SchemeEvaluationResult(DiplomaticAndDevelopment, EvaluationResults.Green.toString),
            SchemeEvaluationResult(GovernmentOperationalResearchService, EvaluationResults.Green.toString))
        ),
        ApplicationForDiversityReport(
          applicationId = appId2, userId = userId2, applicationRoute = ApplicationRoute.Faststream, progress = Some("submitted"),
          schemes = List(DiplomaticAndDevelopment, GovernmentOperationalResearchService),
          disability = Some("Yes"), gis = Some(false), onlineAdjustments = None, assessmentCentreAdjustments = Some("No"),
          civilServiceExperiencesDetails = Some(CivilServiceExperienceDetailsForDiversityReport(
            isCivilServant = Some("Yes"), isEDIP = Some("Yes"), edipYear = Some("2018"), isSDIP = Some("Yes"), sdipYear = Some("2019"),
            otherInternship = Some("Yes"), otherInternshipName = Some("other"), otherInternshipYear = Some("2020"),
            fastPassCertificate = Some("1234567"))
          ),
          currentSchemeStatus = List(SchemeEvaluationResult(DiplomaticAndDevelopment, EvaluationResults.Green.toString),
            SchemeEvaluationResult(GovernmentOperationalResearchService, EvaluationResults.Green.toString))
        ),
        ApplicationForDiversityReport(
          applicationId = appId3, userId = userId3, applicationRoute = ApplicationRoute.Faststream, progress = Some("submitted"),
          schemes = List(DiplomaticAndDevelopment, GovernmentOperationalResearchService),
          disability = Some("Yes"), gis = Some(false), onlineAdjustments = None, assessmentCentreAdjustments = Some("Yes"),
          civilServiceExperiencesDetails = Some(CivilServiceExperienceDetailsForDiversityReport(
            isCivilServant = Some("Yes"), isEDIP = Some("Yes"), edipYear = Some("2018"), isSDIP = Some("Yes"), sdipYear = Some("2019"),
            otherInternship = Some("Yes"), otherInternshipName = Some("other"), otherInternshipYear = Some("2020"),
            fastPassCertificate = Some("1234567"))
          ),
          currentSchemeStatus = List(SchemeEvaluationResult(DiplomaticAndDevelopment, EvaluationResults.Green.toString),
            SchemeEvaluationResult(GovernmentOperationalResearchService, EvaluationResults.Green.toString))
        )
      )
    }
  }

  "non-submitted status" must {
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

  "Adjustments report" must {
    "return a list of AdjustmentReports" in {
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
    "return faststream candidate who did not request adjustments" ignore {
      val request = CreateCandidateRequest.create("SUBMITTED", None, None).copy(
        assistanceDetails = Some(AssistanceDetailsRequest(
          hasDisability = Some("No"),
          assessmentCentreAdjustments = Some(false),
          setGis = Some(false)
        )),
        adjustmentInformation = None
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

  "Candidates for duplicate detection report" must {
    "return empty list when there is no candidates" in {
      val candidates = repository.candidatesForDuplicateDetectionReport.futureValue
      candidates mustBe empty
    }

    "return all candidates with personal-details" in {
      val user1 = UserApplicationProfile("1", ProgressResponse("1", submitted = true), SUBMITTED.key, "first1", "last1",
        ITDateTimeFactoryMock.nowLocalDate, ApplicationRoute.Faststream)
      val user2 = UserApplicationProfile("2", ProgressResponse("2", submitted = true), SUBMITTED.key, "first2", "last2",
        ITDateTimeFactoryMock.nowLocalDate, ApplicationRoute.Faststream)
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

  private def create(application: UserApplicationProfile) = {
    val applicationCollection: MongoCollection[Document] = mongo.database.getCollection(collectionName)

    applicationCollection.insertOne(
      Document(
        "applicationId" -> application.userId,
        "applicationRoute" -> ApplicationRoute.Faststream.toBson,
        "userId" -> application.userId,
        "frameworkId" -> FrameworkId,
        "progress-status" -> Document(application.latestProgressStatus -> true),
        "personal-details" -> Document(
          "firstName" -> application.firstName,
          "lastName" -> application.lastName,
          "dateOfBirth" -> Codecs.toBson(application.dateOfBirth)
        )
      )
    ).toFuture().futureValue
  }

  private def createWithoutPersonalDetails(userId: String, latestProgressStatus: String) = {
    val applicationCollection: MongoCollection[Document] = mongo.database.getCollection(collectionName)

    applicationCollection.insertOne(
      Document(
        "userId" -> userId,
        "frameworkId" -> FrameworkId,
        "progress-status" -> Document(latestProgressStatus -> true)
      )
    ).toFuture().futureValue
  }
}
