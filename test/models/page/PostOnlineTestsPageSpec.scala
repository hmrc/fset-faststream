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

import connectors.ReferenceDataExamples._
import connectors.exchange.SchemeEvaluationResult
import connectors.exchange.referencedata.SchemeId
import connectors.exchange.sift.SiftAnswersStatus
import controllers.UnitSpec
import helpers.{ CachedUserMetadata, CurrentSchemeStatus }
import models.ApplicationData.ApplicationStatus
import models._

class PostOnlineTestsPageSpec extends UnitSpec {

  "PostOnlineTestsPage" should {

    def randUUID = UniqueIdentifier(UUID.randomUUID().toString)

    val userDataWithApp = CachedDataWithApp(
      CachedUser(randUUID, "firstname", "lastname", Some("prefName"), "email@email.com", isActive = true, "unlocked"),
      ApplicationData(randUUID, randUUID,
        ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED, ApplicationRoute.Faststream, ProgressResponseExamples.siftEntered,
        civilServiceExperienceDetails = None, edipCompleted = None, overriddenSubmissionDeadline = None
      )
    )


    "be correctly built candidates after phase 3" in {
      val phase3Results = SchemeEvaluationResult(SchemeId("Commercial"), SchemeStatus.Red.toString)  ::
        SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), SchemeStatus.Red.toString) ::
        SchemeEvaluationResult(SchemeId("HumanResources"), SchemeStatus.Green.toString) ::
      Nil
      val cachedUserMetadata = CachedUserMetadata(userDataWithApp.user, userDataWithApp.application, Schemes.AllSchemes, phase3Results)

      val page = PostOnlineTestsPage.apply(cachedUserMetadata, None, None, hasAnalysisExercise = false)

      page.userDataWithApp.successfulSchemes mustBe CurrentSchemeStatus(Schemes.HR, SchemeStatus.Green, failedAtStage = None) :: Nil

      page.userDataWithApp.failedSchemes mustBe  CurrentSchemeStatus(Schemes.Commercial, SchemeStatus.Red, failedAtStage
        = Some("online tests")) :: CurrentSchemeStatus(Schemes.DaT, SchemeStatus.Red, failedAtStage = Some("online tests")) :: Nil

      page.userDataWithApp.withdrawnSchemes mustBe Nil
      page.hasAssessmentCentreRequirement mustBe true
      page.userDataWithApp.hasNumericRequirement mustBe false
      page.userDataWithApp.hasFormRequirement mustBe false
    }

  }

}
