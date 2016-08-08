package controllers

import config.CSRHttp
import connectors.ApplicationClient

class ApplicationControllerSpec extends BaseControllerSpec {
  val applicationClient = mock[ApplicationClient]

  class TestableApplicationController extends ApplicationController(applicationClient) with TestableSecureActions {
    val http: CSRHttp = CSRHttp
    override protected def env = securityEnvironment
  }

  def controller = new TestableApplicationController

  "index" should {
    "redirect to sign in page" in {
      val result = controller.index()(fakeRequest)
      assertPageRedirection(result, routes.SignInController.signIn().url)
    }
  }

  "terms" should {
    "load terms page" in {
      val result = controller.terms()(fakeRequest)
      assertPageTitle(result, "Terms and conditions")
    }
  }

  "privacy" should {
    "load privacy pge" in {
      val result = controller.privacy()(fakeRequest)
      assertPageTitle(result, "Privacy and cookies")
    }
  }

  "helpdesk" should {
    "load helpdesk page" in {
      val result = controller.helpdesk()(fakeRequest)
      assertPageTitle(result, "Contact us")
    }
  }
}
