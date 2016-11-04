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

package services

import model.Commands.Candidate
import model.events.AuditEvents
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.personaldetails.PersonalDetailsRepository
import scheduler.fixer.{ FixRequiredType, PassToPhase2, RequiredFixes, ResetPhase1TestInvitedSubmitted }
import services.application.ApplicationService
import services.events.EventServiceFixture
import testkit.ExtendedTimeout
import uk.gov.hmrc.play.http.HeaderCarrier
import org.mockito.Matchers.{ any, eq => eqTo }
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.Future


class ApplicationServiceSpec extends PlaySpec with MockitoSugar with ScalaFutures with ExtendedTimeout {

  "fix" should {
    "process all issues we have examples of" in new ApplicationServiceTest {
      when(appRepositoryMock.getApplicationsToFix(FixRequiredType(PassToPhase2, 1))).thenReturn(getApplicationsToFixSuccess2)
      when(appRepositoryMock.getApplicationsToFix(FixRequiredType(ResetPhase1TestInvitedSubmitted, 1))).thenReturn(getApplicationsToFixSuccess1)
      when(appRepositoryMock.fix(candidate1, FixRequiredType(PassToPhase2, 1))).thenReturn(Future.successful(Some(candidate1)))
      when(appRepositoryMock.fix(candidate2, FixRequiredType(PassToPhase2, 1))).thenReturn(Future.successful(Some(candidate2)))
      when(appRepositoryMock.fix(candidate3, FixRequiredType(ResetPhase1TestInvitedSubmitted, 1))).
        thenReturn(Future.successful(Some(candidate3)))

      val result = underTest.fix(FixRequiredType(PassToPhase2, 1) :: FixRequiredType(ResetPhase1TestInvitedSubmitted, 1) :: Nil)(hc, rh).
        futureValue
      result mustBe ()

      verify(appRepositoryMock, times(3)).fix(any[Candidate], any[FixRequiredType])
      verify(underTest.auditEventHandlerMock, times(3)).handle(any[AuditEvents.FixedProdData])(any[HeaderCarrier], any[RequestHeader])
      verifyZeroInteractions(pdRepositoryMock, cdRepositoryMock, underTest.dataStoreEventHandlerMock, underTest.emailEventHandlerMock)
      verifyNoMoreInteractions(underTest.auditEventHandlerMock)
    }

    "don't fix anything if no issue is detected" in new ApplicationServiceTest {
      when(appRepositoryMock.getApplicationsToFix(FixRequiredType(PassToPhase2, 1))).thenReturn(getApplicationsToFixEmpty)

      val result = underTest.fix(FixRequiredType(PassToPhase2, 1) :: Nil)(hc, rh).futureValue
      result mustBe ()

      verify(appRepositoryMock, times(0)).fix(any[Candidate], any[FixRequiredType])
      verifyZeroInteractions(underTest.auditEventHandlerMock)
    }

    "proceeds with the others searches if one of them fails" in new ApplicationServiceTest {
      when(appRepositoryMock.getApplicationsToFix(FixRequiredType(PassToPhase2, 1))).thenReturn(getApplicationsToFixSuccess1)
      when(appRepositoryMock.getApplicationsToFix(FixRequiredType(ResetPhase1TestInvitedSubmitted, 1))).thenReturn(failure)
      when(appRepositoryMock.fix(candidate3, FixRequiredType(PassToPhase2, 1))).thenReturn(Future.successful(Some(candidate3)))

      val result = underTest.fix(FixRequiredType(PassToPhase2, 1) :: FixRequiredType(ResetPhase1TestInvitedSubmitted, 1) :: Nil)(hc, rh)
      result.failed.futureValue mustBe generalException

      verify(appRepositoryMock, times(1)).fix(candidate3, FixRequiredType(PassToPhase2, 1))
      verify(underTest.auditEventHandlerMock).handle(any[AuditEvents.FixedProdData])(any[HeaderCarrier], any[RequestHeader])
      verifyZeroInteractions(underTest.auditEventHandlerMock)
    }

    "publish an event if the fix of a specific issue fails" in new ApplicationServiceTest {
      when(appRepositoryMock.getApplicationsToFix(FixRequiredType(PassToPhase2, 1))).thenReturn(getApplicationsToFixSuccess1)
      when(appRepositoryMock.fix(candidate3, FixRequiredType(PassToPhase2, 1))).thenReturn(failure)

      val result = underTest.fix(FixRequiredType(PassToPhase2, 1) :: Nil)(hc, rh).futureValue
      result mustBe ()

      verify(appRepositoryMock, times(1)).fix(candidate3, FixRequiredType(PassToPhase2, 1))
      verify(underTest.auditEventHandlerMock).handle(any[AuditEvents.FailedFixedProdData])(any[HeaderCarrier], any[RequestHeader])
      verifyZeroInteractions(underTest.auditEventHandlerMock)
    }
  }

  trait ApplicationServiceTest {

    val appRepositoryMock: GeneralApplicationRepository = mock[GeneralApplicationRepository]
    val pdRepositoryMock: PersonalDetailsRepository = mock[PersonalDetailsRepository]
    val cdRepositoryMock: ContactDetailsRepository = mock[ContactDetailsRepository]

    val underTest = new ApplicationService with EventServiceFixture {
      val appRepository = appRepositoryMock
      val pdRepository = pdRepositoryMock
      val cdRepository = cdRepositoryMock
    }

    implicit val hc = HeaderCarrier()
    implicit val rh = mock[RequestHeader]

    val candidate1 = Candidate(userId = "user123", applicationId = Some("appId234"), email = Some("george.foreman@bogus128.com.biv"),
      None, None, None, None, None, None, None, None)

    val candidate2 = Candidate(userId = "user456", applicationId = Some("appId4567"), email = Some("wilfredo.gomez@bazooka128.com.biv"),
      None, None, None, None, None, None, None, None)

    val candidate3 = Candidate(userId = "user569", applicationId = Some("appId84512"), email = Some("carmen.basilio@bogus128.com.biv"),
      None, None, None, None, None, None, None, None)

    val generalException = new RuntimeException("something went wrong")
    val failure = Future.failed(generalException)

    val getApplicationsToFixSuccess2: Future[List[Candidate]] = Future.successful(candidate1 :: candidate2 :: Nil)
    val getApplicationsToFixSuccess1: Future[List[Candidate]] = Future.successful(candidate3 :: Nil)
    val getApplicationsToFixFailure: Future[List[Candidate]] = Future.failed(generalException)
    val getApplicationsToFixEmpty: Future[List[Candidate]] = Future.successful(Nil)
    val success = Future.successful(())

  }
}
