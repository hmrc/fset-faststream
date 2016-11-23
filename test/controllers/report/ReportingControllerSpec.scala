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

package controllers.report

import config.TestFixtureBase
import connectors.AuthProviderClient
import connectors.ExchangeObjects.Candidate
import controllers.ReportingController
import mocks._
import mocks.application.ReportingInMemoryRepository
import model._
import model.PersistedObjects.ContactDetailsWithId
import model.report.{ CandidateProgressReportItem, _ }
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json.JsArray
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.application.ReportingRepository
import repositories.{ ApplicationAssessmentScoresRepository, ContactDetailsRepository, MediaRepository, NorthSouthIndicatorCSVRepository, QuestionnaireRepository, contactdetails }
import testkit.UnitWithAppSpec

import scala.concurrent.Future
import scala.language.postfixOps

class ReportingControllerSpec extends UnitWithAppSpec {

  val reportRepositoryMock: ReportingRepository = mock[ReportingRepository]
  val fsCdRepositoryMock = mock[contactdetails.ContactDetailsRepository]
  val authProviderClientMock: AuthProviderClient = mock[AuthProviderClient]

  class TestableReportingController extends ReportingController {
    override val reportRepository = reportRepositoryMock
    override val cdRepository: ContactDetailsRepository = new ContactDetailsInMemoryRepository
    override val fsCdRepository = fsCdRepositoryMock
    override val authProviderClient = authProviderClientMock
    override val questionnaireRepository: QuestionnaireRepository = QuestionnaireInMemoryRepository
    override val assessmentScoresRepository: ApplicationAssessmentScoresRepository = ApplicationAssessmentScoresInMemoryRepository
    override val medRepository: MediaRepository = MediaInMemoryRepository
    override val indicatorRepository = NorthSouthIndicatorCSVRepository
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
      when(controller.reportRepository.adjustmentReport(frameworkId)).thenReturn(SuccessfulAdjustementReportResponse)

      val result = controller.adjustmentReport(frameworkId)(createAdjustmentsRequest(frameworkId)).run

      val finalResult = contentAsJson(result).as[JsArray].value

      finalResult mustBe a[Seq[_]]

      finalResult.size must be(3)
      val headValue = finalResult.head
      (headValue \ "email").asOpt[String] mustBe Some("joe@bloggs.com")
    }

    "return the adjustment report without contact details data" in new TestFixture {
      val controller = new TestableReportingController
      when(controller.reportRepository.adjustmentReport(frameworkId)).thenReturn(SuccessfulAdjustementReportResponse)

      val result = controller.adjustmentReport(frameworkId)(createAdjustmentsRequest(frameworkId)).run

      val finalResult = contentAsJson(result).as[JsArray].value

      finalResult mustBe a[Seq[_]]
      finalResult.size must be(3)

      val headValue = finalResult.head
      (headValue \ "email").asOpt[String] mustBe None
      (headValue \ "telephone").asOpt[String] mustBe None
    }

