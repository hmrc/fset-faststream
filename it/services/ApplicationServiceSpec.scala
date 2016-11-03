package services

import model.Commands.Candidate
import model.events.AuditEvents
import model.events.AuditEvents.FixedProdData
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.personaldetails.PersonalDetailsRepository
import scheduler.fixer.{ ExpiredPhase1InvitedToPhase2, FixRequiredType, PassToPhase2, RequiredFixes }
import services.application.ApplicationService
import services.events.{ EventService, EventServiceFixture }
import testkit.{ ExtendedTimeout, MongoRepositorySpec }
import uk.gov.hmrc.play.http.HeaderCarrier
import org.mockito.Matchers.{ any, eq => eqTo }
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.Future


class ApplicationServiceSpec extends PlaySpec with MockitoSugar with ScalaFutures with ExtendedTimeout {

  "ApplicationService" should {
    "fix all the issues we have examples of" in new ApplicationServiceTest {
      when(appRepositoryMock.getApplicationsToFix(PassToPhase2)).thenReturn(getApplicationsToFixSuccess2)
      when(appRepositoryMock.getApplicationsToFix(ExpiredPhase1InvitedToPhase2)).thenReturn(getApplicationsToFixSuccess1)
      when(appRepositoryMock.fix(candidate1, PassToPhase2)).thenReturn(Future.successful(Some(candidate1)))
      //when(appRepositoryMock.fix(candidate2, PassToPhase2)).thenReturn(Future.successful(Some(candidate2)))
      when(appRepositoryMock.fix(candidate3, ExpiredPhase1InvitedToPhase2)).thenReturn(Future.successful(Some(candidate3)))

      val result = underTest.fix(RequiredFixes.fixes)(hc, rh).futureValue

      result mustBe ()

      verify(appRepositoryMock, times(2)).fix(any[Candidate], any[FixRequiredType])
      //verify(underTest.auditEventHandlerMock, times(2)).handle(any[AuditEvents.FixedProdData])(any[HeaderCarrier], any[RequestHeader])
      //verifyZeroInteractions(pdRepositoryMock, cdRepositoryMock, underTest.dataStoreEventHandlerMock, underTest.emailEventHandlerMock)
      //verifyNoMoreInteractions(underTest.auditEventHandlerMock)
      //reset(appRepositoryMock)

    }

    "don't fix anything if no issue is detected" in new ApplicationServiceTest {
      when(appRepositoryMock.getApplicationsToFix(PassToPhase2)).thenReturn(getApplicationsToFixEmpty)
      when(appRepositoryMock.getApplicationsToFix(ExpiredPhase1InvitedToPhase2)).thenReturn(getApplicationsToFixEmpty)

      val result = underTest.fix(RequiredFixes.fixes)(hc, rh).futureValue

      result mustBe ()

      verify(appRepositoryMock, times(0)).fix(any[Candidate], any[FixRequiredType])
      verifyZeroInteractions(underTest.auditEventHandlerMock)
    }
  }

  trait ApplicationServiceTest {

    val appRepositoryMock: GeneralApplicationRepository = mock[GeneralApplicationRepository]
    //val eventServiceMock: EventService = eventService
    val pdRepositoryMock: PersonalDetailsRepository = mock[PersonalDetailsRepository]
    val cdRepositoryMock: ContactDetailsRepository = mock[ContactDetailsRepository]

    val underTest = new ApplicationService with EventServiceFixture {
      val appRepository = appRepositoryMock
      //val eventService = eventServiceMock
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

    val getApplicationsToFixSuccess2: Future[List[Candidate]] = Future.successful(candidate1 :: Nil)
    val getApplicationsToFixSuccess1: Future[List[Candidate]] = Future.successful(candidate3 :: Nil)
    val getApplicationsToFixFailure: Future[List[Candidate]] = Future.failed(generalException)
    val getApplicationsToFixEmpty: Future[List[Candidate]] = Future.successful(Nil)
    val success = Future.successful(())
  }
}


