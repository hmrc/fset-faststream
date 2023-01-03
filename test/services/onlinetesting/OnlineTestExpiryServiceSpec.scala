/*
 * Copyright 2023 HM Revenue & Customs
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

//package services.onlinetesting
//
//import connectors.EmailClient
//import model.Address
//import model.PersistedObjects.{ ContactDetails, ExpiringOnlineTest }
//import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
//import org.mockito.Mockito._
//import org.scalatest.concurrent.ScalaFutures
//import org.scalatest.time.{ Millis, Span }
//import repositories.ContactDetailsRepository
//import repositories.onlinetesting.OnlineTestRepository
//import services.AuditService
//import testkit.MockitoImplicits.{ OngoingStubbingExtension, OngoingStubbingExtensionUnit }
//import testkit.MockitoSugar
//import uk.gov.hmrc.play.http.HeaderCarrier

//class OnlineTestExpiryServiceSpec extends UnitSpec {

  //"when processing the next expiring test" should {
  //  "do nothing when there are no expiring tests" in new ProcessNextExpiredFixture {
  //    when(otRepository.nextApplicationPendingExpiry).thenReturnAsync(None)

  //    service.processNextExpiredTest().futureValue mustBe (())
  //  }

  //  "process the next expiring test" in new ProcessNextExpiredFixture {
  //    when(otRepository.nextApplicationPendingExpiry).thenReturnAsync(Some(expiringTest))

  //    service.processNextExpiredTest().futureValue mustBe (())

  //    verify(service).processExpiredTest(expiringTest)
  //  }
  //}

  //"when processing an expiring test" should {
  //  "email the candidate about their expired online test" in new ProcessExpiredFixture {
  //    service.processExpiredTest(expiringTest).futureValue mustBe (())

  //    verify(service).emailCandidate(expiringTest, emailAddress)
  //  }

  //  "update the application status on success" in new ProcessExpiredFixture {
  //    service.processExpiredTest(expiringTest).futureValue mustBe (())

  //    verify(service).commitExpiredStatus(expiringTest)
  //  }

  //  "not update the application status on failure" in new ProcessExpiredFixture {
  //    doThrowAsync().when(service).emailCandidate(any(), any())

  //    val result = service.processExpiredTest(expiringTest).failed.futureValue

  //    result mustBe an[Exception]
  //    verify(service).emailCandidate(expiringTest, emailAddress)
  //    verify(service, never).commitExpiredStatus(expiringTest)
  //  }
  //}

  //"when emailing a candidate" should {
  //  "send the email to their email address" in new EmailCandidateFixture {
  //    service.emailCandidate(expiringTest, emailAddress).futureValue mustBe (())

  //    verify(emailClient).sendOnlineTestExpired(eqTo(emailAddress), any())(any())
  //  }

  //  "greet candidate by their preferred name" in new EmailCandidateFixture {
  //    service.emailCandidate(expiringTest, emailAddress).futureValue mustBe (())

  //    verify(emailClient).sendOnlineTestExpired(any(), eqTo(preferredName))(any())
  //  }

  //  "attach a 'header carrier' with the current timestamp" in new EmailCandidateFixture {
  //    // Test ensures we're not making the mistake of caching the HeaderCarrier, which
  //    // would result in an incorrect timestamp being passed on to other components.
  //    val hc1 = HeaderCarrier(nsStamp = 1)
  //    val hc2 = HeaderCarrier(nsStamp = 2)
  //    val hcs = List(hc1, hc2).iterator
  //    override def hc = hcs.next()

  //    service.emailCandidate(expiringTest, emailAddress).futureValue mustBe (())
  //    verify(emailClient).sendOnlineTestExpired(any(), any())(eqTo(hc1))

  //    service.emailCandidate(expiringTest, emailAddress).futureValue mustBe (())
  //    verify(emailClient).sendOnlineTestExpired(any(), any())(eqTo(hc2))
  //  }

  //  "audit an event after sending" in new EmailCandidateFixture {
  //    service.emailCandidate(expiringTest, emailAddress).futureValue mustBe (())

  //    verify(audit).logEventNoRequest(
  //      "ExpiredOnlineTestNotificationEmailed",
  //      Map("userId" -> userId, "email" -> emailAddress)
  //    )
  //  }
  //}

  //"when updating the application status" should {
  //  "mark the relevant application as expired" in new CommitExpiredStatusFixture {
  //    service.commitExpiredStatus(expiringTest).futureValue mustBe (())

  //    verify(otRepository).updateStatus(userId, "ONLINE_TEST_EXPIRED")
  //  }

  //  "audit an event after updating the application status" in new CommitExpiredStatusFixture {
  //    service.commitExpiredStatus(expiringTest).futureValue mustBe (())

  //    verify(audit).logEventNoRequest(
  //      "ExpiredOnlineTest",
  //      Map("userId" -> userId)
  //    )
  //  }
  //}

  // The call to `Logger.info` within our implementation appears to add sufficient latency
  // to cause timeouts using the default configuration for the `futureValue` helper method.
  //override implicit def patienceConfig = PatienceConfig(timeout = scaled(Span(2000, Millis)))

  //trait Fixture {
  //  val applicationId = "abc"
  //  val userId = "xyz"
  //  val preferredName = "Jon"
  //  val emailAddress = "jon@test.com"
  //  val contactDetails = ContactDetails(Address("line 1"), "HP27 9JU", emailAddress, None)
  //  val expiringTest = ExpiringOnlineTest(applicationId, userId, preferredName)
  //  def hc = HeaderCarrier()

  //  val ec = scala.concurrent.ExecutionContext.Implicits.global
  //  val otRepository = mock[OnlineTestRepository]
  //  val cdRepository = mock[ContactDetailsRepository]
  //  val emailClient = mock[EmailClient]
  //  val audit = mock[AuditService]
  //  val service = spy(new OnlineTestExpiryServiceImpl(otRepository, cdRepository, emailClient, audit, hc)(ec))
  //}

  //trait ProcessNextExpiredFixture extends Fixture {
  //  doReturnAsync()
  //    .when(service).processExpiredTest(any())
  //}

  //trait ProcessExpiredFixture extends Fixture {
  //  when(cdRepository.find(any())).thenReturnAsync(contactDetails)
  //  doReturnAsync()
  //    .when(service).emailCandidate(any(), any())
  //  doReturnAsync()
  //    .when(service).commitExpiredStatus(any())
  //}

  //trait EmailCandidateFixture extends Fixture {
  //  when(emailClient.sendOnlineTestExpired(any(), any())(any())).thenReturnAsync()
  //}

  //trait CommitExpiredStatusFixture extends Fixture {
  //  when(otRepository.updateStatus(any(), any())).thenReturnAsync()
  //}
//}