    "return no adjustments if there's no data on the server" in new TestFixture {
      val controller = new TestableReportingController {
        override val reportRepository = new ReportingInMemoryRepository {
          override def adjustmentReport(frameworkId: String): Future[List[AdjustmentReportItem]] = {
            Future.successful(List.empty[AdjustmentReportItem])
          }
        }
      }
      val result = controller.adjustmentReport(frameworkId)(createAdjustmentsRequest(frameworkId)).run

      val finalResult = contentAsJson(result).as[JsArray].value

      finalResult mustBe a[Seq[_]]
      finalResult mustBe empty
    }
  }

  "Reporting controller create progress report" should {
    "return the progress report in an happy path scenario" in new TestFixture {
      val underTest = new TestableReportingController
      when(reportRepositoryMock.candidateProgressReport(frameworkId)).thenReturn(SuccessfulProgressReportResponse)
      when(fsCdRepositoryMock.findAllPostCode()).thenReturn(SuccessfulFindAllPostCodeResponse)

      val result = underTest.candidateProgressReport(frameworkId)(candidateProgressRequest(frameworkId)).run

      val finalResult = contentAsJson(result).as[JsArray].value

      finalResult mustBe a[Seq[_]]
      finalResult.size must be(4)

      val user1 = finalResult(0)
      (user1 \ "userId").asOpt[String] mustBe Some("user1")
      (user1 \ "fsacIndicator").asOpt[String] mustBe Some("Newcastle")

      val user2 = finalResult(1) // because it's "registered"
      (user2 \ "userId").asOpt[String] mustBe Some("user2")
      (user2 \ "fsacIndicator").asOpt[String] mustBe None

      val user3 = finalResult(2) // because edip candidate
      (user3 \ "userId").asOpt[String] mustBe Some("user3")
      (user3 \ "fsacIndicator").asOpt[String] mustBe None

      val user4 = finalResult(3) // because with no postcode we use the default fsac (London)
      (user4 \ "userId").asOpt[String] mustBe Some("user4")
      (user4 \ "fsacIndicator").asOpt[String] mustBe Some("London")
    }
    "return a failed future with the expected throwable when candidateProgressReport fails" in new TestFixture {

      val underTest = new TestableReportingController
      when(reportRepositoryMock.candidateProgressReport(frameworkId)).thenReturn(GenericFailureResponse)
      when(fsCdRepositoryMock.findAllPostCode()).thenReturn(SuccessfulFindAllPostCodeResponse)

      val result = underTest.candidateProgressReport(frameworkId)(candidateProgressRequest(frameworkId)).run

      result.failed.futureValue mustBe Error

    }
    "return a failed future with the expected throwable when findAllPostCode fails" in new TestFixture {

      val underTest = new TestableReportingController
      when(reportRepositoryMock.candidateProgressReport(frameworkId)).thenReturn(SuccessfulProgressReportResponse)
      when(fsCdRepositoryMock.findAllPostCode()).thenReturn(GenericFailureResponse)

      val result = underTest.candidateProgressReport(frameworkId)(candidateProgressRequest(frameworkId)).run

      result.failed.futureValue mustBe Error

    }
  }

  trait TestFixture extends TestFixtureBase {
    val frameworkId = "FastStream-2016"

    val SuccessfulAdjustementReportResponse = Future.successful(
      List(
        AdjustmentReportItem("1", Some("11"), Some("John"), Some("Smith"), Some("Spiderman"), None, None, Some("Yes"),
          Some(ApplicationStatus.SUBMITTED), Some("Need help for online tests"), Some("Need help at the venue"),
          Some("Yes"), Some("A wooden leg"),
          Some(Adjustments(Some(List("etrayTimeExtension")),Some(true),Some(AdjustmentDetail(Some(55),None,None)),None)),None),
        AdjustmentReportItem("2", Some("22"), Some("Jones"), Some("Batman"), None, None, None, None,
          Some(ApplicationStatus.PHASE1_TESTS), None, Some("Need help at the venue"), None, None,
          Some(Adjustments(Some(List("etrayTimeExtension")),Some(true),Some(AdjustmentDetail(Some(55),None,None)),None)),None),
        AdjustmentReportItem("3", Some("33"), Some("Kathrine"), Some("Jones"), Some("Supergirl"), None, None, None,
          Some(ApplicationStatus.PHASE1_TESTS_PASSED), Some("Need help for online tests"), None,
          Some("Yes"), Some("A glass eye"),
          Some(Adjustments(Some(List("etrayTimeExtension")),Some(true),Some(AdjustmentDetail(Some(55),None,None)),None)),None)
      )
    )

    val SuccessfulProgressReportResponse = Future.successful(
      List(
        CandidateProgressReportItem("user1", "app1", Some("submitted"),
          List(SchemeType.DiplomaticService, SchemeType.GovernmentOperationalResearchService), Some("Yes"),
          Some("No"), Some("No"), Some("No"), Some("No"), Some("No"), Some("No"), Some("No"), Some("No"), Some("1234567"), None, None),
        CandidateProgressReportItem("user2", "app2", Some("registered"),
          List(SchemeType.DiplomaticService, SchemeType.GovernmentOperationalResearchService), Some("Yes"),
          Some("No"), Some("No"), Some("No"), Some("No"), Some("No"), Some("No"), Some("No"), Some("No"), Some("1234567"), None, Some("Faststream")),
        CandidateProgressReportItem("user3", "app3", Some("submitted"),
          List(SchemeType.DiplomaticService, SchemeType.GovernmentOperationalResearchService), Some("Yes"),
          Some("No"), Some("No"), Some("No"), Some("No"), Some("No"), Some("No"), Some("No"), Some("No"), Some("1234567"), None, Some("Edip")),
        CandidateProgressReportItem("user4", "app4", Some("submitted"),
          List(SchemeType.DiplomaticService, SchemeType.GovernmentOperationalResearchService), Some("Yes"),
          Some("No"), Some("No"), Some("No"), Some("No"), Some("No"), Some("No"), Some("No"), Some("No"), Some("1234567"), None, None)
      )
    )

    val SuccessfulFindAllPostCodeResponse = Future.successful(
      Map(
        "user1" -> "EH2 4AD",
        "user2" -> "N1 8QP",
        "user3" -> "N1 8QP"
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

    val authProviderClientMock = mock[AuthProviderClient]
    when(authProviderClientMock.candidatesReport(any())).thenReturn(Future.successful(
      Candidate("firstName1", "lastName1", Some("preferredName1"), "email1@test.com", "user1") ::
        Candidate("firstName2", "lastName2", None, "email2@test.com", "user2") ::
        Nil
    ))
  }

}
