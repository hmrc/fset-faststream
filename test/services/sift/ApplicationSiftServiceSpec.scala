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
import model._
import model.command.ApplicationForSift
import model.persisted.{ PassmarkEvaluation, SchemeEvaluationResult }
import org.joda.time.LocalDate
import org.mockito.ArgumentCaptor
import repositories.sift.ApplicationSiftRepository
import testkit.{ ExtendedTimeout, UnitWithAppSpec }
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import repositories.application.GeneralApplicationRepository

import scala.collection.JavaConversions._
import scala.concurrent.Future

class ApplicationSiftServiceSpec extends UnitWithAppSpec with ExtendedTimeout {

  "An ApplicationSiftService.progressApplicationToSift" should {
    case class TestApplicationSiftService(siftRepository: ApplicationSiftRepository, appRepo: GeneralApplicationRepository)
      extends ApplicationSiftService {
      override def applicationSiftRepo: ApplicationSiftRepository = siftRepository
      override def applicationRepo: GeneralApplicationRepository = appRepo
    }

    val mockAppRepo = mock[GeneralApplicationRepository]

    when(mockAppRepo.addProgressStatusAndUpdateAppStatus(any[String], any[ProgressStatuses.ProgressStatus])).thenReturn(Future.successful())

    "progress all applications regardless of failures" in {
      val mockRepo = mock[ApplicationSiftRepository]

      val applicationsToProgressToSift = List(
        ApplicationForSift("appId1", PassmarkEvaluation("", Some(""),
          List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toPassmark)), "", Some(""))),
        ApplicationForSift("appId2", PassmarkEvaluation("", Some(""),
          List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toPassmark)), "", Some(""))),
        ApplicationForSift("appId3", PassmarkEvaluation("", Some(""),
            List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toPassmark)), "", Some(""))))

      when(mockRepo.nextApplicationsForSiftStage(any[Int])).thenReturn(Future.successful{ applicationsToProgressToSift })

      when(mockRepo.progressApplicationToSiftStage(any[ApplicationForSift]))
        .thenReturn(Future.successful())
        .thenReturn(Future.failed(new Exception))
        .thenReturn(Future.successful())


      whenReady(TestApplicationSiftService(mockRepo, mockAppRepo).progressApplicationToSiftStage(applicationsToProgressToSift)) { results =>

        val failedApplications = Seq(applicationsToProgressToSift(1))
        val passedApplications = Seq(applicationsToProgressToSift(0), applicationsToProgressToSift(2))
        results mustBe SerialUpdateResult(failedApplications, passedApplications)

        val argCaptor = ArgumentCaptor.forClass(classOf[ApplicationForSift])

        verify(mockRepo, times(3)).progressApplicationToSiftStage(argCaptor.capture())

        val consecutiveArguments = argCaptor.getAllValues
        consecutiveArguments.toList mustBe applicationsToProgressToSift
      }
    }

    "find relevant applications for scheme sifting" in {
      val mockRepo = mock[ApplicationSiftRepository]

      val candidates = Seq(Candidate("userId1", Some("appId1"), Some(""), Some(""), Some(""), Some(""), Some(LocalDate.now), Some(Address("")),
        Some("E1 7UA"), Some("UK"), Some(ApplicationRoute.Faststream), Some("")))

      when(mockRepo.findApplicationsReadyForSchemeSift(any[SchemeId])).thenReturn(Future.successful { candidates })

      whenReady(TestApplicationSiftService(mockRepo, mockAppRepo).findApplicationsReadyForSchemeSift(SchemeId("scheme1"))) { result =>
        result mustBe candidates
      }
    }
  }
}
