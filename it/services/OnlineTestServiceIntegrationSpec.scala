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

package services

import _root_.services.onlinetesting.{CubiksSanitizer, OnlineTestService}
import config.MicroserviceAppConfig._
import connectors.{CSREmailClient, CubiksGatewayClient}
import factories.{DateTimeFactory, UUIDFactory}
import model.OnlineTestCommands._
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.time.{Seconds, Span}
import play.api.test.WithApplication
import play.modules.reactivemongo.ReactiveMongoPlugin
import reactivemongo.api.DefaultDB
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers._
import reactivemongo.json.collection.JSONCollection
import repositories._
import repositories.application.GeneralApplicationMongoRepository
import testkit.IntegrationSpec
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.io.Source

class OnlineTestServiceIntegrationSpec extends IntegrationSpec with MockitoSugar {

  private implicit def db: () => DefaultDB = {
    import play.api.Play.current
    ReactiveMongoPlugin.mongoConnector.db
  }

  import scala.concurrent.ExecutionContext.Implicits.global

  override implicit def patienceConfig = PatienceConfig(timeout = scaled(Span(15, Seconds)))

  def readXml = {
    Source.fromURL(getClass getResource "/test-resources/cubiksReport.xml").mkString
  }
  val auditMock = mock[AuditService]

  val gatewayClientMock = mock[CubiksGatewayClient]

  when(gatewayClientMock.getReport(any[OnlineTestApplicationForReportRetrieving])(any[HeaderCarrier]))
    .thenReturn(Future.successful(OnlineTestReportAvailability(1, false)))

  when(gatewayClientMock.downloadXmlReport(any[Int])(any[HeaderCarrier]))
    .thenReturn(Future.successful {
      val VerbalTestName = "Logiks Verbal and Numerical - Verbal"
      val NumericalTestName = "Logiks Verbal and Numerical - Numerical"
      val CompetencyTestName = "Cubiks Factors"
      val SituationalTestName = "Civil Service Fast Track Apprentice SJQ"

      Map(
        CompetencyTestName -> TestResult("Completed", "competency norm", Some(20.1d), Some(20.2d), Some(20.3d), Some(20.4d)),
        NumericalTestName -> TestResult("Completed", "numerical norm", Some(30.1d), Some(30.2d), Some(30.3d), Some(30.4d)),
        VerbalTestName -> TestResult("Completed", "verbal norm", Some(40.1d), Some(40.2d), Some(40.3d), Some(40.4d)),
        SituationalTestName -> TestResult("Completed", "situational norm", Some(50.1d), Some(50.2d), Some(50.3d), Some(50.4d))
      )}
    )

  val gatewayFailingClientMock = mock[CubiksGatewayClient]

  when(gatewayFailingClientMock.getReport(any[OnlineTestApplicationForReportRetrieving])(any[HeaderCarrier]))
    .thenReturn(Future.successful(OnlineTestReportAvailability(1, false)))

  when(gatewayFailingClientMock.downloadXmlReport(any[Int])(any[HeaderCarrier]))
    .thenReturn(Future.successful {
      val CompetencyTestName = "Cubiks Factors"
      val SituationalTestName = "Civil Service Fast Track Apprentice SJQ"

      Map(
        CompetencyTestName -> TestResult("Completed", "competency norm", Some(20.1d), Some(20.2d), Some(20.3d), Some(20.4d)),
        SituationalTestName -> TestResult("Completed", "situational norm", Some(50.1d), Some(50.2d), Some(50.3d), Some(50.4d))
      )}
    )


  lazy val service = new OnlineTestService {
    val appRepository = applicationRepository
    val cdRepository = contactDetailsRepository
    val otRepository = onlineTestRepository
    val otprRepository = onlineTestPDFReportRepository
    val trRepository = testReportRepository
    val cubiksGatewayClient = gatewayClientMock
    val cubiksSanitizer = CubiksSanitizer
    val tokenFactory = UUIDFactory
    val onlineTestInvitationDateFactory = DateTimeFactory
    val emailClient = CSREmailClient
    val auditService = auditMock
    val gatewayConfig = cubiksGatewayConfig
  }

