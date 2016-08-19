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
      val applicationPreviewed = candidateWithApp.application.progress.copy(preview = true)
      val result = controllerUnderTest(applicationPreviewed).startOrContinue()(fakeRequest)
      val content = contentAsString(result)
      status(result) mustBe OK
      content must include ("We have a few more questions before you submit your application")
    }

    "load continue questionnaire page when the questionnaire is started already" in {
      val questionnaireStarted = candidateWithApp.application.progress.copy(diversityQuestionnaire = true)
      val result = controllerUnderTest(questionnaireStarted).startOrContinue()(fakeRequest)
      val content = contentAsString(result)
      status(result) mustBe OK
      content must include ("Page 1 of the diversity questionnaire")
    }

    "redirect to home page when the questionnaire is completed already" in {
      val questionnaireCompleted = candidateWithApp.application.progress.copy(diversityQuestionnaire = true,
        educationQuestionnaire = true, occupationQuestionnaire = true)
      val result = controllerUnderTest(questionnaireCompleted).startOrContinue()(fakeRequest)
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
      val questionnaireCompleted = candidateWithApp.application.progress.copy(diversityQuestionnaire = true,
        educationQuestionnaire = true, occupationQuestionnaire = true)
      val result = controllerUnderTest(questionnaireCompleted).firstPageView()(fakeRequest)
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
      val questionnaireCompleted = candidateWithApp.application.progress.copy(diversityQuestionnaire = true,
        educationQuestionnaire = true, occupationQuestionnaire = true)
      val result = controllerUnderTest(questionnaireCompleted).secondPageView()(fakeRequest)
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
      val questionnaireCompleted = candidateWithApp.application.progress.copy(diversityQuestionnaire = true,
        educationQuestionnaire = true, occupationQuestionnaire = true)
      val result = controllerUnderTest(questionnaireCompleted).thirdPageView()(fakeRequest)
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
    redirectLocation(result) must be(Some(routes.QuestionnaireController.startOrContinue().url))
    flash(result).data mustBe Map("danger" -> errorContent)
  }

}
