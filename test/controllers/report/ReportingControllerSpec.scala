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

package controllers.report

import config.TestFixtureBase
import connectors.AuthProviderClient
import connectors.ExchangeObjects.Candidate
import controllers.ReportingController
import mocks._
import model._
import model.persisted.ContactDetailsWithId
import model.report.{ CandidateProgressReportItem, _ }
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json.{ JsArray, Json }
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.application.ReportingRepository
import repositories._
import repositories.contactdetails.ContactDetailsRepository
import repositories.csv.FSACIndicatorCSVRepository
import testkit.UnitWithAppSpec

import scala.concurrent.Future
import scala.language.postfixOps

class ReportingControllerSpec extends UnitWithAppSpec {

  val mockContactDetailsRepository: ContactDetailsRepository = mock[contactdetails.ContactDetailsRepository]
  val reportingRepositoryMock: ReportingRepository = mock[ReportingRepository]
  val authProviderClientMock: AuthProviderClient = mock[AuthProviderClient]

  class TestableReportingController extends ReportingController {
    override val reportingRepository: ReportingRepository = reportingRepositoryMock
    override val contactDetailsRepository: contactdetails.ContactDetailsRepository = mockContactDetailsRepository
    override val questionnaireRepository: QuestionnaireRepository = QuestionnaireInMemoryRepository
    override val fsacScoresRepository: FSACScoresRepository = FSACScoresRepositoryInMemoryRepository
    override val mediaRepository: MediaRepository = MediaInMemoryRepository
    override val fsacIndicatorCSVRepository = FSACIndicatorCSVRepository
    override val authProviderClient: AuthProviderClient = mock[AuthProviderClient]
  }