  lazy val failingService = new OnlineTestService {
    val appRepository = applicationRepository
    val cdRepository = contactDetailsRepository
    val otRepository = onlineTestRepository
    val otprRepository = onlineTestPDFReportRepository
    val trRepository = testReportRepository
    val cubiksGatewayClient = gatewayFailingClientMock
    val cubiksSanitizer = CubiksSanitizer
    val tokenFactory = UUIDFactory
    val onlineTestInvitationDateFactory = DateTimeFactory
    val emailClient = CSREmailClient
    val auditService = auditMock
    val gatewayConfig = cubiksGatewayConfig
  }

  "Online test service" should {
    "retrieve online test result" in new WithApplication {
      clearDatabase()
      val application = mock[OnlineTestApplicationWithCubiksUser]
      when(application.applicationId).thenReturn("appId")
      when(application.userId).thenReturn("userId")

      createApplication("appId", "userId", "frameworkId", "CREATED")
      service.retrieveTestResult(application, waitSecs = Some(0)).futureValue

      val reportOpt = service.trRepository.getReportByApplicationId("appId").futureValue

      reportOpt must not be None
      val report = reportOpt.get

      report.numerical mustBe Some(TestResult("Completed", "numerical norm", Some(30.1d), Some(30.2d), Some(30.3d), Some(30.4d)))
      report.verbal mustBe Some(TestResult("Completed", "verbal norm", Some(40.1d), Some(40.2d), Some(40.3d), Some(40.4d)))
      report.situational mustBe Some(TestResult("Completed", "situational norm", Some(50.1d), Some(50.2d), Some(50.3d), Some(50.4d)))
      report.competency mustBe Some(TestResult("Completed", "competency norm", Some(20.1d), Some(20.2d), Some(20.3d), Some(20.4d)))

      report.reportType mustBe "XML"

      verify(auditMock).logEventNoRequest("OnlineTestXmlReportSaved", Map("userId" -> "userId"))
    }

    // TODO: Broken test. This works in isolation, but somehow the mongo connections get reset if run after another test
//    "expect exception" in new WithApplication {
//      pending
//      clearDatabase()
//      val application = mock[OnlineTestApplicationWithCubiksUser]
//      when(application.applicationId).thenReturn("appId2")
//      when(application.userId).thenReturn("userId2")
//      when(application.cubiksUserId).thenReturn(111)
//
//      createApplication("appId2", "userId2", "frameworkId", "CREATED")
//
//
//      private val exception = failingService.retrieveTestResult(application).failed.futureValue
//      exception mustBe a[IllegalStateException]
//
//      Logger.info(exception.getMessage)
//      exception.getMessage mustBe "Cubiks report 1 does not have the required amount of " +
//        "tests in the payload for Cubiks User ID:111 and applicationId:appId2"
//
//    }
  }

  def helperRepo = new GeneralApplicationMongoRepository(GBTimeZoneService)

  def createApplication(appId: String, userId: String, frameworkId: String, appStatus: String) = {
    helperRepo.collection.insert(BSONDocument(
      "userId" -> userId,
      "frameworkId" -> frameworkId,
      "applicationId" -> appId,
      "applicationStatus" -> appStatus,
      "personal-details" -> BSONDocument("preferredName" -> "Test Preferred Name"),
      "assistance-details" -> BSONDocument(
        "needsAdjustment" -> "No"
      )
    )).futureValue
  }

  def clearDatabase() = {
    val reportCollection = db().collection[JSONCollection]("online-test-report")
    reportCollection.drop().futureValue
    val collection = db().collection[JSONCollection]("application")
    collection.drop().futureValue
  }

}
