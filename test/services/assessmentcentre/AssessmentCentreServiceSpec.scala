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

import model._
import model.command.ApplicationForFsac
import model.persisted.SchemeEvaluationResult
import repositories.application.GeneralApplicationRepository
import repositories.assessmentcentre.AssessmentCentreRepository
import testkit.ScalaMockUnitSpec
import testkit.ScalaMockImplicits._

import scala.concurrent.Future

class AssessmentCentreServiceSpec extends ScalaMockUnitSpec {
  "An AssessmentCentreService" should {
    val applicationsToProgressToSift = List(
      ApplicationForFsac("appId1", ApplicationStatus.SIFT,
        List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString))),
      ApplicationForFsac("appId2", ApplicationStatus.SIFT,
        List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString))),
      ApplicationForFsac("appId3", ApplicationStatus.SIFT,
        List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)))
    )

    val mockAppRepo = mock[GeneralApplicationRepository]
    val mockAssessmentCentreRepo = mock[AssessmentCentreRepository]
    val service = new AssessmentCentreService {
      def applicationRepo: GeneralApplicationRepository = mockAppRepo
      def assessmentCentreRepo: AssessmentCentreRepository = mockAssessmentCentreRepo
    }

    (mockAssessmentCentreRepo.progressToAssessmentCentre _)
      .expects(applicationsToProgressToSift.head, ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)
      .returningAsync
    (mockAssessmentCentreRepo.progressToAssessmentCentre _)
      .expects(applicationsToProgressToSift(1), ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)
      .returning(Future.failed(new Exception))
    (mockAssessmentCentreRepo.progressToAssessmentCentre _)
      .expects(applicationsToProgressToSift(2), ProgressStatuses.ASSESSMENT_CENTRE_AWAITING_ALLOCATION)
      .returningAsync

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
