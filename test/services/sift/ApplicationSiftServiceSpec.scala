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
import model.Commands.Candidate
import model._
import model.command.ApplicationForSift
import model.persisted.SchemeEvaluationResult
import org.joda.time.{ DateTime, LocalDate }
import repositories.sift.ApplicationSiftRepository
import testkit.ScalaMockUnitSpec
import testkit.ScalaMockImplicits._
import reactivemongo.bson.{ BSONArray, BSONDocument }
import repositories.SchemeRepositoryImpl
import repositories.application.GeneralApplicationRepository
import repositories.BSONDateTimeHandler

import scala.concurrent.Future

class ApplicationSiftServiceSpec extends ScalaMockUnitSpec {

  trait TestFixture  {
    val mockAppRepo = mock[GeneralApplicationRepository]
    val mockSiftRepo = mock[ApplicationSiftRepository]
    val mockSchemeRepo = mock[SchemeRepositoryImpl]

    val service = new ApplicationSiftService {
      def applicationSiftRepo: ApplicationSiftRepository = mockSiftRepo
      def applicationRepo: GeneralApplicationRepository = mockAppRepo
      def schemeRepo: SchemeRepositoryImpl = mockSchemeRepo
      def dateTimeFactory = DateTimeFactoryMock
    }
  }


  "An ApplicationSiftService.progressApplicationToSift" must {
    "progress all applications regardless of failures" in new TestFixture {
      val applicationsToProgressToSift = List(
        ApplicationForSift("appId1", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString))),
        ApplicationForSift("appId2", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString))),
        ApplicationForSift("appId3",ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
            List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)))
      )

      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects("appId1", ProgressStatuses.ALL_SCHEMES_SIFT_ENTERED).returningAsync
      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects("appId2", ProgressStatuses.ALL_SCHEMES_SIFT_ENTERED)
        .returning(Future.failed(new Exception))
      (mockAppRepo.addProgressStatusAndUpdateAppStatus _).expects("appId3", ProgressStatuses.ALL_SCHEMES_SIFT_ENTERED).returningAsync

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

    "sift and update progress status for a candidate" in new TestFixture {
      val appId = "applicationId"
      val schemeEval = SchemeEvaluationResult(SchemeId("International"), EvaluationResults.Green.toString)
      val evalBson = BSONDocument(
        "currentSchemeStatus" -> BSONArray(
          BSONDocument("schemeId" -> "International", "result" -> "Green")
      ))
      val queryBson = BSONDocument("applicationId" -> appId)
      val updateBson = BSONDocument("test" -> "test")
      val expectedUpdateBson = updateBson ++ BSONDocument("$set" -> evalBson) ++ BSONDocument(
        "$set" -> BSONDocument(
          "progress-status.ALL_SCHEMES_SIFT_COMPLETED" -> true,
          "progress-status-timestamp.ALL_SCHEMES_SIFT_COMPLETE" -> BSONDateTimeHandler.write(DateTimeFactoryMock.nowLocalTimeZone)
        )
      )

      play.api.Logger.error(s"\n\n EXPECTED ${BSONDocument.pretty(expectedUpdateBson)}")
      play.api.Logger.error(s"\n\n EXPECTED ${BSONDocument.pretty(queryBson)}")

      (mockAppRepo.getCurrentSchemeStatus _).expects(appId).returningAsync(Seq(
        SchemeEvaluationResult(SchemeId("International"), EvaluationResults.Green.toString)
      ))
      (mockSiftRepo.getSiftEvaluations _).expects(appId).returningAsync(Nil)
      (mockSchemeRepo.siftableSchemeIds _).expects.returning(Seq(SchemeId("International")))
      (mockSiftRepo.siftApplicationForSchemeBSON _).expects(appId, schemeEval).returning((queryBson, updateBson))

      (mockSiftRepo.update _).expects("applicationId", queryBson, expectedUpdateBson, "Sifting application for International").returningAsync

      whenReady(service.siftApplicationForScheme("applicationId", schemeEval)) { result =>
        result mustBe unit
      }
    }

    "sift a candidate for a scheme with existing sift results" in new TestFixture {
      val schemeEval = SchemeEvaluationResult(SchemeId("International"), EvaluationResults.Green.toString)

      (mockAppRepo.getCurrentSchemeStatus _).expects(*).returningAsync(
        Seq(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString))
      )
      (mockSiftRepo.getSiftEvaluations _).expects(*).returningAsync(Seq(
        SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), EvaluationResults.Green.toString)
      ))
      (mockSchemeRepo.siftableSchemeIds _).expects.returning(Seq(SchemeId("International"), SchemeId("Commercial"),
        SchemeId("DigitalAndTechnology"))
      )

      val evalBson = BSONDocument(
        "currentSchemeStatus" -> BSONArray(
          BSONDocument("schemeId" -> "International", "result" -> "Green"),
          BSONDocument("schemeId" -> "Commercial", "result" -> "Green")
      ))
      val queryBson = BSONDocument("applicationId" -> "applicationId")
      val updateBson = BSONDocument("test" -> "test")
      (mockSiftRepo.siftApplicationForSchemeBSON _).expects(*, *).returning((queryBson, updateBson))

      (mockSiftRepo.update _).expects(*, *, *, *).returningAsync

      val expectedUpdateBson = updateBson ++ BSONDocument("$set" -> evalBson)
      whenReady(service.siftApplicationForScheme("applicationId", schemeEval)) { result =>
        result mustBe unit
      }
    }
  }
}
