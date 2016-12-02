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

import _root_.services.testdata.{ StatusGeneratorFactory, TestDataGeneratorService }
import factories.UUIDFactory
import model.ProgressStatuses.{ PHASE1_TESTS_PASSED => _, SUBMITTED => _ }
import model.SchemeType.SchemeType
import model._
import model.report.{ AdjustmentReportItem, CandidateProgressReportItem }
import services.GBTimeZoneService
import config.MicroserviceAppConfig._
import model.ApplicationRoute.{ apply => _ }
import model.command.testdata.GeneratorConfig
import model.command.{ ProgressResponse, WithdrawApplication }
import model.exchange.testdata.{ AssistanceDetailsRequest, CreateCandidateInStatusRequest, StatusDataRequest }
import model.persisted._
import repositories.CommonBSONDocuments
import testkit.MongoRepositorySpec
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Await

class ReportingMongoRepositorySpec extends MongoRepositorySpec with UUIDFactory with CommonBSONDocuments {

  val frameworkId = "FastStream-2016"

  val collectionName = "application"

  def repository = new ReportingMongoRepository(GBTimeZoneService)

  def applicationRepo = new GeneralApplicationMongoRepository(GBTimeZoneService, cubiksGatewayConfig)

  def testDataRepo = new TestDataMongoRepository()

  val testDataGeneratorService = TestDataGeneratorService

  implicit def blankedHeaderCarrier = new HeaderCarrier()

  "Candidate Progress Report" should {
    "for an application with all fields" in {
      val userId = generateUUID()
      val appId = generateUUID()
      testDataRepo.createApplicationWithAllFields(userId, appId, "FastStream-2016").futureValue

      val result = repository.candidateProgressReport("FastStream-2016").futureValue

      result must not be empty
      result.head mustBe CandidateProgressReportItem(userId, appId, Some("submitted"),
        List(SchemeType.DiplomaticService, SchemeType.GovernmentOperationalResearchService), Some("Yes"),
        Some("No"), Some("No"), Some("No"), Some("Yes"), Some("No"), Some("Yes"), Some("No"), Some("Yes"), Some("1234567"), None, None)
    }

    "for the minimum application" in {
      val userId = generateUUID()
      val appId = generateUUID()
      testDataRepo.createMinimumApplication(userId, appId, "FastStream-2016").futureValue

      val result = repository.candidateProgressReport("FastStream-2016").futureValue

      result must not be empty
      result.head must be(CandidateProgressReportItem(userId, appId, Some("registered"),
        List.empty[SchemeType], None, None, None, None, None, None, None, None, None, None, None, None)
      )
    }
  }

