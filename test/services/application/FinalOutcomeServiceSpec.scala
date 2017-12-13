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

package services.application

import connectors.EmailClient
import model.ProgressStatuses.{ ASSESSMENT_CENTRE_FAILED, ASSESSMENT_CENTRE_FAILED_SDIP_GREEN, ASSESSMENT_CENTRE_SCORES_ACCEPTED }
import model._
import model.command.ApplicationForProgression
import model.persisted.{ ContactDetails, SchemeEvaluationResult }
import org.joda.time.DateTime
import repositories.application.{ FinalOutcomeRepository, GeneralApplicationRepository }
import repositories.contactdetails.ContactDetailsRepository
import testkit.ScalaMockImplicits._
import testkit.ScalaMockUnitSpec
import uk.gov.hmrc.http.HeaderCarrier

class FinalOutcomeServiceSpec extends ScalaMockUnitSpec {

  "final success notified" must {
    "progress candidate" in new TestFixture {

      ( mockApplicationRepo.find(_: String) )
        .expects(App1.applicationId)
        .returningAsync(Option(C1))

      ( mockContactDetailsRepo.find _ )
        .expects(C1.userId)
        .returningAsync(Cd1)

      ( mockEmailClient.notifyCandidateOnFinalSuccess(_: String, _: String, _: String)(_: HeaderCarrier) )
        .expects(Cd1.email, C1.name, Scheme, hc)
        .returningAsync

      (mockFinalOutcomeRepo.firstResidualPreference _)
        .expects(App1.currentSchemeStatus, false)
        .returning(Option(App1.currentSchemeStatus.head))

      ( mockFinalOutcomeRepo.progressToJobOfferNotified _ )
        .expects(App1)
        .returningAsync

      service.progressApplicationsToFinalSuccessNotified(Seq(App1)).futureValue
    }
  }

  "final failure notified" must {
    "progress candidate to final state" in new TestFixture {

      ( mockApplicationRepo.find(_: String) )
        .expects(App1.applicationId)
        .returningAsync(Option(C1))

      ( mockContactDetailsRepo.find _ )
        .expects(C1.userId)
        .returningAsync(Cd1)

      ( mockEmailClient.notifyCandidateOnFinalFailure(_: String, _: String)(_: HeaderCarrier) )
        .expects(Cd1.email, C1.name, hc)
        .returningAsync

      ( mockApplicationRepo.getProgressStatusTimestamps(_: String) )
        .expects(App1.applicationId)
        .returningAsync(List((ASSESSMENT_CENTRE_FAILED.toString, DateTime.now)))

      ( mockFinalOutcomeRepo.progressToFinalFailureNotified _ )
        .expects(App1)
        .returningAsync

      service.progressApplicationsToFinalFailureNotified(Seq(App1)).futureValue
    }

    "progress candidate to non-final state (assessment centre failed sdip green)" in new TestFixture {

      ( mockApplicationRepo.find(_: String) )
        .expects(App1.applicationId)
        .returningAsync(Option(C1))

      ( mockContactDetailsRepo.find _ )
        .expects(C1.userId)
        .returningAsync(Cd1)

      ( mockEmailClient.notifyCandidateOnFinalFailure(_: String, _: String)(_: HeaderCarrier) )
        .expects(Cd1.email, C1.name, hc)
        .returningAsync

      ( mockApplicationRepo.getProgressStatusTimestamps(_: String) )
        .expects(App1.applicationId)
        .returningAsync(List(
          (ASSESSMENT_CENTRE_SCORES_ACCEPTED.toString, DateTime.now().minusMinutes(1)),
          (ASSESSMENT_CENTRE_FAILED_SDIP_GREEN.toString, DateTime.now())
        ))

      ( mockFinalOutcomeRepo.progressToAssessmentCentreFailedSdipGreenNotified _ )
        .expects(App1)
        .returningAsync

      service.progressApplicationsToFinalFailureNotified(Seq(App1)).futureValue
    }
  }

  trait TestFixture {

    implicit val hc = HeaderCarrier()

    val Scheme = "Commercial"
    val App1 = ApplicationForProgression("appId1", ApplicationStatus.ASSESSMENT_CENTRE,
      List(SchemeEvaluationResult(SchemeId(Scheme), EvaluationResults.Green.toString)))

    val C1 = Candidate("userId", Some(App1.applicationId), Some("test@test123.com"), None, None, None, None, None, None, None, None, None)

    val Cd1 = ContactDetails(outsideUk = false, Address("line1a"), Some("123"), Some("UK"), "email1@email.com", "12345")

    val mockContactDetailsRepo = mock[ContactDetailsRepository]
    val mockApplicationRepo = mock[GeneralApplicationRepository]
    val mockFinalOutcomeRepo = mock[FinalOutcomeRepository]
    val mockEmailClient = mock[EmailClient]

    val service = new FinalOutcomeService {
      override def contactDetailsRepo: ContactDetailsRepository = mockContactDetailsRepo

      override def applicationRepo: GeneralApplicationRepository = mockApplicationRepo

      override def finalOutcomeRepo: FinalOutcomeRepository = mockFinalOutcomeRepo

      override def emailClient: EmailClient = mockEmailClient
    }
  }
}
