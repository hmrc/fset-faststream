package controllers

import connectors.ApplicationClient
import play.api.test.Helpers._


class DiversityQuestionnaireSpec extends BaseControllerSpec {

  val applicationClient = mock[ApplicationClient]

  def controllerUnderTest = new QuestionnaireController(applicationClient)

  "firstPage in the questionnaire " should {
    "redirect user to the home page when filled in already" in {
      val result = controllerUnderTest.firstPageView()(fakeRequest)
      status(result) mustBe SEE_OTHER
    }
  }

}
