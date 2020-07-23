/*
 * Copyright 2020 HM Revenue & Customs
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

import connectors.OnlineTestEmailClient
import model.ProgressStatuses.ASSESSMENT_CENTRE_PASSED
import model.command.{ ApplicationForProgression, ApplicationStatusDetails }
import model.persisted.{ ContactDetails, SchemeEvaluationResult }
import model.{ SchemeId, _ }
import repositories.SchemeRepository
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.fsb.FsbRepository
import services.scheme.SchemePreferencesService
import testkit.ScalaMockImplicits._
import testkit.ScalaMockUnitSpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class ProgressionToFsbOrOfferServiceSpec extends ScalaMockUnitSpec {
//TODO:fix these tests
  "progressApplicationsToFsbOrJobOffer" must {
    "Progress candidates to FSB when their first residual preference is green, and requires an FSB" in new TestFixture {
      applicationsToProgressToFsb.map { expectedApplication =>
        (mockSchemePreferencesService.find(_: String))
          .expects(expectedApplication.applicationId)
          .returningAsync(selectedSchemes)

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

        // Mock no arg method, which returns data
        (mockSchemeRepository.fsbSchemeIds _).expects().returning(
          Seq(SchemeId("DigitalAndTechnology"), SchemeId("DiplomaticService"), SchemeId("GovernmentStatisticalService")))

        (mockEmailClient.sendCandidateAssessmentCompletedMovedToFsb(_: String, _: String)(_: HeaderCarrier))
          .expects(candidate1.email, candidate0.name, hc)
          .returningAsync
      }

      whenReady(progressionToFsbOrOfferService.progressApplicationsToFsbOrJobOffer(applicationsToProgressToFsb)(hc)) {
        results =>
          val failedApplications = Seq()
          val passedApplications = applicationsToProgressToFsb
          results mustBe SerialUpdateResult(failedApplications, passedApplications)
      }
    }

    "Progress candidates to job offer when their first residual preference is green, and does not require an FSB" in new TestFixture {
      applicationsToProgressToJobOffer.map { expectedApplication =>
        (mockSchemePreferencesService.find(_: String))
          .expects(expectedApplication.applicationId)
          .returningAsync(selectedSchemes)

        (mockApplicationRepository.getCurrentSchemeStatus _)
          .expects(expectedApplication.applicationId)
          .returningAsync(expectedApplication.currentSchemeStatus).once

        (mockApplicationRepository.findStatus(_: String))
          .expects(expectedApplication.applicationId)
          .returningAsync(assessmentCentrePassedApplicationStatus)

        // Mock no arg method, which returns data
        (mockSchemeRepository.fsbSchemeIds _).expects().returning(
          Seq(SchemeId("DigitalAndTechnology"), SchemeId("DiplomaticService"), SchemeId("GovernmentStatisticalService")))

        (mockFsbRepository.progressToJobOffer _)
          .expects(expectedApplication)
          .returning(Future.successful(())).once
      }

      whenReady(progressionToFsbOrOfferService.progressApplicationsToFsbOrJobOffer(applicationsToProgressToJobOffer)(hc)) {
        results =>
          val failedApplications = Seq()
          val passedApplications = applicationsToProgressToJobOffer
          results mustBe SerialUpdateResult(failedApplications, passedApplications)
      }
    }

    "Do nothing to a candidate when their first residual preference is not green" in new TestFixture {
      applicationsNotToProgress.map { expectedApplication =>
        (mockSchemePreferencesService.find(_: String))
          .expects(expectedApplication.applicationId)
          .returningAsync(selectedSchemes)

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

      whenReady(progressionToFsbOrOfferService.progressApplicationsToFsbOrJobOffer(applicationsNotToProgress)(hc)) {
        results =>
          val failedApplications = Seq()
          val passedApplications = applicationsNotToProgress
          results mustBe SerialUpdateResult(failedApplications, passedApplications)
      }
    }
  }

  trait TestFixture  {
    val mockSchemeRepository = mock[SchemeRepository]
    val mockApplicationRepository = mock[GeneralApplicationRepository]
    val mockContactDetailsRepo = mock[ContactDetailsRepository]
    val mockFsbRepository = mock[FsbRepository]
    val mockSchemePreferencesService = mock[SchemePreferencesService]
    val mockEmailClient = mock[OnlineTestEmailClient] //TODO:fix change the type was EmailClient

    val progressionToFsbOrOfferService = new ProgressionToFsbOrOfferService(
      mockSchemeRepository,
      mockApplicationRepository,
      mockContactDetailsRepo,
      mockFsbRepository,
      mockSchemePreferencesService,
      mockEmailClient
/*      val fsbRepo = mockFsbRepository
      val applicationRepo = mockApplicationRepository
      val fsbRequiredSchemeIds: Seq[SchemeId] =
        Seq(SchemeId("DigitalAndTechnology"), SchemeId("DiplomaticService"), SchemeId("GovernmentStatisticalService"))

      override def emailClient: EmailClient = mockEmailClient
      override def contactDetailsRepo: ContactDetailsRepository = mockContactDetailsRepo
      def schemePreferencesService: SchemePreferencesService = mockSchemePreferencesService*/
    )

    val userId = "1"
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val assessmentCentrePassedApplicationStatus = ApplicationStatusDetails(
      ApplicationStatus.ASSESSMENT_CENTRE,
      ApplicationRoute.Faststream,
      latestProgressStatus = Some(ASSESSMENT_CENTRE_PASSED),
      statusDate = None,
      overrideSubmissionDeadline = None
    )

    val candidate0 = model.Candidate(userId, applicationId = None, testAccountId = None, email = None, firstName = None,
      lastName = None, preferredName = None, dateOfBirth = None, address = None, postCode = None, country = None,
      applicationRoute = None, applicationStatus = None)

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

    val schemes = List(
      SchemeId("DigitalAndTechnology"),
      SchemeId("DiplomaticService"),
      SchemeId("GovernmentStatisticalService"),
      SchemeId("Commercial"),
      SchemeId("International"),
      SchemeId("Finance")
    )

    val selectedSchemes = SelectedSchemes(schemes, orderAgreed = true, eligible = true)
  }
}
