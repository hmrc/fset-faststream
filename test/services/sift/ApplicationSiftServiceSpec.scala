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

package services.sift

import factories.{ DateTimeFactory, DateTimeFactoryMock }
import model._
import model.command.ApplicationForSift
import model.persisted.SchemeEvaluationResult
import org.joda.time.{ DateTime, LocalDate }
import repositories.sift.ApplicationSiftRepository
import testkit.ScalaMockUnitSpec
import testkit.ScalaMockImplicits._
import reactivemongo.bson.{ BSONArray, BSONDocument }
import repositories.SchemeRepository
import repositories.application.GeneralApplicationRepository
import repositories.BSONDateTimeHandler

import scala.concurrent.Future

class ApplicationSiftServiceSpec extends ScalaMockUnitSpec {

  trait TestFixture  {
    val appId = "applicationId"
    val mockAppRepo = mock[GeneralApplicationRepository]
    val mockSiftRepo = mock[ApplicationSiftRepository]
    val mockSchemeRepo = new SchemeRepository {
      override lazy val schemes: Seq[Scheme] = Seq(
        Scheme("DigitalAndTechnology", "DaT", "Digital and Technology", civilServantEligible = false, None, Some(SiftRequirement.FORM),
          siftEvaluationRequired = true, fsbType = None, telephoneInterviewType = None
        ),
        Scheme("International", "INT", "International", civilServantEligible = false, None, Some(SiftRequirement.FORM),
          siftEvaluationRequired = true, fsbType = None, telephoneInterviewType = None
        ),
        Scheme("Commercial", "GCS", "Commercial", civilServantEligible = false, None, Some(SiftRequirement.NUMERIC_TEST),
          siftEvaluationRequired = true, fsbType = None, telephoneInterviewType = None
        )
      )

      override def siftableSchemeIds: Seq[SchemeId] = Seq(SchemeId("International"), SchemeId("Commercial"))
    }

    val service = new ApplicationSiftService {
      def applicationSiftRepo: ApplicationSiftRepository = mockSiftRepo
      def applicationRepo: GeneralApplicationRepository = mockAppRepo
      def schemeRepo: SchemeRepository = mockSchemeRepo
      def dateTimeFactory = DateTimeFactoryMock
    }


  }

  trait SiftUpdateTest extends TestFixture {
    val progressStatusUpdateBson = BSONDocument(
      "$set" -> BSONDocument(
        s"progress-status.${ProgressStatuses.SIFT_COMPLETED}" -> true,
        s"progress-status-timestamp.${ProgressStatuses.SIFT_COMPLETED}" ->
          BSONDateTimeHandler.write(DateTimeFactoryMock.nowLocalTimeZone)
      )
    )

    def currentSchemeUpdateBson(schemeResult: SchemeEvaluationResult*) = BSONDocument(
        "currentSchemeStatus" -> schemeResult.map { s =>
          BSONDocument("schemeId" -> s.schemeId.value, "result" -> s.result)
        }
      )

    val schemeSiftResult = SchemeEvaluationResult(SchemeId("International"), EvaluationResults.Green.toString)
    val queryBson = BSONDocument("applicationId" -> appId)
    val updateBson = BSONDocument("test" -> "test")
    (mockSiftRepo.getSiftEvaluations _).expects(appId).returningAsync(Nil)
    (mockSiftRepo.siftApplicationForSchemeBSON _).expects(appId, schemeSiftResult).returning((queryBson, updateBson))
  }


  "An ApplicationSiftService.progressApplicationToSift" must {

    "progress all applications regardless of failures" in new TestFixture {
      val applicationsToProgressToSift = List(
        ApplicationForSift("appId1", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString))),
        ApplicationForSift("appId2", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), EvaluationResults.Green.toString))),
        ApplicationForSift("appId3",ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
            List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)))
      )

      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects("appId1", ProgressStatuses.SIFT_READY).returningAsync
      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects("appId2", ProgressStatuses.SIFT_ENTERED)
        .returning(Future.failed(new Exception))
      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects("appId3", ProgressStatuses.SIFT_READY).returningAsync

      whenReady(service.progressApplicationToSiftStage(applicationsToProgressToSift)) { results =>

        val failedApplications = Seq(applicationsToProgressToSift(1))
        val passedApplications = Seq(applicationsToProgressToSift.head, applicationsToProgressToSift(2))
        results mustBe SerialUpdateResult(failedApplications, passedApplications)
      }
    }

    "find relevant applications for scheme sifting" in new TestFixture {
      val candidates = Seq(Candidate("userId1", Some("appId1"), Some(""), Some(""), Some(""), Some(""), Some(LocalDate.now), Some(Address("")),
        Some("E1 7UA"), Some("UK"), Some(ApplicationRoute.Faststream), Some("")))

      (mockSiftRepo.findApplicationsReadyForSchemeSift _).expects(*).returningAsync(candidates)

      whenReady(service.findApplicationsReadyForSchemeSift(SchemeId("scheme1"))) { result =>
        result mustBe candidates
      }
    }

    "sift and update progress status for a candidate" in new SiftUpdateTest {
      val expectedUpdateBson = Seq(
        updateBson,
        BSONDocument("$set" -> currentSchemeUpdateBson(schemeSiftResult)),
        progressStatusUpdateBson
      ).foldLeft(BSONDocument.empty)(_ ++ _)

      (mockAppRepo.getCurrentSchemeStatus _).expects(appId).returningAsync(Seq(
        SchemeEvaluationResult(SchemeId("International"), EvaluationResults.Green.toString)
      ))
      (mockSiftRepo.update _).expects("applicationId", queryBson, expectedUpdateBson, *).returningAsync

      whenReady(service.siftApplicationForScheme("applicationId", schemeSiftResult)) { result => result mustBe unit }
    }

     "sift a candidate with remaining schemes to sift" in new SiftUpdateTest {
       val currentStatus = Seq(
        SchemeEvaluationResult(SchemeId("International"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)
      )
      val expectedUpdateBson = Seq(
        updateBson,
        BSONDocument("$set" -> currentSchemeUpdateBson(currentStatus:_*))
      ).foldLeft(BSONDocument.empty)(_ ++ _)

      (mockAppRepo.getCurrentSchemeStatus _).expects(appId).returningAsync(currentStatus)
      (mockSiftRepo.update _).expects("applicationId", queryBson, expectedUpdateBson, *).returningAsync

      whenReady(service.siftApplicationForScheme("applicationId", schemeSiftResult)) { result => result mustBe unit }
    }
  }
}
