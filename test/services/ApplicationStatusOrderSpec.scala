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

import model.ApplicationStatusOrder
import model.Commands.ProgressResponse
import org.scalatestplus.play.PlaySpec

class ApplicationStatusOrderSpec extends PlaySpec {

  import ApplicationStatusOrderSpec._

  "no progress status" should {
    "return registered" in {
      ApplicationStatusOrder.getStatus(None) must be("registered")
    }
  }

  "a registered application" should {
    "return registered" in {
      val status = ApplicationStatusOrder.getStatus(new ProgressResponse("id", false, false, false, false, Nil,
        false, false, false, false, false, false))
      status must be("registered")
    }
  }

  "a withdrawn application" should {
    "return withdrawn" in {
      ApplicationStatusOrder.getStatus(progress) must be("withdrawn")
    }
    "return withdrawn when all other progresses are set" in {
      ApplicationStatusOrder.getStatus(completeProgress) must be("withdrawn")
    }
  }

  "a submitted application" should {
    "return submitted" in {
      val customProgress = progress.copy(withdrawn = false)
      ApplicationStatusOrder.getStatus(customProgress) must be("submitted")
    }
  }

  "a reviewed application" should {
    "return reviewed" in {
      val customProgress = progress.copy(withdrawn = false, submitted = false,
        questionnaire = Nil)
      ApplicationStatusOrder.getStatus(customProgress) must be("review_completed")
    }
  }

  "an application in framework and locations" should {
    "return schemes_and_locations_completed" in {
      val customProgress = emptyProgress.copy(personalDetails = true, schemePreferences = true)
      ApplicationStatusOrder.getStatus(customProgress) must be("scheme_preferences_completed")
    }
  }

  "an application in personal details" should {
    "return personal_details_completed" in {
      val customProgress = emptyProgress.copy(personalDetails = true)
      ApplicationStatusOrder.getStatus(customProgress) must be("personal_details_completed")
    }
    "return personal_details_completed when sections are not completed" in {
      val customProgress = emptyProgress.copy(personalDetails = true, schemePreferences = false)
      ApplicationStatusOrder.getStatus(customProgress) must be("personal_details_completed")
    }
  }

  "non-submitted status" should {
    import ApplicationStatusOrder._

    "be true for non submitted progress" in {
      isNonSubmittedStatus(emptyProgress.copy(submitted = false, withdrawn = false)) must be(true)
    }

    "be false for withdrawn progress" in {
      isNonSubmittedStatus(emptyProgress.copy(submitted = true, withdrawn = true)) must be(false)
      isNonSubmittedStatus(emptyProgress.copy(submitted = false, withdrawn = true)) must be(false)
    }

    "be false for submitted but not withdrawn progress" in {
      isNonSubmittedStatus(emptyProgress.copy(submitted = true, withdrawn = false)) must be(false)
    }
  }
}

object ApplicationStatusOrderSpec {

  val progress = ProgressResponse("1", true, true, true, true,
    List("start_questionnaire", "diversity_questionnaire", "education_questionnaire", "occupation_questionnaire"), true, true)

  val emptyProgress = ProgressResponse("1")

  val completeProgress = ProgressResponse("1", true, true, true, true,
    List("start_questionnaire", "diversity_questionnaire", "education_questionnaire",
      "occupation_questionnaire"), true, true, true, true, true, true)
}
