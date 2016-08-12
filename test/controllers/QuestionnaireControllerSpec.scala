package controllers

import connectors.ApplicationClient
import models.CachedDataWithApp
import models.Progress
import models.ApplicationData.ApplicationStatus._
import play.api.test.Helpers._
import play.api.mvc.Result
import scala.concurrent._


class QuestionnaireControllerSpec extends BaseControllerSpec {

  val applicationClient = mock[ApplicationClient]
  val candidateWithApp = currentCandidateWithApp.copy(
    application = currentCandidateWithApp.application.copy(applicationStatus = IN_PROGRESS))
  val errorContent = "You've now completed this part of the application and for security reasons you can't go back and change your answers."

  def controllerUnderTest(appStatus: Progress) = new QuestionnaireController(applicationClient) with TestableSecureActions {
    override val CandidateWithApp: CachedDataWithApp = candidateWithApp
      .copy(application = candidateWithApp.application.copy(progress = appStatus))
  }

  "start" should {
    "load start page when the questionnaire is not started already" in {
      val applicationReviewed = candidateWithApp.application.progress.copy(review = true)
      val result = controllerUnderTest(applicationReviewed).start()(fakeRequest)
      val content = contentAsString(result)
      status(result) mustBe OK
      content must include ("We have a few more questions before you submit your application")
    }

    "load continue questionnaire page when the questionnaire is started already" in {
      val questionnaireStarted = candidateWithApp.application.progress.copy(diversityQuestionnaire = true)
      val result = controllerUnderTest(questionnaireStarted).start()(fakeRequest)
      val content = contentAsString(result)
      status(result) mustBe OK
      content must include ("Page 1 of the diversity questionnaire")
    }

    "redirect to home page when the questionnaire is completed already" in {
      val questionnaireCompleted = candidateWithApp.application.progress.copy(diversityQuestionnaire = true,
        educationQuestionnaire = true, occupationQuestionnaire = true)
      val result = controllerUnderTest(questionnaireCompleted).start()(fakeRequest)
      assertHomePageRedirect(result)
    }
  }

  "firstPageView" should {
    "load first page when not filled in already" in {
      val questionnaireStarted = candidateWithApp.application.progress.copy(startedQuestionnaire = true)
      val result = controllerUnderTest(questionnaireStarted).firstPageView()(fakeRequest)
      val content = contentAsString(result)
      status(result) mustBe OK
      content must include ("About you")
    }

    "redirect to continue questionnaire page, when the first page is completed already" in {
      val diversityQuestCompleted = candidateWithApp.application.progress.copy(startedQuestionnaire = true, diversityQuestionnaire = true)
      val result = controllerUnderTest(diversityQuestCompleted).firstPageView()(fakeRequest)
      assertQuestionnaireContinueRedirect(result)
    }

    "redirect to home page, when the questionnaire is completed already" in {
      val questionnaireStarted = candidateWithApp.application.progress.copy(diversityQuestionnaire = true,
        educationQuestionnaire = true, occupationQuestionnaire = true)
      val result = controllerUnderTest(questionnaireStarted).firstPageView()(fakeRequest)
      assertHomePageRedirect(result)
    }
  }

  "secondPageView" should {
    "load second page when not filled in already" in {
      val diversityQuestCompleted = candidateWithApp.application.progress.copy(diversityQuestionnaire = true)
      val result = controllerUnderTest(diversityQuestCompleted).secondPageView()(fakeRequest)
      val content = contentAsString(result)
      status(result) mustBe OK
      content must include ("Diversity questions")
    }

    "redirect to continue questionnaire page, when the second page is completed already" in {
      val educationQuestCompleted = candidateWithApp.application.progress.copy(educationQuestionnaire = true)
      val result = controllerUnderTest(educationQuestCompleted).secondPageView()(fakeRequest)
      assertQuestionnaireContinueRedirect(result)
    }

    "redirect to home page, when the questionnaire is completed already" in {
      val questionnaireStarted = candidateWithApp.application.progress.copy(diversityQuestionnaire = true,
        educationQuestionnaire = true, occupationQuestionnaire = true)
      val result = controllerUnderTest(questionnaireStarted).secondPageView()(fakeRequest)
      assertHomePageRedirect(result)
    }
  }

  "thirdPageView" should {
    "load third page when not filled in already" in {
      val educationQuestCompleted = candidateWithApp.application.progress.copy(educationQuestionnaire = true)
      val result = controllerUnderTest(educationQuestCompleted).thirdPageView()(fakeRequest)
      val content = contentAsString(result)
      status(result) mustBe OK
      content must include ("About your parents")
    }

    "redirect to continue questionnaire page, when the third page is completed already" in {
      val occupationQuestCompleted = candidateWithApp.application.progress.copy(occupationQuestionnaire = true)
      val result = controllerUnderTest(occupationQuestCompleted).thirdPageView()(fakeRequest)
      assertQuestionnaireContinueRedirect(result)
    }

    "redirect to home page, when the questionnaire is completed already" in {
      val questionnaireStarted = candidateWithApp.application.progress.copy(diversityQuestionnaire = true,
        educationQuestionnaire = true, occupationQuestionnaire = true)
      val result = controllerUnderTest(questionnaireStarted).thirdPageView()(fakeRequest)
      assertHomePageRedirect(result)
    }
  }

  private def assertHomePageRedirect(result:Future[Result]): Unit = {
    status(result) mustBe SEE_OTHER
    redirectLocation(result) must be(Some(routes.HomeController.present().url))
    flash(result).data mustBe Map("danger" -> errorContent)
  }

  private def assertQuestionnaireContinueRedirect(result:Future[Result]): Unit = {
    status(result) mustBe SEE_OTHER
    redirectLocation(result) must be(Some(routes.QuestionnaireController.start().url))
    flash(result).data mustBe Map("danger" -> errorContent)
  }

}
