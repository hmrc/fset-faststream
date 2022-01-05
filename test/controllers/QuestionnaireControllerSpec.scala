/*
 * Copyright 2022 HM Revenue & Customs
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

import forms.{DiversityQuestionnaireForm, EducationQuestionnaireForm, ParentalOccupationQuestionnaireForm}
import models.ApplicationData.ApplicationStatus._
import models.{CachedDataWithApp, Progress}
import play.api.mvc.Result
import play.api.test.Helpers._
import testkit.TestableSecureActions

import scala.concurrent._

class QuestionnaireControllerSpec extends BaseControllerSpec {
  val errorContent = "questionnaire.completed"

  "start" should {
    "load start page when the questionnaire is not started already" in new TestFixture {
      val applicationPreviewed = candWithApp.application.progress.copy(preview = true)

      val result = controller(applicationPreviewed).presentStartOrContinue()(fakeRequest)

      val content = contentAsString(result)
      status(result) mustBe OK
      content must include ("We have a few more questions before you submit your application")
    }

    "load continue questionnaire page when the questionnaire is started already" in new TestFixture {
      val questionnaireStarted = candWithApp.application.progress.copy(diversityQuestionnaire = true)

      val result = controller(questionnaireStarted).presentStartOrContinue()(fakeRequest)

      val content = contentAsString(result)
      status(result) mustBe OK
      content must include ("About you")
    }

    "redirect to home page when the questionnaire is completed already" in new TestFixture {
      val questionnaireCompleted = candWithApp.application.progress.copy(diversityQuestionnaire = true,
        educationQuestionnaire = true, occupationQuestionnaire = true)
      val result = controller(questionnaireCompleted).presentStartOrContinue()(fakeRequest)
      assertHomePageRedirect(result)
    }
  }

  "firstPageView" should {
    "load first page when not filled in already" in new TestFixture {
      val questionnaireStarted = candWithApp.application.progress.copy(startedQuestionnaire = true)

      val result = controller(questionnaireStarted).presentFirstPage()(fakeRequest)

      val content = contentAsString(result)
      status(result) mustBe OK
      content must include ("About you")
    }

    "redirect to continue questionnaire page, when the first page is completed already" in new TestFixture {
      val diversityQuestCompleted = candWithApp.application.progress.copy(startedQuestionnaire = true, diversityQuestionnaire = true)
      val result = controller(diversityQuestCompleted).presentFirstPage()(fakeRequest)
      assertQuestionnaireContinueRedirect(result)
    }

    "redirect to home page, when the questionnaire is completed already" in new TestFixture {
      val questionnaireCompleted = candWithApp.application.progress.copy(diversityQuestionnaire = true,
        educationQuestionnaire = true, occupationQuestionnaire = true)

      val result = controller(questionnaireCompleted).presentFirstPage()(fakeRequest)

      assertHomePageRedirect(result)
    }
  }

  "secondPageView" should {
    "load second page when not filled in already" in new TestFixture {
      val diversityQuestCompleted = candWithApp.application.progress.copy(diversityQuestionnaire = true)

      val result = controller(diversityQuestCompleted).presentSecondPage()(fakeRequest)

      val content = contentAsString(result)
      status(result) mustBe OK
      content must include ("Diversity questions")
    }

    "redirect to continue questionnaire page, when the second page is completed already" in new TestFixture {
      val educationQuestCompleted = candWithApp.application.progress.copy(educationQuestionnaire = true)
      val result = controller(educationQuestCompleted).presentSecondPage()(fakeRequest)
      assertQuestionnaireContinueRedirect(result)
    }

    "redirect to home page, when the questionnaire is completed already" in new TestFixture {
      val questionnaireCompleted = candWithApp.application.progress.copy(diversityQuestionnaire = true,
        educationQuestionnaire = true, occupationQuestionnaire = true)
      val result = controller(questionnaireCompleted).presentSecondPage()(fakeRequest)
      assertHomePageRedirect(result)
    }
  }

  "thirdPageView" should {
    "load third page when not filled in already" in new TestFixture {
      val educationQuestCompleted = candWithApp.application.progress.copy(educationQuestionnaire = true)

      val result = controller(educationQuestCompleted).presentThirdPage()(fakeRequest)

      val content = contentAsString(result)
      status(result) mustBe OK
      content must include ("About your parents")
    }

    "redirect to continue questionnaire page, when the third page is completed already" in new TestFixture {
      val occupationQuestCompleted = candWithApp.application.progress.copy(occupationQuestionnaire = true)
      val result = controller(occupationQuestCompleted).presentThirdPage()(fakeRequest)
      assertQuestionnaireContinueRedirect(result)
    }

    "redirect to home page, when the questionnaire is completed already" in new TestFixture {
      val questionnaireCompleted = candWithApp.application.progress.copy(diversityQuestionnaire = true,
        educationQuestionnaire = true, occupationQuestionnaire = true)
      val result = controller(questionnaireCompleted).presentThirdPage()(fakeRequest)
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
    redirectLocation(result) must be(Some(routes.QuestionnaireController.presentStartOrContinue().url))
    flash(result).data mustBe Map("danger" -> errorContent)
  }

  trait TestFixture extends BaseControllerTestFixture {
    val candWithApp = currentCandidateWithApp.copy(
      application = currentCandidateWithApp.application.copy(applicationStatus = IN_PROGRESS))

    def controller(appStatus: Progress) = {
      val diversityFormWrapper = new DiversityQuestionnaireForm
      val educationFormWrapper = new EducationQuestionnaireForm
      val parentalOccupationFormWrapper = new ParentalOccupationQuestionnaireForm
      new QuestionnaireController(mockConfig, stubMcc, mockSecurityEnv, mockSilhouetteComponent,
        mockNotificationTypeHelper, mockApplicationClient,
        diversityFormWrapper, educationFormWrapper, parentalOccupationFormWrapper) with TestableSecureActions {

        override val candidateWithApp: CachedDataWithApp = candWithApp
          .copy(application = candWithApp.application.copy(progress = appStatus))
      }
    }
  }
}
