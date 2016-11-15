package controllers

import config.{ CSRCache, CSRHttp }
import connectors.ApplicationClient
import connectors.UserManagementClient.TokenEmailPairInvalidException
import connectors.exchange.InvigilatedTestUrl
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.test.Helpers._
import testkit.BaseControllerSpec

import scala.concurrent.Future

class InvigilatedControllerSpec extends BaseControllerSpec {

  "present" should {
    "display the Start invigilated e-tray page" in new TestFixture {
      val result = underTest.present()(fakeRequest)
      status(result) must be(OK)
      val content = contentAsString(result)
      content must include("Start invigilated e-tray")
    }
  }

  "verifyToken" should {
    "redirect to test url upon successful token validation" in new TestFixture {
      val Request = fakeRequest.withFormUrlEncodedBody("email" -> "test@test.com", "token" -> "KI6U8T")
      when(mockApplicationClient.verifyInvigilatedToken(eqTo("test@test.com"), eqTo("KI6U8T"))(any())).thenReturn(succesfulValidationResponse)

      val result = underTest.verifyToken()(Request)
      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some(testUrl))
    }
    "display the Start invigilated e-tray page with an error message" in new TestFixture {
      val Request = fakeRequest.withFormUrlEncodedBody("email" -> "test@test.com", "token" -> "KI6U8T")
      when(mockApplicationClient.verifyInvigilatedToken(eqTo("test@test.com"), eqTo("KI6U8T"))(any())).thenReturn(failedValidationResponse)

      val result = underTest.verifyToken()(Request)
      status(result) must be(OK)
      val content = contentAsString(result)
      content must include("Start invigilated e-tray")
      content must include("Invalid email or access code")
    }
  }

  trait TestFixture {
    val mockApplicationClient = mock[ApplicationClient]
    val mockCacheClient = mock[CSRCache]
    val testUrl = "http://localhost:9284/fset-fast-stream/invigilated-etray"
    val succesfulValidationResponse = Future.successful(InvigilatedTestUrl(testUrl))
    val failedValidationResponse = Future.failed(new TokenEmailPairInvalidException())

    class TestableInvigilatedController extends InvigilatedController(mockApplicationClient, mockCacheClient) {
      val http: CSRHttp = CSRHttp
    }

    val underTest = new TestableInvigilatedController
  }

}
