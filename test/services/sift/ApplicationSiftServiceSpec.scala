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

import java.util

import model.Commands.Candidate
import model.EvaluationResults.Result
import model._
import model.command.ApplicationForSift
import model.persisted.{ PassmarkEvaluation, SchemeEvaluationResult }
import org.joda.time.LocalDate
import org.mockito.ArgumentCaptor
import repositories.sift.ApplicationSiftRepository
import testkit.{ ExtendedTimeout, MockitoSugar, UnitWithAppSpec }
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import reactivemongo.bson.BSONDocument
import repositories.SchemeRepositoryImpl
import repositories.application.GeneralApplicationRepository
import testkit.MockitoImplicits._

import scala.collection.JavaConversions._
import scala.concurrent.Future

class ApplicationSiftServiceSpec extends UnitWithAppSpec with ExtendedTimeout with MockitoSugar {

  trait TestFixture  {
    val mockAppRepo = mock[GeneralApplicationRepository]
    val mockSiftRepo = mock[ApplicationSiftRepository]
    val mockSchemeRepo = mock[SchemeRepositoryImpl]

    when(mockAppRepo.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatuses.ProgressStatus])).thenReturn(Future.successful())

    val service = new ApplicationSiftService {
      def applicationSiftRepo: ApplicationSiftRepository = mockSiftRepo
      def applicationRepo: GeneralApplicationRepository = mockAppRepo
      def schemeRepo: SchemeRepositoryImpl = mockSchemeRepo
    }
  }


  "An ApplicationSiftService.progressApplicationToSift" must {
    "progress all applications regardless of failures" in new TestFixture {
      val applicationsToProgressToSift = List(
        ApplicationForSift("appId1", PassmarkEvaluation("", Some(""),
          List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)), "", Some(""))),
        ApplicationForSift("appId2", PassmarkEvaluation("", Some(""),
          List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)), "", Some(""))),
        ApplicationForSift("appId3", PassmarkEvaluation("", Some(""),
            List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)), "", Some(""))))

      when(mockSiftRepo.nextApplicationsForSiftStage(any[Int])).thenReturn(Future.successful{ applicationsToProgressToSift })

      when(mockAppRepo.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatuses.ProgressStatus]))
        .thenReturn(Future.successful())
        .thenReturn(Future.failed(new Exception))
        .thenReturn(Future.successful())


      whenReady(service.progressApplicationToSiftStage(applicationsToProgressToSift)) { results =>

        val failedApplications = Seq(applicationsToProgressToSift(1))
        val passedApplications = Seq(applicationsToProgressToSift(0), applicationsToProgressToSift(2))
        results mustBe SerialUpdateResult(failedApplications, passedApplications)

        val argCaptor = ArgumentCaptor.forClass(classOf[String])

        verify(mockAppRepo, times(3)).addProgressStatusAndUpdateAppStatus(argCaptor.capture(), eqTo(ProgressStatuses.ALL_SCHEMES_SIFT_ENTERED))

        val consecutiveArguments = argCaptor.getAllValues
        consecutiveArguments.toSet mustBe applicationsToProgressToSift.map(_.applicationId).toSet
      }
    }

    "find relevant applications for scheme sifting" in new TestFixture {
      val candidates = Seq(Candidate("userId1", Some("appId1"), Some(""), Some(""), Some(""), Some(""), Some(LocalDate.now), Some(Address("")),
        Some("E1 7UA"), Some("UK"), Some(ApplicationRoute.Faststream), Some("")))

      when(mockSiftRepo.findApplicationsReadyForSchemeSift(any[SchemeId])).thenReturn(Future.successful { candidates })

      whenReady(service.findApplicationsReadyForSchemeSift(SchemeId("scheme1"))) { result =>
        result mustBe candidates
      }
    }

    "sift a candidate for a scheme" in new TestFixture {
      val schemeEval = SchemeEvaluationResult(SchemeId("International"), EvaluationResults.Green.toString)
      when(mockAppRepo.getCurrentSchemeStatus(any[String])).thenReturnAsync(
        Seq(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString))
      )

      val evalBson = BSONDocument(
        "currentSchemeStatus.International" -> "Green",
        "currentSchemeStatus.Commercial" -> "Green"
      )
      val queryBson = BSONDocument("applicationId" -> "applicationId")
      val updateBson = BSONDocument("test" -> "test")
      when(mockSiftRepo.siftApplicationForSchemeBSON(any[String], any[SchemeEvaluationResult])).thenReturn((queryBson, updateBson))
      when(mockSiftRepo.update(any[String], any[BSONDocument], any[BSONDocument], any[String]))
        .thenReturnAsync()

      val expectedUpdateBson = updateBson ++ BSONDocument("$set" -> evalBson)
      whenReady(service.siftApplicationForScheme("applicationId", schemeEval)) { _ =>
        verify(mockSiftRepo).update(eqTo("applicationId"), eqTo(queryBson), eqTo(expectedUpdateBson),
          eqTo("Sifting application for International")
        )
      }
    }
  }
}
