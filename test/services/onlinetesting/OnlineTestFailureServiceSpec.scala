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

package services.onlinetesting

import connectors.CSREmailClient
import model.Address
import model.PersistedObjects.ContactDetails
import model.persisted.ApplicationForNotification
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito._
import org.scalatest.time.{ Millis, Span }
import repositories.ContactDetailsRepository
import repositories.application.GeneralApplicationRepository
import repositories.onlinetesting.Phase1TestRepository
import services.{ AuditService, BaseServiceSpec }
import testkit.MockitoImplicits.{ OngoingStubbingExtension, OngoingStubbingExtensionUnit }
import model.ApplicationStatus._
import testkit.UnitSpec
import uk.gov.hmrc.play.http.HeaderCarrier

class OnlineTestFailureServiceSpec extends BaseServiceSpec {

  /* TODO FAST STREAM FIX ME
  "when processing the next failed test" should {
    "do nothing when there are no failed tests" in new ProcessNextFailedFixture {
      when(otRepository.nextApplicationPendingFailure).thenReturnAsync(None)

      service.processNextFailedTest().futureValue mustBe (())
    }

    "process the next failed test" in new ProcessNextFailedFixture {
      when(otRepository.nextApplicationPendingFailure).thenReturnAsync(Some(failedTest))

      service.processNextFailedTest().futureValue mustBe (())

      verify(service).processFailedTest(failedTest)
    }
  }*/

  "when processing an failed test" should {
    "email the candidate about their failed online test" in new ProcessFailedFixture {

      service.processFailedTest(failedTest).futureValue mustBe unit

      verify(service).emailCandidate(failedTest, emailAddress)
    }

    "update the application status on success" in new ProcessFailedFixture {
      service.processFailedTest(failedTest).futureValue mustBe unit

      verify(service).commitNotifiedStatus(failedTest)
    }

    "not update the application status on failure" in new ProcessFailedFixture {
      doThrowAsync().when(service).emailCandidate(any(), any())

      val result = service.processFailedTest(failedTest).failed.futureValue

      result mustBe an[Exception]
      verify(service).emailCandidate(failedTest, emailAddress)
      verify(service, never).commitNotifiedStatus(failedTest)
    }
  }

  "when emailing a candidate" should {
    "send the email to their email address" in new EmailCandidateFixture {
      service.emailCandidate(failedTest, emailAddress).futureValue mustBe unit

      verify(emailClient).sendOnlineTestFailed(eqTo(emailAddress), any())(any())
    }

    "greet candidate by their preferred name" in new EmailCandidateFixture {
      service.emailCandidate(failedTest, emailAddress).futureValue mustBe unit

      verify(emailClient).sendOnlineTestFailed(any(), eqTo(preferredName))(any())
    }

    "attach a 'header carrier' with the current timestamp" in new EmailCandidateFixture {
      // Test ensures we're not making the mistake of caching the HeaderCarrier, which
      // would result in an incorrect timestamp being passed on to other components.
      val hc1 = HeaderCarrier(nsStamp = 1)
      val hc2 = HeaderCarrier(nsStamp = 2)
      val hcs = List(hc1, hc2).iterator
      override def hc = hcs.next()

      service.emailCandidate(failedTest, emailAddress).futureValue mustBe unit
      verify(emailClient).sendOnlineTestFailed(any(), any())(eqTo(hc1))

      service.emailCandidate(failedTest, emailAddress).futureValue mustBe unit
      verify(emailClient).sendOnlineTestFailed(any(), any())(eqTo(hc2))
    }

    "audit an event after sending" in new EmailCandidateFixture {
      service.emailCandidate(failedTest, emailAddress).futureValue mustBe unit

      verify(audit).logEventNoRequest(
        "FailedOnlineTestNotificationEmailed",
        Map("userId" -> userId, "email" -> emailAddress)
      )
    }
  }

  "when updating the application status" should {
    "mark the relevant application as failed" in new CommitFailedStatusFixture {
      service.commitNotifiedStatus(failedTest).futureValue mustBe unit

      // TODO FAST STREAM FIX ME verify(applicationRepository).updateStatus(userId, "ONLINE_TEST_FAILED_NOTIFIED")
    }

    "audit an event after updating the application status" in new CommitFailedStatusFixture {
      service.commitNotifiedStatus(failedTest).futureValue mustBe unit

      //verify(audit).logEventNoRequest(
      //  "FailedOnlineTest",
      //  Map("userId" -> userId)
      //)
    }
  }

  // The call to `Logger.info` within our implementation appears to add sufficient latency
  // to cause timeouts using the default configuration for the `futureValue` helper method.
  override implicit def patienceConfig = PatienceConfig(timeout = scaled(Span(2000, Millis)))

  trait Fixture {
    val applicationId = "abc"
    val userId = "xyz"
    val preferredName = "Jon"
    val emailAddress = "jon@test.com"
    val contactDetails = ContactDetails(Address("line 1"), "HP27 9JU", emailAddress, None)
    val failedTest = ApplicationForNotification(applicationId, userId, preferredName, PHASE1_TESTS_FAILED)
    def hc = HeaderCarrier()

    val ec = scala.concurrent.ExecutionContext.Implicits.global
    val applicationRepository = mock[GeneralApplicationRepository]
    val otRepository = mock[Phase1TestRepository]
    val cdRepository = mock[ContactDetailsRepository]
    val emailClient = mock[CSREmailClient]
    val audit = mock[AuditService]
    val service = spy(new OnlineTestFailureServiceImpl(applicationRepository, otRepository,
      cdRepository, emailClient, audit, hc
    )(ec))
  }

  trait ProcessNextFailedFixture extends Fixture {
    doReturnAsync()
      .when(service).processFailedTest(any())
  }

  trait ProcessFailedFixture extends Fixture {
    when(cdRepository.find(any())).thenReturnAsync(contactDetails)
    doReturnAsync()
      .when(service).emailCandidate(any(), any())
    doReturnAsync()
      .when(service).commitNotifiedStatus(any())
  }

  trait EmailCandidateFixture extends Fixture {
    when(emailClient.sendOnlineTestFailed(any(), any())(any())).thenReturnAsync()
  }

  trait CommitFailedStatusFixture extends Fixture {
    when(applicationRepository.updateStatus(any(), any())).thenReturnAsync()
  }
}
