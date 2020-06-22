/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers.report

import config.TestFixtureBase
import connectors.AuthProviderClient
import connectors.ExchangeObjects.{Candidate, UserAuthInfo}
import controllers.ReportingController
import model.EvaluationResults.Green
import model.persisted._
import model.persisted.assessor.{Assessor, AssessorStatus}
import model.persisted.eventschedules.SkillType
import model.report.onlinetestpassmark.TestResultsForOnlineTestPassMarkReportItemExamples
import model.report.{CandidateProgressReportItem, _}
import model.{Scheme, _}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json.{JsArray, Json}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest, Helpers}
import repositories.fsb._
import repositories.events._
import repositories.sift._
import repositories.csv._
import repositories._
import contactdetails.ContactDetailsRepository
import application._
import testkit.MockitoImplicits._
import testkit.UnitWithAppSpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class ReportingControllerSpec extends UnitWithAppSpec {

  val mockContactDetailsRepository: ContactDetailsRepository = mock[contactdetails.ContactDetailsRepository]
  val reportingRepositoryMock: ReportingRepository = mock[ReportingRepository]
  val authProviderClientMock: AuthProviderClient = mock[AuthProviderClient]
  val mockQuestionnaireRepository: QuestionnaireRepository = mock[QuestionnaireRepository]
  val mockMediaRepository: MediaRepository = mock[MediaRepository]

  class TestableReportingController extends ReportingController {
    override val reportingRepository: ReportingRepository = reportingRepositoryMock
    override val contactDetailsRepository: contactdetails.ContactDetailsRepository = mockContactDetailsRepository
    override val questionnaireRepository: QuestionnaireRepository = mockQuestionnaireRepository
    override val mediaRepository: MediaRepository = mockMediaRepository
    override val prevYearCandidatesDetailsRepository = mock[PreviousYearCandidatesDetailsRepository]
    override val authProviderClient: AuthProviderClient = mock[AuthProviderClient]
    override val assessorRepository: AssessorRepository = mock[AssessorRepository]
    override val eventsRepository: EventsRepository = mock[EventsRepository]
    override val assessorAllocationRepository: AssessorAllocationRepository = mock[AssessorAllocationRepository]
    override val assessmentScoresRepository: AssessmentScoresRepository = mock[AssessmentScoresRepository]
    override val applicationSiftRepository: ApplicationSiftRepository = mock[ApplicationSiftRepository]
    override val fsacIndicatorCSVRepository: FSACIndicatorCSVRepository = mock[FSACIndicatorCSVRepository]
    override val schemeRepo: SchemeRepository = mock[SchemeRepository]
    override val candidateAllocationRepo: CandidateAllocationRepository = mock[CandidateAllocationRepository]
    override val fsbRepository: FsbRepository = mock[FsbRepository]
    override val applicationRepository: GeneralApplicationRepository = mock[GeneralApplicationRepository]
  }

  "Reporting controller create adjustment report" must {
    "return the adjustment report when we execute adjustment reports" in new TestFixture {
      when(mockContactDetailsRepository.findAll).thenReturn(Future.successful(List(
        ContactDetailsWithId("1", Address("First Line", None, None, None), Some("HP18 9DN"), outsideUk = false, "joe@bloggs.com", None),
        ContactDetailsWithId("2", Address("First Line", None, None, None), Some("HP18 9DN"), outsideUk = false, "joe@bloggs.com", None),
        ContactDetailsWithId("3", Address("First Line", None, None, None), Some("HP18 9DN"), outsideUk = false, "joe@bloggs.com", None)
      )))
      when(mockReportingRepository.adjustmentReport(frameworkId)).thenReturn(SuccessfulAdjustmentReportResponse)
      val controller = new TestableReportingController
      val result = controller.adjustmentReport(frameworkId)(createAdjustmentsRequest(frameworkId)).run
      val finalResult = contentAsJson(result).as[JsArray].value

      finalResult mustBe a[Seq[_]]

      finalResult.size mustBe 3
      val headValue = finalResult.head
      (headValue \ "email").asOpt[String] mustBe Some("joe@bloggs.com")
    }

    "return the adjustment report without contact details data" in new TestFixture {
      val controller = new TestableReportingController
      when(controller.reportingRepository.adjustmentReport(frameworkId)).thenReturn(SuccessfulAdjustmentReportResponse)

      val result = controller.adjustmentReport(frameworkId)(createAdjustmentsRequest(frameworkId)).run

      val finalResult = contentAsJson(result).as[JsArray].value

      finalResult mustBe a[Seq[_]]
      finalResult.size mustBe 3

      val headValue = finalResult.head
      (headValue \ "email").asOpt[String] mustBe None
      (headValue \ "telephone").asOpt[String] mustBe None
    }

    "return no adjustments if there's no data on the server" in new TestFixture {
      val controller = new TestableReportingController
      when(controller.reportingRepository.adjustmentReport(frameworkId)).thenReturn(Future.successful(Nil))
      val result = controller.adjustmentReport(frameworkId)(createAdjustmentsRequest(frameworkId)).run

      val finalResult = contentAsJson(result).as[JsArray].value

      finalResult mustBe a[Seq[_]]
      finalResult mustBe empty
    }
  }

  "Reporting controller internship report" must {
    "return the report in a happy path scenario" in new TestFixture {
      val underTest = new TestableReportingController
      when(mockReportingRepository.applicationsForInternshipReport(frameworkId)).thenReturn(SuccessfulInternshipReportResponse)
      when(mockContactDetailsRepository.findByUserIds(any[List[String]])).thenReturn(SuccessfulFindByUserIdsResponse)

      val result = underTest.internshipReport(frameworkId)(internshipReportRequest(frameworkId)).run

      val json = contentAsJson(result).as[JsArray].value

      json mustBe a[Seq[_]]
      json.size mustBe 2

      val reportItem1 = json.head
      (reportItem1 \ "applicationRoute").asOpt[String] mustBe Some("Edip")
      (reportItem1 \ "progressStatus").asOpt[String] mustBe Some(s"${ProgressStatuses.PHASE1_TESTS_COMPLETED}")
      (reportItem1 \ "firstName").asOpt[String] mustBe Some("Joe")
      (reportItem1 \ "lastName").asOpt[String] mustBe Some("Bloggs")
      (reportItem1 \ "preferredName").asOpt[String] mustBe Some("Joey")
      (reportItem1 \ "email").asOpt[String] mustBe Some("joe.bloggs@test.com")
      (reportItem1 \ "guaranteedInterviewScheme").asOpt[String] mustBe Some("N")
      (reportItem1 \ "behaviouralTScore").asOpt[String] mustBe None
      (reportItem1 \ "situationalTScore").asOpt[String] mustBe None

      val reportItem2 = json.last
      (reportItem2 \ "applicationRoute").asOpt[String] mustBe Some("Sdip")
      (reportItem2 \ "progressStatus").asOpt[String] mustBe Some(s"${ProgressStatuses.PHASE1_TESTS_COMPLETED}")
      (reportItem2 \ "firstName").asOpt[String] mustBe Some("Bill")
      (reportItem2 \ "lastName").asOpt[String] mustBe Some("Bloggs")
      (reportItem2 \ "preferredName").asOpt[String] mustBe Some("Billy")
      (reportItem2 \ "email").asOpt[String] mustBe Some("bill.bloggs@test.com")
      (reportItem2 \ "guaranteedInterviewScheme").asOpt[String] mustBe Some("Y")
      (reportItem2 \ "behaviouralTScore").asOpt[String] mustBe Some("10.0")
      (reportItem2 \ "situationalTScore").asOpt[String] mustBe Some("11.0")
    }

    "throw an exception if no contact details are fetched" in new TestFixture {
      val underTest = new TestableReportingController
      when(mockReportingRepository.applicationsForInternshipReport(frameworkId)).thenReturn(SuccessfulInternshipReportResponse)
      when(mockContactDetailsRepository.findByUserIds(any[List[String]])).thenReturn(Future.successful(List.empty[ContactDetailsWithId]))

      val result = underTest.internshipReport(frameworkId)(internshipReportRequest(frameworkId)).run

      result.failed.futureValue.isInstanceOf[IllegalStateException] mustBe true
      result.failed.futureValue.getMessage mustBe "No contact details found for user Id = user1"
    }
  }

  "Reporting controller analytical schemes report" must {
    "return the analytical schemes report in a happy path scenario" in new TestFixture {
      val underTest = new TestableReportingController
      when(mockReportingRepository.applicationsForAnalyticalSchemesReport(frameworkId)).thenReturn(SuccessfulAnalyticalSchemesReportResponse)
      when(mockContactDetailsRepository.findByUserIds(any[List[String]])).thenReturn(SuccessfulFindByUserIdsResponse)

      val result = underTest.analyticalSchemesReport(frameworkId)(candidateProgressRequest(frameworkId)).run

      val json = contentAsJson(result).as[JsArray].value

      json mustBe a[Seq[_]]
      json.size mustBe 2

      val reportItem1 = json.head
      (reportItem1 \ "firstName").asOpt[String] mustBe Some("Joe")
      (reportItem1 \ "lastName").asOpt[String] mustBe Some("Bloggs")
      (reportItem1 \ "email").asOpt[String] mustBe Some("joe.bloggs@test.com")
      (reportItem1 \ "firstSchemePreference").asOpt[String] mustBe Some("GovernmentOperationalResearchService")
      (reportItem1 \ "guaranteedInterviewScheme").asOpt[String] mustBe Some("N")

      val reportItem2 = json(1)
      (reportItem2 \ "firstName").asOpt[String] mustBe Some("Bill")
      (reportItem2 \ "lastName").asOpt[String] mustBe Some("Bloggs")
      (reportItem2 \ "email").asOpt[String] mustBe Some("bill.bloggs@test.com")
      (reportItem2 \ "firstSchemePreference").asOpt[String] mustBe Some("GovernmentOperationalResearchService")
      (reportItem2 \ "guaranteedInterviewScheme").asOpt[String] mustBe Some("Y")
      (reportItem2 \ "behaviouralTScore").asOpt[String] mustBe Some("10.0")
      (reportItem2 \ "situationalTScore").asOpt[String] mustBe Some("11.0")
      (reportItem2 \ "etrayTScore").asOpt[String] mustBe Some("12.0")
      (reportItem2 \ "overallVideoInterviewScore").asOpt[String] mustBe Some("13.0")
    }

    "throw an exception if no contact details are fetched" in new TestFixture {
      val underTest = new TestableReportingController
      when(mockReportingRepository.applicationsForAnalyticalSchemesReport(frameworkId)).thenReturn(SuccessfulAnalyticalSchemesReportResponse)
      when(mockContactDetailsRepository.findByUserIds(any[List[String]])).thenReturn(Future.successful(List.empty[ContactDetailsWithId]))

      val result = underTest.analyticalSchemesReport(frameworkId)(candidateProgressRequest(frameworkId)).run

      result.failed.futureValue.isInstanceOf[IllegalStateException] mustBe true
      result.failed.futureValue.getMessage mustBe "No contact details found for user Id = user1"
    }
  }

  "Reporting controller create progress report" must {
    "return the progress report in an happy path scenario" in new TestFixture {
      val underTest = new TestableReportingController
      when(mockReportingRepository.candidateProgressReport(frameworkId)).thenReturn(SuccessfulProgressReportResponse)

      val response = underTest.candidateProgressReport(frameworkId)(candidateProgressRequest(frameworkId)).run

      val result = contentAsJson(response).as[List[CandidateProgressReportItem]]

      result.size must be(4)

      val user1 = result.head
      user1.userId mustBe "user1"
      user1.assessmentCentre mustBe Some("London")

      val user2 = result(1)
      user2.userId mustBe "user2"
      user2.assessmentCentre mustBe None

      val user3 = result(2)
      user3.userId mustBe "user3"
      user3.assessmentCentre mustBe None

      val user4 = result(3)
      user4.userId mustBe "user4"
      user4.assessmentCentre mustBe Some("Newcastle")
    }

    "return a failed future with the expected throwable when candidateProgressReport fails" in new TestFixture {
      val underTest = new TestableReportingController
      when(mockReportingRepository.candidateProgressReport(frameworkId)).thenReturn(GenericFailureResponse)

      val result = underTest.candidateProgressReport(frameworkId)(candidateProgressRequest(frameworkId)).run

      result.failed.futureValue mustBe Error
    }
  }


  "assessor allocation report" must {
    "return the allocation report when all data is present" in new TestFixture {
      val underTest = new TestableReportingController
      when(mockEventsRepository.findAll(any())(any[ExecutionContext]())).thenReturnAsync(
        List(
          EventExamples.e1,
          EventExamples.e2
        )
      )
      when(mockAssessorRepository.findAll(any())(any[ExecutionContext]())).thenReturnAsync(
        List(
          Assessor("userId1", None, List("ASSESSOR", "QUALITY_ASSURANCE_COORDINATOR"), Nil,
            civilServant = false, Set.empty, AssessorStatus.CREATED)
        )
      )
      when(mockAuthProviderClient.findByUserIds(any[Seq[String]]())(any[HeaderCarrier])).thenReturnAsync(
        Seq(
          Candidate(
            "Bob", "Smith", None, "bob@bob.com", None, "userId1", List("assessor")
          )
        )
      )
      when(mockAuthProviderClient.findAuthInfoByUserIds(any[Seq[String]]())(any[HeaderCarrier])).thenReturnAsync(
        Seq(
          UserAuthInfo("userId1", isActive = true, disabled = false, None, None)
        )
      )
      when(mockAssessorAllocationRepository.findAll(any())(any[ExecutionContext]())).thenReturnAsync(
        List(
          AssessorAllocation(
            "userId1", EventExamples.e1.id, AllocationStatuses.CONFIRMED, SkillType.ASSESSOR, "version1"
          )
        )
      )

      val response = underTest.assessorAllocationReport(FakeRequest())

      val result = contentAsJson(response).as[List[String]]

      result must have length 2

      val expectedHeaders = s"Assessor ID,Name,Role,Deactivated,Skills,Sift schemes,Email,Phone," +
        s"""Internal/External,"${EventExamples.e1.date.toString} (FSAC, London)","${EventExamples.e2.date.toString} (FSB, London)""""

      result.head mustBe expectedHeaders

      result(1) mustBe
        """"userId1","Bob Smith","assessor","NO","ASSESSOR, QUALITY_ASSURANCE_COORDINATOR","",
          |"bob@bob.com"," ","External","ASSESSOR (CONFIRMED)"," """".stripMargin.replaceAll("\n", "")
    }

    "numeric text extract report" must {
      "return candidates in sift_entered or ready who have a numeric test requirement, " +
        "ignoring candidates who do not meet this criteria" in new TestFixture {
        val underTest = new TestableReportingController

        mocksForNumericTestExtract

        val response = underTest.numericTestExtractReport()(FakeRequest())

        val result = contentAsJson(response).as[List[NumericTestExtractReportItem]]

        result must have length 1

        result.head mustBe NumericTestExtractReportItem(
          application = applicationForNumericSift,
          contactDetails = contactDetailsWithId,
          questionnaire = questionnaireReportItem
        )
      }
    }
  }

  trait TestFixture extends TestFixtureBase {
    val frameworkId = "FastStream-2016"

    val mockContactDetailsRepository: ContactDetailsRepository = mock[contactdetails.ContactDetailsRepository]
    val mockReportingRepository: ReportingRepository = mock[ReportingRepository]
    val mockAuthProviderClient: AuthProviderClient = mock[AuthProviderClient]
    val mockQuestionnaireRepository: QuestionnaireRepository = mock[QuestionnaireRepository]
    val mockAssessmentScoresRepository: AssessmentScoresRepository = mock[AssessmentScoresRepository]
    val mockMediaRepository: MediaRepository = mock[MediaRepository]
    val mockAssessorAllocationRepository = mock[AssessorAllocationRepository]
    val mockEventsRepository = mock[EventsRepository]
    val mockAssessorRepository = mock[AssessorRepository]
    val mockSchemeRepo = mock[SchemeRepository]
    val mockCandidateAllocationRepo = mock[CandidateAllocationRepository]
    val mockApplicationSiftRepo = mock[ApplicationSiftRepository]
    val mockFsbRepo = mock[FsbRepository]
    val mockAppRepo = mock[GeneralApplicationRepository]
    val mockPrevYearCandidatesDetailsRepo = mock[PreviousYearCandidatesDetailsRepository]

    class TestableReportingController extends ReportingController {
      override val reportingRepository: ReportingRepository = mockReportingRepository
      override val contactDetailsRepository: contactdetails.ContactDetailsRepository = mockContactDetailsRepository
      override val questionnaireRepository: QuestionnaireRepository = mockQuestionnaireRepository
      override val assessmentScoresRepository: AssessmentScoresRepository = mockAssessmentScoresRepository
      override val mediaRepository: MediaRepository = mockMediaRepository // MediaInMemoryRepository
      override val fsacIndicatorCSVRepository = FSACIndicatorCSVRepository
      override val authProviderClient: AuthProviderClient = mockAuthProviderClient
      override val eventsRepository = mockEventsRepository
      override val assessorRepository = mockAssessorRepository
      override val assessorAllocationRepository = mockAssessorAllocationRepository
      override val schemeRepo = mockSchemeRepo
      override val candidateAllocationRepo = mockCandidateAllocationRepo
      override val applicationSiftRepository = mockApplicationSiftRepo
      override val fsbRepository: FsbRepository = mockFsbRepo
      override val applicationRepository: GeneralApplicationRepository = mockAppRepo
      override val prevYearCandidatesDetailsRepository: PreviousYearCandidatesDetailsRepository = mockPrevYearCandidatesDetailsRepo
    }

    val contactDetailsWithId = ContactDetailsWithId(
      "userId1",
      Address("123, Fake Street"),
      Some("AB1 2CD"),
      outsideUk = false,
      "fake@user.com",
      Some("231456879")
    )

    val questionnaireReportItem = QuestionnaireReportItem(gender = None, sexualOrientation = None, ethnicity = None,
      isEnglishYourFirstLanguage = None, parentEmploymentStatus = None, parentOccupation = None, parentEmployedOrSelf = None,
      parentCompanySize = None, lowerSocioEconomicBackground = None, socioEconomicScore = "2.5", university = None)

    val applicationForNumericSift = ApplicationForNumericTestExtractReport(
      "userId1", "appId1",
      ApplicationRoute.Faststream, "Firstname", "Lastname", "PreferredName",
      ProgressStatuses.SIFT_ENTERED,
      schemes = List(SchemeId("Commercial"), SchemeId("Finance"), SchemeId("Generalist"), SchemeId("HumanResources")),
      disability = None, gis = None, onlineAdjustments = None, assessmentCentreAdjustments = None,
      testResults = TestResultsForOnlineTestPassMarkReportItemExamples.testResults1,
      currentSchemeStatus =
        List("Commercial", "Finance", "Generalist", "HumanResources").map(s => SchemeEvaluationResult(s, Green.toString))
    )

    val applicationForFormSift = ApplicationForNumericTestExtractReport(
      "userId2", "appId2",
      ApplicationRoute.Faststream, "Firstname", "Lastname", "PreferredName",
      ProgressStatuses.SIFT_ENTERED,
      schemes = List(SchemeId("Generalist")),
      disability = None, gis = None, onlineAdjustments = None, assessmentCentreAdjustments = None,
      testResults = TestResultsForOnlineTestPassMarkReportItemExamples.testResults1,
      currentSchemeStatus =
        List("Generalist").map(s => SchemeEvaluationResult(s, Green.toString))
    )

    val applicationForNoSift = ApplicationForNumericTestExtractReport(
      "userId3", "appId3",
      ApplicationRoute.Faststream, "Firstname", "Lastname", "PreferredName",
      ProgressStatuses.SIFT_ENTERED,
      schemes = List(SchemeId("HumanResources")),
      disability = None, gis = None, onlineAdjustments = None, assessmentCentreAdjustments = None,
      testResults = TestResultsForOnlineTestPassMarkReportItemExamples.testResults1,
      currentSchemeStatus =
        List("HumanResources").map(s => SchemeEvaluationResult(s, Green.toString))
    )

    def mocksForNumericTestExtract = {
      when(mockSchemeRepo.schemes).thenReturn(
        Seq(
          Scheme("Commercial", "COM", "Commercial", civilServantEligible = false, None, Some(SiftRequirement.NUMERIC_TEST),
            siftEvaluationRequired = true, fsbType = None, schemeGuide = None, None),
          Scheme("Finance", "FIN", "Finance", civilServantEligible = false, None, Some(SiftRequirement.NUMERIC_TEST),
            siftEvaluationRequired = true, fsbType = None, schemeGuide = None, None),
          Scheme("Generalist", "GCS", "Generalist", civilServantEligible = false, None, Some(SiftRequirement.FORM),
            siftEvaluationRequired = true, fsbType = None, schemeGuide = None, None),
          Scheme("HumanResources", "HRS", "Human Resources", civilServantEligible = false, None, None,
            siftEvaluationRequired = false, fsbType = None, schemeGuide = None, None)
        )
      )

      when(mockReportingRepository.numericTestExtractReport).thenReturnAsync(
        List(applicationForNumericSift, applicationForFormSift, applicationForNoSift)
      )

      when(mockContactDetailsRepository.findByUserIds(any())).thenReturnAsync(
        List(
          contactDetailsWithId,
          contactDetailsWithId.copy(userId = "userId2"),
          contactDetailsWithId.copy(userId = "userId3")
        )
      )

      when(mockQuestionnaireRepository.findForOnlineTestPassMarkReport(any())).thenReturnAsync(
        Map(
          "appId1" -> questionnaireReportItem,
          "appId2" -> questionnaireReportItem,
          "appId3" -> questionnaireReportItem
        )
      )
    }

    val SuccessfulAdjustmentReportResponse = Future.successful(
      List(
        AdjustmentReportItem(userId = "1", applicationId = Some("11"), firstName = Some("John"), lastName = Some("Smith"),
          preferredName = Some("Spiderman"), email = None, telephone = None, gis = Some("Yes"),
          disabilityCategories = None,
          otherDisabilityDescription = None,
          applicationStatus = Some(ApplicationStatus.SUBMITTED), needsSupportForOnlineAssessmentDescription = Some("Need help for online tests"),
          needsSupportAtVenueDescription = Some("Need help at the venue"),
          hasDisability = Some("Yes"), hasDisabilityDescription = Some("A wooden leg"),
          adjustments = Some(Adjustments(
            adjustments = Some(List("etrayTimeExtension")), adjustmentsConfirmed = Some(true),
            etray = Some(AdjustmentDetail(timeNeeded = Some(55))), video = None)
          ),
          adjustmentsComment = None
        ),
        AdjustmentReportItem(userId = "2", applicationId = Some("22"), firstName = Some("Jones"), lastName = Some("Batman"),
          preferredName = None,email =  None, telephone = None, gis = None,
          disabilityCategories = None,
          otherDisabilityDescription = None,
          applicationStatus = Some(ApplicationStatus.PHASE1_TESTS), needsSupportForOnlineAssessmentDescription = None,
          needsSupportAtVenueDescription = Some("Need help at the venue"), hasDisability = None, hasDisabilityDescription = None,
          adjustments = Some(Adjustments(
            adjustments = Some(List("etrayTimeExtension")), adjustmentsConfirmed = Some(true),
            etray = Some(AdjustmentDetail(timeNeeded = Some(55))), video = None)
          ),
          adjustmentsComment = None
        ),
        AdjustmentReportItem(userId = "3", applicationId = Some("33"), firstName = Some("Kathrine"), lastName = Some("Jones"),
          preferredName = Some("Supergirl"), email = None, telephone = None, gis = None,
          disabilityCategories = None,
          otherDisabilityDescription = None,
          applicationStatus = Some(ApplicationStatus.PHASE1_TESTS_PASSED),
          needsSupportForOnlineAssessmentDescription = Some("Need help for online tests"), needsSupportAtVenueDescription = None,
          hasDisability = Some("Yes"), hasDisabilityDescription = Some("A glass eye"),
          adjustments = Some(Adjustments(
            adjustments = Some(List("etrayTimeExtension")), adjustmentsConfirmed = Some(true),
            etray = Some(AdjustmentDetail(timeNeeded = Some(55))), video = None)
          ),
          adjustmentsComment = None
        )
      )
    )

    val SuccessfulProgressReportResponse = Future.successful(
      List(
        CandidateProgressReportItem("user1", "app1", Some("submitted"),
          List(SchemeId("DiplomaticService"), SchemeId("GovernmentOperationalResearchService")), disability = Some("Yes"),
          onlineAdjustments = Some("No"), assessmentCentreAdjustments = Some("No"), phoneAdjustments = None, gis = Some("No"),
          civilServant = Some("No"), edip = Some("No"), sdip = Some("No"), otherInternship = Some("No"),
          fastPassCertificate = Some("1234567"), assessmentCentre = Some("London"), ApplicationRoute.Faststream),
        CandidateProgressReportItem("user2", "app2", Some("registered"),
          List(SchemeId("DiplomaticService"), SchemeId("GovernmentOperationalResearchService")), disability = Some("Yes"),
          onlineAdjustments = Some("No"), assessmentCentreAdjustments = Some("No"), phoneAdjustments = None, gis = Some("No"),
          civilServant = Some("No"), edip = Some("No"), sdip = Some("No"), otherInternship = Some("No"),
          fastPassCertificate = Some("1234567"), assessmentCentre = None, ApplicationRoute.Faststream),
        CandidateProgressReportItem("user3", "app3", Some("submitted"),
          List(SchemeId("DiplomaticService"), SchemeId("GovernmentOperationalResearchService")), disability = Some("Yes"),
          onlineAdjustments = Some("No"), assessmentCentreAdjustments = Some("No"), phoneAdjustments = None, gis = Some("No"),
          civilServant = Some("No"), edip = Some("No"), sdip = Some("No"), otherInternship = Some("No"), fastPassCertificate = Some("1234567"),
          assessmentCentre = None, ApplicationRoute.Edip),
        CandidateProgressReportItem("user4", "app4", Some("submitted"),
          List(SchemeId("DiplomaticService"), SchemeId("GovernmentOperationalResearchService")), disability = Some("Yes"),
          onlineAdjustments = Some("No"), assessmentCentreAdjustments = Some("No"), phoneAdjustments = None, gis = Some("No"),
          civilServant = Some("No"), edip = Some("No"), sdip = Some("No"), otherInternship = Some("No"), fastPassCertificate = Some("1234567"),
          assessmentCentre = Some("Newcastle"), ApplicationRoute.Faststream)
      )
    )

    val SuccessfulInternshipReportResponse = Future.successful(
      List(
        ApplicationForInternshipReport(applicationRoute = ApplicationRoute.Edip, userId = "user1",
          progressStatus = Some(s"${ProgressStatuses.PHASE1_TESTS_COMPLETED}"),
          firstName = Some("Joe"), lastName = Some("Bloggs"),
          preferredName = Some("Joey"), guaranteedInterviewScheme = None, behaviouralTScore = None, situationalTScore = None),
        ApplicationForInternshipReport(applicationRoute = ApplicationRoute.Sdip, userId = "user2",
          progressStatus = Some(s"${ProgressStatuses.PHASE1_TESTS_COMPLETED}"),
          firstName = Some("Bill"), lastName = Some("Bloggs"), preferredName = Some("Billy"),
          guaranteedInterviewScheme = Some(true), behaviouralTScore = Some(10.0), situationalTScore = Some(11.0))
      )
    )

    val SuccessfulAnalyticalSchemesReportResponse = Future.successful(
      List(
        ApplicationForAnalyticalSchemesReport(userId = "user1", firstName = Some("Joe"), lastName = Some("Bloggs"),
          firstSchemePreference = Some("GovernmentOperationalResearchService"),
          guaranteedInterviewScheme = None, behaviouralTScore = None, situationalTScore = None, etrayTScore = None,
          overallVideoScore = None),
        ApplicationForAnalyticalSchemesReport(userId = "user2", firstName = Some("Bill"), lastName = Some("Bloggs"),
          firstSchemePreference = Some("GovernmentOperationalResearchService"),
          guaranteedInterviewScheme = Some(true), behaviouralTScore = Some(10.0), situationalTScore = Some(11.0),
          etrayTScore = Some(12.0), overallVideoScore = Some(13.0))
      )
    )

    val SuccessfulFindByUserIdsResponse = Future.successful(
      List(
        ContactDetailsWithId(userId = "user1", address = Address(line1 = "line1"), postCode = None,
          email = "joe.bloggs@test.com", outsideUk = false, phone = None),
        ContactDetailsWithId(userId = "user2", address = Address(line1 = "line1"), postCode = None,
          email = "bill.bloggs@test.com", outsideUk = false, phone = None)
      )
    )

    val SuccessfulFindAllPostCodeResponse = Future.successful(
      Map(
        "user1" -> "EH9 9ZZ",
        "user2" -> "N82 8QP",
        "user3" -> "N82 8QP"
      )
    )

    val Error = new RuntimeException("something bad happened")
    val GenericFailureResponse = Future.failed(Error)

    def createAdjustmentsRequest(frameworkId: String) = {
      FakeRequest(Helpers.GET, controllers.routes.ReportingController.adjustmentReport(frameworkId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }

    def candidateProgressRequest(frameworkId: String) = {
      FakeRequest(Helpers.GET, controllers.routes.ReportingController.candidateProgressReport(frameworkId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }

    def internshipReportRequest(frameworkId: String) = {
      FakeRequest(Helpers.GET, controllers.routes.ReportingController.internshipReport(frameworkId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }

    def analyticalSchemesReportRequest(frameworkId: String) = {
      FakeRequest(Helpers.GET, controllers.routes.ReportingController.analyticalSchemesReport(frameworkId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }

    when(mockAuthProviderClient.candidatesReport(any())).thenReturn(Future.successful(
      Candidate("firstName1", "lastName1", Some("preferredName1"), "email1@test.com", None, "user1", List("candidate")) ::
        Candidate("firstName2", "lastName2", None, "email2@test.com", None, "user2", List("candidate")) ::
        Nil
    ))

    when(mockContactDetailsRepository.findAll).thenReturn(Future.successful(List.empty))
  }
}
