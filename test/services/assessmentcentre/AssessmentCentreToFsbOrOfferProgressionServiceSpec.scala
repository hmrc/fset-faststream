/*
 * Copyright 2018 HM Revenue & Customs
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

import connectors.ExchangeObjects.Candidate
import connectors.{ AuthProviderClient, EmailClient }
import model.ProgressStatuses.ASSESSMENT_CENTRE_PASSED
import model.{ SchemeId, _ }
import model.command.{ ApplicationForProgression, ApplicationStatusDetails }
import model.persisted.{ ContactDetails, SchemeEvaluationResult }
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.fsb.FsbRepository
import testkit.ScalaMockImplicits._
import testkit.ScalaMockUnitSpec

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class AssessmentCentreToFsbOrOfferProgressionServiceSpec extends ScalaMockUnitSpec {

  "progress candidates to fsb or job offer" must {
    "Progress candidates to FSB when their first residual preference is green, and requires an FSB" in new TestFixture {

      applicationsToProgressToFsb.map { expectedApplication =>
        (mockApplicationRepository.getCurrentSchemeStatus _)
          .expects(expectedApplication.applicationId)
          .returningAsync(expectedApplication.currentSchemeStatus).once

        (mockFsbRepository.progressToFsb _)
          .expects(expectedApplication)
          .returningAsync.once

        (mockApplicationRepository.find(_: String))
          .expects(expectedApplication.applicationId)
          .returningAsync(Option(candidate0))

        (mockApplicationRepository.findStatus(_: String))
          .expects(expectedApplication.applicationId)
          .returningAsync(assessmentCentrePassedApplicationStatus)

        (mockContactDetailsRepo.find _)
          .expects(userId)
          .returningAsync(candidate1)

        (mockEmailClient.sendCandidateAssessmentCompletedMovedToFsb(_: String, _: String)(_: HeaderCarrier))
          .expects(candidate1.email, candidate0.name, hc)
          .returningAsync
      }

      whenReady(service.progressApplicationsToFsbOrJobOffer(applicationsToProgressToFsb)(hc)) {
        results =>
          val failedApplications = Seq()
          val passedApplications = applicationsToProgressToFsb
          results mustBe SerialUpdateResult(failedApplications, passedApplications)
      }
    }

    "Progress candidates to job offer when their first residual preference is green, and does not require an FSB" in new TestFixture {
      applicationsToProgressToJobOffer.map { expectedApplication =>
        (mockApplicationRepository.getCurrentSchemeStatus _)
          .expects(expectedApplication.applicationId)
          .returningAsync(expectedApplication.currentSchemeStatus).once

        (mockApplicationRepository.findStatus(_: String))
          .expects(expectedApplication.applicationId)
          .returningAsync(assessmentCentrePassedApplicationStatus)

        (mockFsbRepository.progressToJobOffer _)
          .expects(expectedApplication)
          .returning(Future.successful(())).once
      }

      whenReady(service.progressApplicationsToFsbOrJobOffer(applicationsToProgressToJobOffer)(hc)) {
        results =>
          val failedApplications = Seq()
          val passedApplications = applicationsToProgressToJobOffer
          results mustBe SerialUpdateResult(failedApplications, passedApplications)
      }
    }

    "Do nothing to a candidate when their first residual preference is not green" in new TestFixture {
      applicationsNotToProgress.map { expectedApplication =>
        (mockApplicationRepository.getCurrentSchemeStatus _)
          .expects(expectedApplication.applicationId)
          .returningAsync(expectedApplication.currentSchemeStatus).once

        (mockApplicationRepository.findStatus(_: String))
          .expects(expectedApplication.applicationId)
          .returningAsync(assessmentCentrePassedApplicationStatus)

        (mockFsbRepository.progressToJobOffer _)
          .expects(*)
          .returning(Future.successful(())).never

        (mockFsbRepository.progressToFsb _)
          .expects(*)
          .returning(Future.successful(())).never
      }

      whenReady(service.progressApplicationsToFsbOrJobOffer(applicationsNotToProgress)(hc)) {
        results =>
          val failedApplications = Seq()
          val passedApplications = applicationsNotToProgress
          results mustBe SerialUpdateResult(failedApplications, passedApplications)
      }
    }
  }

  trait TestFixture  {
    val mockFsbRepository = mock[FsbRepository]
    val mockApplicationRepository = mock[GeneralApplicationRepository]
    val mockContactDetailsRepo = mock[ContactDetailsRepository]
    val mockEmailClient = mock[EmailClient]

    val service: AssessmentCentreToFsbOrOfferProgressionService = new AssessmentCentreToFsbOrOfferProgressionService() {
      val fsbRepo = mockFsbRepository
      val applicationRepo = mockApplicationRepository
      val fsbRequiredSchemeIds: Seq[SchemeId] = Seq(SchemeId("DigitalAndTechnology"),
        SchemeId("DiplomaticService"), SchemeId("GovernmentStatisticalService"))

      override def emailClient: EmailClient = mockEmailClient
      override def contactDetailsRepo: ContactDetailsRepository = mockContactDetailsRepo
    }

    val userId = "1"
    implicit val hc = HeaderCarrier()

    val assessmentCentrePassedApplicationStatus = ApplicationStatusDetails(
    ApplicationStatus.ASSESSMENT_CENTRE,
    ApplicationRoute.Faststream,
    Some(ASSESSMENT_CENTRE_PASSED),
    None,
    None
    )

    val candidate0 = model.Candidate(userId, None, None, None, None, None, None, None, None, None, None, None)

    val candidate1 = ContactDetails(outsideUk = false, Address("line1a"), Some("123"), Some("UK"), "email1@email.com", "12345")
    val applicationsToProgressToFsb = List(
      ApplicationForProgression("appId1", ApplicationStatus.ASSESSMENT_CENTRE,
        List(SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), EvaluationResults.Green.toString))),
      ApplicationForProgression("appId2", ApplicationStatus.ASSESSMENT_CENTRE,
        List(SchemeEvaluationResult(SchemeId("DiplomaticService"), EvaluationResults.Green.toString))),
      ApplicationForProgression("appId3", ApplicationStatus.ASSESSMENT_CENTRE,
        List(SchemeEvaluationResult(SchemeId("GovernmentStatisticalService"), EvaluationResults.Green.toString)))
    )

    val applicationsToProgressToJobOffer = List(
      ApplicationForProgression("appId1", ApplicationStatus.ASSESSMENT_CENTRE,
        List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString))),
      ApplicationForProgression("appId2", ApplicationStatus.ASSESSMENT_CENTRE,
        List(SchemeEvaluationResult(SchemeId("International"), EvaluationResults.Green.toString))),
      ApplicationForProgression("appId3", ApplicationStatus.ASSESSMENT_CENTRE,
        List(SchemeEvaluationResult(SchemeId("Finance"), EvaluationResults.Green.toString)))
    )

    val applicationsNotToProgress = List(
      ApplicationForProgression("appId1", ApplicationStatus.ASSESSMENT_CENTRE,
        List(SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), EvaluationResults.Red.toString))),
      ApplicationForProgression("appId2", ApplicationStatus.ASSESSMENT_CENTRE,
        List(SchemeEvaluationResult(SchemeId("International"), EvaluationResults.Amber.toString))),
      ApplicationForProgression("appId3", ApplicationStatus.ASSESSMENT_CENTRE,
        List(SchemeEvaluationResult(SchemeId("Finance"), EvaluationResults.Red.toString)))
    )
  }
}
