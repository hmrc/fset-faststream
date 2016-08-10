package controllers

import com.mohiva.play.silhouette.api.{Env, EventBus, LoginInfo}
import com.mohiva.play.silhouette.api.services.IdentityService
import com.mohiva.play.silhouette.impl.User
import com.mohiva.play.silhouette.impl.authenticators.{DummyAuthenticator, SessionAuthenticator, SessionAuthenticatorService}
import com.mohiva.play.silhouette.test._
import connectors.ApplicationClient
import models.CachedDataExample
import org.mockito.Matchers._
import org.mockito.Mockito._
import play.api.test.Helpers._
import security.{CsrCredentialsProvider, SecurityEnvironment}

import scala.concurrent.Future
import scala.util.Right


class QuestionnaireControllerSpec extends BaseControllerSpec {

  val applicationClient = mock[ApplicationClient]
  val authenticatorService = mock[SessionAuthenticatorService]
  val authenticator = mock[SessionAuthenticator]
  val credentialsProvider = mock[CsrCredentialsProvider]
  val eventBus = mock[EventBus]


  def controllerUnderTest = new QuestionnaireController(applicationClient)

  "firstPage in the questionnaire " should {
    "redirect user to the home page when filled in already" in {
      val fakeIdentity = FakeIdentity(LoginInfo("fakeProvider", "fakeKey"))
      implicit val env = FakeEnvironment[FakeIdentity, DummyAuthenticator](Seq(fakeIdentity.loginInfo -> fakeIdentity))
      val result = controllerUnderTest.firstPageView()(fakeRequest.withAuthenticator(fakeIdentity.loginInfo))
      status(result) mustBe SEE_OTHER
    }
  }

}
