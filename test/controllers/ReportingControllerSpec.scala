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

package controllers

import config.TestFixtureBase
import connectors.AuthProviderClient
import connectors.ExchangeObjects.Candidate
import mocks._
import mocks.application.DocumentRootInMemoryRepository
import model.Address
import model.PersistedObjects.ContactDetailsWithId
import model.report._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json.JsArray
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.application.GeneralApplicationRepository
import repositories.{ ApplicationAssessmentScoresRepository, ContactDetailsRepository, MediaRepository, QuestionnaireRepository, TestReportRepository }
import testkit.UnitWithAppSpec

import scala.concurrent.Future
import scala.language.postfixOps

class ReportingControllerSpec extends UnitWithAppSpec {

  class TestableReportingController extends ReportingController {
    override val appRepository: GeneralApplicationRepository = DocumentRootInMemoryRepository
    override val cdRepository: ContactDetailsRepository = new ContactDetailsInMemoryRepository
    override val authProviderClient: AuthProviderClient = mock[AuthProviderClient]
      override val questionnaireRepository: QuestionnaireRepository = QuestionnaireInMemoryRepository
    override val testReportRepository: TestReportRepository = TestReportInMemoryRepository
    override val assessmentScoresRepository: ApplicationAssessmentScoresRepository = ApplicationAssessmentScoresInMemoryRepository
    override val medRepository: MediaRepository = MediaInMemoryRepository
  }

  "Reporting controller create adjustment report" should {
    "return the adjustment report when we execute adjustment reports" in new TestFixture {
      val controller = new TestableReportingController {
        override val cdRepository = new ContactDetailsInMemoryRepository {
          override def findAll: Future[List[ContactDetailsWithId]] =
            Future.successful(List(
              ContactDetailsWithId("1", Address("First Line", None, None, None), Some("HP18 9DN"), "joe@bloggs.com", None),
              ContactDetailsWithId("2", Address("First Line", None, None, None), Some("HP18 9DN"), "joe@bloggs.com", None),
              ContactDetailsWithId("3", Address("First Line", None, None, None), Some("HP18 9DN"), "joe@bloggs.com", None)
            ))
        }
      }
      val result = controller.adjustmentReport(frameworkId)(createAdjustmentsReport(frameworkId)).run

      val finalResult = contentAsJson(result).as[JsArray].value

      finalResult mustBe a[Seq[_]]

      finalResult.size must be(3)
      val headValue = finalResult.head
      (headValue \ "email").asOpt[String] mustBe Some("joe@bloggs.com")
    }

    "return the adjustment report without contact details data" in new TestFixture {
      val controller = new TestableReportingController
      val result = controller.adjustmentReport(frameworkId)(createAdjustmentsReport(frameworkId)).run

      val finalResult = contentAsJson(result).as[JsArray].value

      finalResult mustBe a[Seq[_]]
      finalResult.size must be(3)

      val headValue = finalResult.head
      (headValue \ "email").asOpt[String] mustBe None
      (headValue \ "telephone").asOpt[String] mustBe None
    }

    "return no adjustments if there's no data on the server" in new TestFixture {
      val controller = new TestableReportingController {
        override val appRepository = new DocumentRootInMemoryRepository {
          override def adjustmentReport(frameworkId: String): Future[List[AdjustmentReportItem]] = {
            Future.successful(List.empty[AdjustmentReportItem])
          }
        }
      }
      val result = controller.adjustmentReport(frameworkId)(createAdjustmentsReport(frameworkId)).run

      val finalResult = contentAsJson(result).as[JsArray].value

      finalResult mustBe a[Seq[_]]
      finalResult mustBe empty
    }
  }

/*
  "Reporting controller create non-submitted applications report" should {
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
  "Assessment centre allocation report" should {
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
  "Assessment results report" should {
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
  "Successful candidates report" should {
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

    def createAdjustmentsReport(frameworkId: String) = {
      FakeRequest(Helpers.GET, controllers.routes.ReportingController.adjustmentReport(frameworkId).url, FakeHeaders(), "")
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