  "Diversity Report" should {
    "for the minimum application" in {
      val userId = generateUUID()
      val appId = generateUUID()
      testDataRepo.createMinimumApplication(userId, appId, "FastStream-2016").futureValue

      val result = repository.diversityReport("FastStream-2016").futureValue

      result must not be empty
      result.head mustBe ApplicationForDiversityReport(appId, userId, Some("registered"),
        List.empty, None, None, None, None, None)
    }

    "for an application with all fields" in {
      val userId1 = generateUUID()
      val userId2 = generateUUID()
      val userId3 = generateUUID()
      val appId1 = generateUUID()
      val appId2 = generateUUID()
      val appId3 = generateUUID()

      testDataRepo.createApplicationWithAllFields(userId1, appId1, "FastStream-2016", guaranteedInterview = true,
        needsSupportForOnlineAssessment = true).futureValue
      testDataRepo.createApplicationWithAllFields(userId2, appId2, "FastStream-2016", hasDisability = "No").futureValue
      testDataRepo.createApplicationWithAllFields(userId3, appId3, "FastStream-2016", needsSupportAtVenue = true).futureValue

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


  "Applications report" should {
    "return an empty list when there are no applications" in {
      repository.applicationsReport(frameworkId).futureValue mustBe empty
    }

    "return a list of non submitted applications when there are only non submitted applications" in {
      Await.ready({
        for {
          _ <- applicationRepo.create("userId1", frameworkId, ApplicationRoute.Faststream)
          _ <- applicationRepo.create("userId2", frameworkId, ApplicationRoute.Faststream)
        } yield {
          Unit
        }
      }, timeout)

      val results = repository.applicationsReport(frameworkId).futureValue
      results must have size 2
      results.foreach { case (userId, isNonSubmitted, _) =>
        isNonSubmitted mustBe true
        userId must startWith("userId")
      }
    }

    "return only submitted applications" in {
      Await.ready({
        for {
          app <- applicationRepo.create("userId1", frameworkId, ApplicationRoute.Faststream)
          _ <- applicationRepo.addProgressStatusAndUpdateAppStatus(app.applicationId, ProgressStatuses.PREVIEW)
          _ <- applicationRepo.submit(app.applicationId)
          app2 <- applicationRepo.create("userId2", frameworkId, ApplicationRoute.Faststream)
          _ <- applicationRepo.addProgressStatusAndUpdateAppStatus(app2.applicationId, ProgressStatuses.PREVIEW)
          _ <- applicationRepo.submit(app2.applicationId)
        } yield {
          Unit
        }
      }, timeout)

      val results = repository.applicationsReport(frameworkId).futureValue
      results must have size 2
      results.foreach { case (_, isNonSubmitted, _) =>
        isNonSubmitted mustBe false
      }
    }

    "return only the applications in a specific framework id" in {
      Await.ready({
        for {
          app <- applicationRepo.create("userId1", frameworkId, ApplicationRoute.Faststream)
          app2 <- applicationRepo.create("userId2", "otherFramework", ApplicationRoute.Faststream)
        } yield {
          Unit
        }
      }, timeout)

      val results = repository.applicationsReport(frameworkId).futureValue
      results must have size 1
    }

    "return a list of non submitted applications with submitted applications" in {
      Await.ready({
        for {
          app1 <- applicationRepo.create("userId1", frameworkId, ApplicationRoute.Faststream)
          _ <- applicationRepo.addProgressStatusAndUpdateAppStatus(app1.applicationId, ProgressStatuses.PREVIEW)
          _ <- applicationRepo.submit(app1.applicationId)
          _ <- applicationRepo.create("userId2", frameworkId, ApplicationRoute.Faststream)
          app3 <- applicationRepo.create("userId3", frameworkId, ApplicationRoute.Faststream)
          _ <- applicationRepo.addProgressStatusAndUpdateAppStatus(app3.applicationId, ProgressStatuses.PREVIEW)
          _ <- applicationRepo.submit(app3.applicationId)
          app4 <- applicationRepo.create("userId4", frameworkId, ApplicationRoute.Faststream)
          _ <- applicationRepo.addProgressStatusAndUpdateAppStatus(app4.applicationId, ProgressStatuses.PREVIEW)
          _ <- applicationRepo.submit(app4.applicationId)
          _ <- applicationRepo.create("userId5", frameworkId, ApplicationRoute.Faststream)
        } yield {
          Unit
        }
      }, timeout)

      val results = repository.applicationsReport(frameworkId).futureValue
      results must have size 5
      results.filter { case (_, isNonSubmitted, _) => isNonSubmitted } must have size 2
      results.filter { case (_, isNonSubmitted, _) => !isNonSubmitted } must have size 3
    }
  }

  "Adjustments report" should {
    "return a list of AdjustmentReports" in {
      val frameworkId = "FastStream-2016"

      lazy val testData = new TestDataMongoRepository()
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
      val request = CreateCandidateInStatusRequest.create("SUBMITTED", None, None).copy(
        assistanceDetails = Some(AssistanceDetailsRequest(
          hasDisability = Some("Yes"),
          onlineAdjustments = Some(true),
          onlineAdjustmentsDescription = Some("I need a bigger screen")
        ))
      )

      testDataGeneratorService.createCandidatesInSpecificStatus(1, StatusGeneratorFactory.getGenerator, (GeneratorConfig.apply("", request))
      )(new HeaderCarrier(), EmptyRequestHeader)

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
      val request = CreateCandidateInStatusRequest.create("SUBMITTED", None, None).copy(
        assistanceDetails = Some(AssistanceDetailsRequest(
          hasDisability = Some("Yes"),
          assessmentCentreAdjustments = Some(true),
          assessmentCentreAdjustmentsDescription = Some("I need a warm room and no sun light")
        ))
      )
      testDataGeneratorService.createCandidatesInSpecificStatus(1, StatusGeneratorFactory.getGenerator, (GeneratorConfig.apply("", request))
      )(new HeaderCarrier(), EmptyRequestHeader)

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
      val request = CreateCandidateInStatusRequest.create("SUBMITTED", None, None).copy(
        assistanceDetails = Some(AssistanceDetailsRequest(
          hasDisability = Some("No"),
          assessmentCentreAdjustments = Some(false),
          onlineAdjustments = Some(false),
          setGis = Some(false)
        )),
        adjustmentInformation = Some(Adjustments(
          adjustments = Some(List("other adjustments")),
          adjustmentsConfirmed = Some(true),
          etray = None,
          video = None)
        )
      )
      testDataGeneratorService.createCandidatesInSpecificStatus(1, StatusGeneratorFactory.getGenerator, (GeneratorConfig.apply("", request))
      )(new HeaderCarrier(), EmptyRequestHeader)

      val result = repository.adjustmentReport(frameworkId).futureValue

      result mustBe a[List[_]]
      result must not be empty
      result.head mustBe a[AdjustmentReportItem]
      result.head.userId must not be empty
      result.head.applicationId must not be empty
      result.head.adjustments  mustBe Some(Adjustments(adjustments=Some(List("other adjustments")), adjustmentsConfirmed = Some(true), etray = None, video = None))
    }

    // This test only works when run in isolation, change ignore to in for that
    "do not return sdip candidate who requested adjustments and has confirmed adjustments" ignore {
      val request = CreateCandidateInStatusRequest.create("SUBMITTED", None, None).copy(
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
        adjustmentInformation = Some(Adjustments(
          adjustments = Some(List("phone interview")),
          adjustmentsConfirmed = Some(true),
          etray = None,
          video = None)
        )
      )
      testDataGeneratorService.createCandidatesInSpecificStatus(1, StatusGeneratorFactory.getGenerator, (GeneratorConfig.apply("", request))
      )(new HeaderCarrier(), EmptyRequestHeader)

      val result = repository.adjustmentReport(frameworkId).futureValue

      result mustBe a[List[_]]
      result mustBe empty
    }
  }

  "manual assessment centre allocation report" should {
    "return all candidates that are in awaiting allocation state" in {
      val testData = new TestDataMongoRepository()
      testData.createApplications(10, onlyAwaitingAllocation = true).futureValue

      val result = repository.candidatesAwaitingAllocation(frameworkId).futureValue
      result must have size 10
    }

    "not return candidates that are initially awaiting allocation but subsequently withdrawn" in {
      val testData = new TestDataMongoRepository()
      testData.createApplications(10, onlyAwaitingAllocation = true).futureValue

      val result = repository.candidatesAwaitingAllocation(frameworkId).futureValue
      result.foreach {
        c =>
          val appId = applicationRepo.findByUserId(c.userId, frameworkId).futureValue.applicationId
          applicationRepo.withdraw(appId, WithdrawApplication("testing", None, "Candidate")).futureValue
      }

      val updatedResult = repository.candidatesAwaitingAllocation(frameworkId).futureValue
      updatedResult mustBe empty
    }
  }


}
