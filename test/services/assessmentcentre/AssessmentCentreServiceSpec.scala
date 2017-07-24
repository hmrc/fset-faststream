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

package services.assessmentcentre

import model.{ EvaluationResults, ProgressStatuses, SchemeId, SerialUpdateResult }
import model.command.ApplicationForFsac
import model.persisted.{ PassmarkEvaluation, SchemeEvaluationResult }
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.{ OneAppPerSuite, PlaySpec }
import play.api.mvc.Results
import repositories.application.GeneralApplicationRepository
import repositories.assessmentcentre.AssessmentCentreRepository
import testkit.{ ExtendedTimeout, FutureHelper }

import scala.concurrent.Future

class AssessmentCentreServiceSpec extends PlaySpec with OneAppPerSuite with Results with ScalaFutures with FutureHelper with MockFactory
  with ExtendedTimeout {
  "An AssessmentCentreService" should {
    val applicationsToProgressToSift = List(
      ApplicationForFsac("appId1", PassmarkEvaluation("", Some(""),
        List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toPassmark)), "", Some(""))),
      ApplicationForFsac("appId2", PassmarkEvaluation("", Some(""),
        List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toPassmark)), "", Some(""))),
      ApplicationForFsac("appId3", PassmarkEvaluation("", Some(""),
        List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toPassmark)), "", Some(""))))

    val mockAppRepo = mock[GeneralApplicationRepository]
    val mockAssessmentCentreRepo = mock[AssessmentCentreRepository]
    val service = new AssessmentCentreService {
      def applicationRepo: GeneralApplicationRepository = mockAppRepo
      def assessmentCentreRepo: AssessmentCentreRepository = mockAssessmentCentreRepo
    }

    (mockAppRepo.addProgressStatusAndUpdateAppStatus _)
      .expects(applicationsToProgressToSift.head.applicationId, ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)
      .returning(Future.successful())
    (mockAppRepo.addProgressStatusAndUpdateAppStatus _)
      .expects(applicationsToProgressToSift(1).applicationId, ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)
      .returning(Future.failed(new Exception))
    (mockAppRepo.addProgressStatusAndUpdateAppStatus _)
      .expects(applicationsToProgressToSift(2).applicationId, ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)
      .returning(Future.successful())

    "progress candidates to assessment centre, attempting all despite errors" in {

      whenReady(service.progressApplicationsToAssessmentCentre(applicationsToProgressToSift)) {
        results =>
          val failedApplications = Seq(applicationsToProgressToSift(1))
          val passedApplications = Seq(applicationsToProgressToSift.head, applicationsToProgressToSift(2))
          results mustBe SerialUpdateResult(failedApplications, passedApplications)
      }
    }
  }
}