  "Reporting controller create adjustment report" must {
    "return the adjustment report when we execute adjustment reports" in new TestFixture {
      when(mockContactDetailsRepository.findAll).thenReturn(Future.successful(List(
        ContactDetailsWithId("1", Address("First Line", None, None, None), Some("HP18 9DN"), outsideUk = false, "joe@bloggs.com", None),
        ContactDetailsWithId("2", Address("First Line", None, None, None), Some("HP18 9DN"), outsideUk = false, "joe@bloggs.com", None),
        ContactDetailsWithId("3", Address("First Line", None, None, None), Some("HP18 9DN"), outsideUk = false, "joe@bloggs.com", None)
      )))
      when(reportingRepositoryMock.adjustmentReport(frameworkId)).thenReturn(SuccessfulAdjustmentReportResponse)
      val controller = new TestableReportingController
      val result = controller.adjustmentReport(frameworkId)(createAdjustmentsRequest(frameworkId)).run
      val finalResult = contentAsJson(result).as[JsArray].value

      finalResult mustBe a[Seq[_]]

      finalResult.size must be(3)
      val headValue = finalResult.head
      (headValue \ "email").asOpt[String] mustBe Some("joe@bloggs.com")
    }

    "return the adjustment report without contact details data" in new TestFixture {
      val controller = new TestableReportingController
      when(controller.reportingRepository.adjustmentReport(frameworkId)).thenReturn(SuccessfulAdjustmentReportResponse)

      val result = controller.adjustmentReport(frameworkId)(createAdjustmentsRequest(frameworkId)).run

      val finalResult = contentAsJson(result).as[JsArray].value

      finalResult mustBe a[Seq[_]]
      finalResult.size must be(3)

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

  "Reporting controller candidate deferral report" must {
    "return the report in a happy path" in new TestFixture {

      when(reportingRepositoryMock.candidateDeferralReport(any[String])).thenReturn(
        Future.successful(List(
        ApplicationDeferralPartialItem("userId1", "Bob", "Bobson", "prefBob", List("Police Now")),
        ApplicationDeferralPartialItem("userId2", "Dave", "Daveson", "prefDave", List("Teach First"))
      )))

      when(mockContactDetailsRepository.findAll).thenReturn(
        Future.successful(List(
          ContactDetailsWithId(userId = "userId1", address = Address("1 Test Street"), postCode = Some("QQ1 1QQ"), outsideUk = false,
            email = "blah@blah.com", phone = Some("07707717711")
          ),
            ContactDetailsWithId(userId = "userId2", address = Address("1 Fake Street"), postCode = Some("QQ1 1QQ"), outsideUk = false,
              email = "blah@blah.com", phone = Some("07707727722")
          )
      )))

      val controller = new TestableReportingController
      val result = controller.candidateDeferralReport("frameworkId")(candidateDeferralRequest("frameworkId")).run

      val expectedJson = Json.toJson(List(
        CandidateDeferralReportItem(
          "Bob Bobson", "prefBob", "blah@blah.com", Address("1 Test Street"), Some("QQ1 1QQ"), Some("07707717711"), List("Police Now")
        ),
        CandidateDeferralReportItem(
          "Dave Daveson", "prefDave", "blah@blah.com", Address("1 Fake Street"), Some("QQ1 1QQ"), Some("07707727722"), List("Teach First")
        )
      ))

      val json = contentAsJson(result)

      json mustBe expectedJson
    }
  }

  "Reporting controller internship report" must {
    "return the report in a happy path scenario" in new TestFixture {
      val underTest = new TestableReportingController
      when(reportingRepositoryMock.applicationsForInternshipReport(frameworkId)).thenReturn(SuccessfulInternshipReportResponse)
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
      when(reportingRepositoryMock.applicationsForInternshipReport(frameworkId)).thenReturn(SuccessfulInternshipReportResponse)
      when(mockContactDetailsRepository.findByUserIds(any[List[String]])).thenReturn(Future.successful(List.empty[ContactDetailsWithId]))

      val result = underTest.internshipReport(frameworkId)(internshipReportRequest(frameworkId)).run

      result.failed.futureValue.isInstanceOf[IllegalStateException] mustBe true
      result.failed.futureValue.getMessage mustBe "No contact details found for user Id = user1"
    }
  }

  "Reporting controller analytical schemes report" must {
    "return the analytical schemes report in a happy path scenario" in new TestFixture {
      val underTest = new TestableReportingController
      when(reportingRepositoryMock.applicationsForAnalyticalSchemesReport(frameworkId)).thenReturn(SuccessfulAnalyticalSchemesReportResponse)
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
      when(reportingRepositoryMock.applicationsForAnalyticalSchemesReport(frameworkId)).thenReturn(SuccessfulAnalyticalSchemesReportResponse)
      when(mockContactDetailsRepository.findByUserIds(any[List[String]])).thenReturn(Future.successful(List.empty[ContactDetailsWithId]))

      val result = underTest.analyticalSchemesReport(frameworkId)(candidateProgressRequest(frameworkId)).run

      result.failed.futureValue.isInstanceOf[IllegalStateException] mustBe true
      result.failed.futureValue.getMessage mustBe "No contact details found for user Id = user1"
    }
  }

  "Reporting controller create progress report" must {
    "return the progress report in an happy path scenario" in new TestFixture {
      val underTest = new TestableReportingController
      when(reportingRepositoryMock.candidateProgressReport(frameworkId)).thenReturn(SuccessfulProgressReportResponse)

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
      when(reportingRepositoryMock.candidateProgressReport(frameworkId)).thenReturn(GenericFailureResponse)

      val result = underTest.candidateProgressReport(frameworkId)(candidateProgressRequest(frameworkId)).run

      result.failed.futureValue mustBe Error
    }
  }

  /*
  "Reporting controller create non-submitted applications report" must {
    "return a list of non submitted applications with phone number if contact details exist" in new TestFixture {
      val controller = new ReportingController {
        override val appRepository = new DocumentRootInMemoryRepository
        //override val reportingRepository = ReportingInMemoryRepository
        override val cdRepository = new ContactDetailsInMemoryRepository {
          override def findAll: Future[List[ContactDetailsWithId]] = {
            Future.successful(ContactDetailsWithId(
              "user1",
              Address("First Line", None, None, None), Some("HP18 9DN"), "joe@bloggs.com", Some("123456789")
            ) :: Nil)
          }
        }
        override val authProviderClient: AuthProviderClient = authProviderClientMock
        override val questionnaireRepository = QuestionnaireInMemoryRepository
        override val testReportRepository = TestReportInMemoryRepository
        override val assessmentScoresRepository: ApplicationAssessmentScoresRepository = ApplicationAssessmentScoresInMemoryRepository
      }
      val result = controller.createNonSubmittedAppsReports(frameworkId)(createNonSubmittedAppsReportRequest(frameworkId)).run

      val finalResult = contentAsJson(result).as[List[PreferencesWithContactDetails]]

      finalResult must have size 2
      val reportApp1 = finalResult.head
      reportApp1.firstName must be(Some("firstName1"))
      reportApp1.lastName must be(Some("lastName1"))
      reportApp1.preferredName must be(Some("preferredName1"))
      reportApp1.email must be(Some("email1@test.com"))
      reportApp1.location1 must be(Some("location1"))
      reportApp1.location1Scheme1 must be(Some("location1Scheme1"))
      reportApp1.location1Scheme2 must be(Some("location1Scheme2"))
      reportApp1.location2 must be(Some("location2"))
      reportApp1.location2Scheme1 must be(Some("location2Scheme1"))
      reportApp1.location2Scheme2 must be(Some("location2Scheme2"))
    }

    "return only applications based on auth provider in registered state if there is no applications created" in new TestFixture {
      val controller = new ReportingController {
        override val appRepository = new DocumentRootInMemoryRepository {
          override def applicationsReport(frameworkId: String): Future[List[(String, IsNonSubmitted, PreferencesWithContactDetails)]] = {
            Future.successful(Nil)
          }
        }
        //override val reportingRepository = ReportingInMemoryRepository
        override val cdRepository = ContactDetailsInMemoryRepository
        override val authProviderClient = authProviderClientMock
        override val questionnaireRepository = QuestionnaireInMemoryRepository
        override val testReportRepository = TestReportInMemoryRepository
        override val assessmentScoresRepository: ApplicationAssessmentScoresRepository = ApplicationAssessmentScoresInMemoryRepository
      }
      val result = controller.createNonSubmittedAppsReports(frameworkId)(createNonSubmittedAppsReportRequest(frameworkId)).run

      val finalResult = contentAsJson(result).as[List[JsValue]]

      finalResult must have size 2
      finalResult.foreach { headValue =>
        (headValue \ "progress").asOpt[String] mustBe Some("registered")
      }
    }
  }
*/
  /*
  "Assessment centre allocation report" must {
    "return nothing if no applications exist" in new AssessmentCentreAllocationReportTestFixture {
      when(appRepo.candidatesAwaitingAllocation(any())).thenReturnAsync(Nil)
      when(cdRepo.findAll).thenReturnAsync(Nil)

      val response = controller.createAssessmentCentreAllocationReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[AssessmentCentreAllocationReport]]

      status(response) mustBe OK
      result mustBe empty
    }

    "return nothing if applications exist, but no contact details" in new AssessmentCentreAllocationReportTestFixture {
      when(appRepo.candidatesAwaitingAllocation(any())).thenReturnAsync(candidates)
      when(cdRepo.findAll).thenReturnAsync(Nil)

      val response = controller.createAssessmentCentreAllocationReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[AssessmentCentreAllocationReport]]

      status(response) mustBe OK
      result mustBe empty
    }

    "return applications with contact details" in new AssessmentCentreAllocationReportTestFixture {
      when(appRepo.candidatesAwaitingAllocation(any())).thenReturnAsync(candidates)
      when(cdRepo.findAll).thenReturnAsync(contactDetails)

      val response = controller.createAssessmentCentreAllocationReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[AssessmentCentreAllocationReport]]

      status(response) mustBe OK
      result mustBe List(
        toReport(candidate1, contactDetails1),
        toReport(candidate2, contactDetails2)
      )

      def toReport(candidate: CandidateAwaitingAllocation, contact: ContactDetailsWithId) = {
       import candidate._
       import contact._
        AssessmentCentreAllocationReport(firstName, lastName, preferredName, email, phone.getOrElse(""),
          preferredLocation1, adjustments, dateOfBirth)
      }

    }
  }*/

  /*
  "Assessment results report" must {
    "return results report" in new AssessmentResultsReportTestFixture {
      when(appRepo.applicationsWithAssessmentScoresAccepted(any())).thenReturnAsync(appPreferences)
      when(questionRepo.onlineTestPassMarkReport).thenReturnAsync(passMarks)
      when(assessmentScoresRepo.allScores).thenReturnAsync(scores)

      val response = controller.createAssessmentResultsReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[AssessmentResultsReport]]

      status(response) mustBe OK

      result mustBe List(AssessmentResultsReport(applicationPreference1, passMarks1, scores1))
    }

    "return nothing if no applications exist" in new AssessmentResultsReportTestFixture {
      when(appRepo.applicationsWithAssessmentScoresAccepted(any())).thenReturnAsync(Nil)
      when(questionRepo.onlineTestPassMarkReport).thenReturnAsync(passMarks)
      when(assessmentScoresRepo.allScores).thenReturnAsync(scores)

      val response = controller.createAssessmentResultsReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[AssessmentResultsReport]]

      status(response) mustBe OK

      result mustBe empty
    }

    "return nothing if no questionnaires exist" in new AssessmentResultsReportTestFixture {
      when(appRepo.applicationsWithAssessmentScoresAccepted(any())).thenReturnAsync(appPreferences)
      when(questionRepo.onlineTestPassMarkReport).thenReturnAsync(Map.empty)
      when(assessmentScoresRepo.allScores).thenReturnAsync(scores)

      val response = controller.createAssessmentResultsReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[AssessmentResultsReport]]

      status(response) mustBe OK

      result mustBe empty
    }

    "return nothing if no scores exist" in new AssessmentResultsReportTestFixture {
      when(appRepo.applicationsWithAssessmentScoresAccepted(any())).thenReturnAsync(appPreferences)
      when(questionRepo.onlineTestPassMarkReport).thenReturnAsync(passMarks)
      when(assessmentScoresRepo.allScores).thenReturnAsync(Map.empty)

      val response = controller.createAssessmentResultsReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[AssessmentResultsReport]]

      status(response) mustBe OK

      result mustBe empty
    }
  }
  */

  /*
  "Successful candidates report" must {
    "return results report" in new SuccessfulCandidatesReportTestFixture {
      when(appRepo.applicationsPassedInAssessmentCentre(any())).thenReturnAsync(appPreferences)
      when(cdRepo.findAll).thenReturnAsync(contactDetails)

      val response = controller.createSuccessfulCandidatesReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[AssessmentCentreCandidatesReport]]

      status(response) mustBe OK

      result mustBe List(AssessmentCentreCandidatesReport(applicationPreference1, phoneAndEmail))
    }

    "return nothing if no applications exist" in new SuccessfulCandidatesReportTestFixture {
      when(appRepo.applicationsPassedInAssessmentCentre(any())).thenReturnAsync(Nil)
      when(cdRepo.findAll).thenReturnAsync(contactDetails)

      val response = controller.createSuccessfulCandidatesReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[AssessmentCentreCandidatesReport]]

      status(response) mustBe OK

      result mustBe empty
    }

    "return nothing if no contact details exist" in new SuccessfulCandidatesReportTestFixture {
      when(appRepo.applicationsPassedInAssessmentCentre(any())).thenReturnAsync(appPreferences)
      when(cdRepo.findAll).thenReturnAsync(Nil)

      val response = controller.createSuccessfulCandidatesReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[AssessmentCentreCandidatesReport]]

      status(response) mustBe OK

      result mustBe empty
    }
  }
  */

  trait TestFixture extends TestFixtureBase {
    val frameworkId = "FastStream-2016"


    val SuccessfulAdjustmentReportResponse = Future.successful(
      List(
        AdjustmentReportItem("1", Some("11"), Some("John"), Some("Smith"), Some("Spiderman"), None, None, Some("Yes"),
          Some(ApplicationStatus.SUBMITTED), Some("Need help for online tests"), Some("Need help at the venue"),
          Some("Yes"), Some("A wooden leg"),
          Some(Adjustments(Some(List("etrayTimeExtension")), Some(true), Some(AdjustmentDetail(Some(55), None, None)), None)), None),
        AdjustmentReportItem("2", Some("22"), Some("Jones"), Some("Batman"), None, None, None, None,
          Some(ApplicationStatus.PHASE1_TESTS), None, Some("Need help at the venue"), None, None,
          Some(Adjustments(Some(List("etrayTimeExtension")), Some(true), Some(AdjustmentDetail(Some(55), None, None)), None)), None),
        AdjustmentReportItem("3", Some("33"), Some("Kathrine"), Some("Jones"), Some("Supergirl"), None, None, None,
          Some(ApplicationStatus.PHASE1_TESTS_PASSED), Some("Need help for online tests"), None,
          Some("Yes"), Some("A glass eye"),
          Some(Adjustments(Some(List("etrayTimeExtension")), Some(true), Some(AdjustmentDetail(Some(55), None, None)), None)), None)
      )
    )

    val SuccessfulProgressReportResponse = Future.successful(
      List(
        CandidateProgressReportItem("user1", "app1", Some("submitted"),
          List(SchemeId("DiplomaticService"), SchemeId("GovernmentOperationalResearchService")), Some("Yes"),
          Some("No"), Some("No"), None, Some("No"), Some("No"), Some("No"), Some("No"), Some("No"), Some("No"), Some("1234567"), Some("London"),
          ApplicationRoute.Faststream),
        CandidateProgressReportItem("user2", "app2", Some("registered"),
          List(SchemeId("DiplomaticService"), SchemeId("GovernmentOperationalResearchService")), Some("Yes"),
          Some("No"), Some("No"), None, Some("No"), Some("No"), Some("No"), Some("No"), Some("No"), Some("No"), Some("1234567"), None,
          ApplicationRoute.Faststream),
        CandidateProgressReportItem("user3", "app3", Some("submitted"),
          List(SchemeId("DiplomaticService"), SchemeId("GovernmentOperationalResearchService")), Some("Yes"),
          Some("No"), Some("No"), None, Some("No"), Some("No"), Some("No"), Some("No"), Some("No"), Some("No"), Some("1234567"), None,
          ApplicationRoute.Edip),
        CandidateProgressReportItem("user4", "app4", Some("submitted"),
          List(SchemeId("DiplomaticService"), SchemeId("GovernmentOperationalResearchService")), Some("Yes"),
          Some("No"), Some("No"), None, Some("No"), Some("No"), Some("No"), Some("No"), Some("No"), Some("No"), Some("1234567"), Some("Newcastle"),
          ApplicationRoute.Faststream)
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

    def candidateDeferralRequest(frameworkId: String) = {
      FakeRequest(Helpers.GET, controllers.routes.ReportingController.candidateDeferralReport(frameworkId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }

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

    /*
    def createNonSubmittedAppsReportRequest(frameworkId: String) = {
      FakeRequest(Helpers.GET, controllers.routes.ReportingController.createNonSubmittedAppsReports(frameworkId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }
    */

    val authProviderClientMock = mock[AuthProviderClient]
    when(authProviderClientMock.candidatesReport(any())).thenReturn(Future.successful(
      Candidate("firstName1", "lastName1", Some("preferredName1"), "email1@test.com", "user1") ::
        Candidate("firstName2", "lastName2", None, "email2@test.com", "user2") ::
        Nil
    ))

    when(mockContactDetailsRepository.findAll).thenReturn(Future.successful(List.empty))
  }

  /*
  trait SuccessfulCandidatesReportTestFixture extends TestFixture {
    val appRepo = mock[GeneralApplicationRepository]
    val questionRepo = mock[QuestionnaireRepository]
    val testResultRepo = mock[TestReportRepository]
    val assessmentScoresRepo = mock[ApplicationAssessmentScoresRepository]
    val cdRepo = mock[ContactDetailsRepository]
    val controller = new ReportingController {
      val appRepository = appRepo
      val reportingRepository = mock[ReportingRepository]
      val cdRepository = cdRepo
      val authProviderClient = mock[AuthProviderClient]
      val questionnaireRepository = questionRepo
      val testReportRepository = testResultRepo
      val assessmentScoresRepository = assessmentScoresRepo
    }

    val appId = rnd("appId")
    val userId = rnd("userId")

    lazy val applicationPreference1 = newAppPreferences
    lazy val contactDetails1 = newContactDetails

    lazy val appPreferences = List(applicationPreference1)
    lazy val contactDetails = List(contactDetails1)
    lazy val phoneAndEmail = newPhoneAndEmail(contactDetails1)
    lazy val summaryScores = CandidateScoresSummary(Some(10d), Some(10d), Some(10d),
      Some(10d), Some(10d), Some(10d), Some(20d), Some(80d))
    lazy val schemeEvaluations = SchemeEvaluation(Some("Pass"), Some("Fail"), Some("Amber"), Some("Pass"),
      Some("Fail"))

    private def someDouble = Some(Random.nextDouble())

    def newAppPreferences =
      ApplicationPreferencesWithTestResults(userId, appId, someRnd("location"), someRnd("location1scheme1-"),
        someRnd("location1scheme2-"), someRnd("location"), someRnd("location2scheme1-"), someRnd("location2scheme2-"),
        yesNoRnd, yesNoRnd,
        PersonalInfo(someRnd("firstname-"), someRnd("lastName-"), someRnd("preferredName-"), yesNoRnd, yesNoRnd),
        summaryScores, schemeEvaluations)

    def newContactDetails = ContactDetailsWithId(
      userId,
      Address(rnd("Line 1"), None, None, None),
      Some(rnd("PostCode")),
      rnd("Email"),
      someRnd("Phone")
    )

    def newPhoneAndEmail(cd: ContactDetailsWithId) = {
      PhoneAndEmail(cd.phone, Some(cd.email))
    }

    def request = {
      FakeRequest(Helpers.GET, controllers.routes.ReportingController.createSuccessfulCandidatesReport(frameworkId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }
  }
  */
  /*
  trait AssessmentCentreAllocationReportTestFixture extends TestFixture {
    val appRepo = mock[GeneralApplicationRepository]
    val cdRepo = mock[ContactDetailsRepository]
    val controller = new ReportingController {
      val appRepository = appRepo
      val reportingRepository = mock[ReportingRepository]
      val cdRepository = cdRepo
      val authProviderClient = mock[AuthProviderClient]
      val questionnaireRepository = mock[QuestionnaireRepository]
      val testReportRepository = mock[TestReportRepository]
      val assessmentScoresRepository = mock[ApplicationAssessmentScoresRepository]
    }

    lazy val candidate1 = newCandidate
    lazy val candidate2 = newCandidate
    lazy val candidates = List(candidate1, candidate2)

    lazy val contactDetails1 = newContactDetails(candidate1.userId)
    lazy val contactDetails2 = newContactDetails(candidate2.userId)
    lazy val contactDetails = List(contactDetails1, contactDetails2)

    def newCandidate = CandidateAwaitingAllocation(
      rnd("UserId"),
      rnd("FirstName"),
      rnd("LastName"),
      rnd("PreferredName"),
      rnd("PrefLocation1"),
      someRnd("Adjustments"),
      new LocalDate(2000, 1, 1)
    )

    def newContactDetails(id: String) = ContactDetailsWithId(
      id,
      Address(rnd("Line 1"), None, None, None),
      Some(rnd("PostCode")),
      rnd("Email"),
      someRnd("Phone")
    )

    def request = {
      FakeRequest(Helpers.GET, controllers.routes.ReportingController.createAssessmentCentreAllocationReport(frameworkId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }
  }
  */

  /*
  trait AssessmentResultsReportTestFixture extends TestFixture {
    val appRepo = mock[GeneralApplicationRepository]
    val questionRepo = mock[QuestionnaireRepository]
    val testResultRepo = mock[TestReportRepository]
    val assessmentScoresRepo = mock[ApplicationAssessmentScoresRepository]
    val controller = new ReportingController {
      val appRepository = appRepo
      val reportingRepository = mock[ReportingRepository]
      val cdRepository = mock[ContactDetailsRepository]
      val authProviderClient = mock[AuthProviderClient]
      val questionnaireRepository = questionRepo
      val testReportRepository = testResultRepo
      val assessmentScoresRepository = assessmentScoresRepo
    }

    val appId = rnd("appId")

    lazy val applicationPreference1 = newAppPreferences
    lazy val passMarks1 = newQuestionnaire
    lazy val scores1 = newScores

    lazy val appPreferences = List(applicationPreference1)
    lazy val passMarks = Map(appId -> passMarks1)
    lazy val scores = Map(appId -> scores1)

    private def someDouble = Some(Random.nextDouble())

    def newAppPreferences =
      ApplicationPreferences(rnd("userId"), appId, someRnd("location"), someRnd("location1scheme1-"),
        someRnd("location1scheme2-"), someRnd("location"), someRnd("location2scheme1-"), someRnd("location2scheme2-"),
        yesNoRnd, yesNoRnd, yesNoRnd, yesNoRnd, yesNoRnd, yesNoRnd, yesNoRnd,
        OnlineTestPassmarkEvaluationSchemes(Some("Pass"), Some("Fail"), Some("Pass"), Some("Fail"), Some("Amber")))

    def newQuestionnaire =
      QuestionnaireReportItem(someRnd("Gender"), someRnd("Orientation"), someRnd("Ethnicity"),
        someRnd("EmploymentStatus"), someRnd("Occupation"), someRnd("(Self)Employed"), someRnd("CompanySize"), rnd("SES"),
        someRnd("university"))

    def newScores = CandidateScoresAndFeedback(applicationId = appId, attendancy = maybe(true),
      assessmentIncomplete = false,
      leadingAndCommunicating = CandidateScores(someDouble, someDouble, someDouble),
      collaboratingAndPartnering = CandidateScores(someDouble, someDouble, someDouble),
      deliveringAtPace = CandidateScores(someDouble, someDouble, someDouble),
      makingEffectiveDecisions = CandidateScores(someDouble, someDouble, someDouble),
      changingAndImproving = CandidateScores(someDouble, someDouble, someDouble),
      buildingCapabilityForAll = CandidateScores(someDouble, someDouble, someDouble),
      motivationFit = CandidateScores(someDouble, someDouble, someDouble),
      feedback = CandidateScoreFeedback(someRnd("feedback"), someRnd("feedback"), someRnd("feedback")))

    def request = {
      FakeRequest(Helpers.GET, controllers.routes.ReportingController.createAssessmentResultsReport(frameworkId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }
  }
  */

}
