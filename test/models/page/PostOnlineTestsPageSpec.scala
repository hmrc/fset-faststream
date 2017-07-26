/*
 * Copyright 2017 HM Revenue & Customs
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

package models.page

import java.util.UUID

import connectors.exchange.SchemeEvaluationResult
import connectors.exchange.referencedata.{ Scheme, SchemeId, SiftRequirement }
import controllers.UnitSpec
import models.ApplicationData.ApplicationStatus
import models._

class PostOnlineTestsPageSpec extends UnitSpec {

  "PostOnlineTestsPage" should {

    def randUUID = UniqueIdentifier(UUID.randomUUID().toString)

    val userDataWithApp = CachedDataWithApp(
      CachedUser(randUUID, "firstname", "lastname", Some("prefName"), "email@email.com", isActive = true, "unlocked"),
      ApplicationData(randUUID, randUUID,
        ApplicationStatus.SIFT, ApplicationRoute.Faststream, ProgressResponseExamples.siftEntered, civilServiceExperienceDetails = None,
        edipCompleted = None, overriddenSubmissionDeadline = None
      )
    )

    val commercial = Scheme(SchemeId("Commercial"), "CFS", "Commercial", Some(SiftRequirement.NUMERIC_TEST), evaluationRequired = true)
    val dat = Scheme(SchemeId("DigitalAndTechnology"), "DAT", "Digital and Technology", Some(SiftRequirement.FORM), evaluationRequired = true)
    val hr = Scheme(SchemeId("HumanResources"), "HR", "Human Resources", None, evaluationRequired = false)

    val allSchemes = commercial :: dat :: hr :: Nil

    "be correctly built for non-siftable candidates" in {
      val phase3Results = SchemeEvaluationResult(SchemeId("Commercial"), SchemeStatus.Red.toString)  ::
        SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), SchemeStatus.Red.toString) ::
        SchemeEvaluationResult(SchemeId("HumanResources"), SchemeStatus.Green.toString) ::
      Nil

      val page = PostOnlineTestsPage.apply(userDataWithApp, phase3Results, allSchemes)

      page.successfulSchemes mustBe CurrentSchemeStatus(hr, SchemeStatus.Green, failedAtStage = None) :: Nil

      page.failedSchemes mustBe  CurrentSchemeStatus(commercial, SchemeStatus.Red, failedAtStage = Some("online tests")) ::
        CurrentSchemeStatus(dat, SchemeStatus.Red, failedAtStage = Some("online tests")) :: Nil

      page.withdrawnSchemes mustBe Nil
      page.hasAssessmentCentreRequirement mustBe true
      page.hasNumericRequirement mustBe false
      page.hasFormRequirement mustBe false
    }

  }

}
