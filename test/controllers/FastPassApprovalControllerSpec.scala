package controllers


import model.command.FastPassEvaluation
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.fastpass.FastPassService
import testkit.UnitWithAppSpec
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class FastPassApprovalControllerSpec extends UnitWithAppSpec {

  "FastPassApprovalController" should {
    "respond ok" in new TestFixture {
      when(mockFastPassService.processFastPassCandidate(any[String], any[String], any[Boolean], any[String])
      (any[HeaderCarrier], any[RequestHeader])).thenReturn(serviceResponse)
      val response = controllerUnderTest.processFastPassCandidate("userId", "appId")(fakeRequest(request))
      status(response) mustBe OK

      verify(mockFastPassService).processFastPassCandidate(eqTo("userId"), eqTo("appId"), eqTo(true), eqTo("adminId"))(
        any[HeaderCarrier](), any[RequestHeader])
      verifyNoMoreInteractions(mockFastPassService)
    }
  }


  trait TestFixture {
    implicit val hc = new HeaderCarrier()
    implicit val rh: RequestHeader = FakeRequest("GET", "some/path")
    val mockFastPassService = mock[FastPassService]
    val request = FastPassEvaluation(true, "adminId")
    val serviceResponse: Future[(String, String)] = Future.successful(("George", "Foreman"))

    def controllerUnderTest = new FastPassApprovalController {
      val fastPassService = mockFastPassService
    }
  }

}
