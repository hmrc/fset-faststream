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
import connectors.exchange.{ EventsExamples, SchemeEvaluationResult }
import connectors.exchange.candidateevents.CandidateAllocationWithEvent
import connectors.exchange.referencedata.SchemeId
import connectors.exchange.sift.SiftAnswersStatus
import controllers.UnitSpec
import helpers.{ CachedUserWithSchemeData, CurrentSchemeStatus }
import models.ApplicationData.ApplicationStatus
import models._
import models.events.AllocationStatuses

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
      val cachedUserMetadata = CachedUserWithSchemeData(userDataWithApp.user, userDataWithApp.application, Schemes.AllSchemes, phase3Results)

      val page = PostOnlineTestsPage.apply(cachedUserMetadata, None, None, hasAnalysisExercise = false)

      page.userDataWithSchemes.successfulSchemes mustBe CurrentSchemeStatus(Schemes.HR, SchemeStatus.Green, failedAtStage = None) :: Nil

      page.userDataWithSchemes.failedSchemes mustBe  CurrentSchemeStatus(Schemes.Commercial, SchemeStatus.Red, failedAtStage
        = Some("online tests")) :: CurrentSchemeStatus(Schemes.DaT, SchemeStatus.Red, failedAtStage = Some("online tests")) :: Nil

      page.userDataWithSchemes.withdrawnSchemes mustBe Nil
      page.userDataWithSchemes.hasNumericRequirement mustBe false
      page.userDataWithSchemes.hasFormRequirement mustBe false
      page.stage mustBe PostOnlineTestsStage.OTHER
    }

    "application is in correct stage" in {
      val app = userDataWithApp.application.copy(
        progress = userDataWithApp.application.progress.copy(
        assessmentCentre = userDataWithApp.application.progress.assessmentCentre.copy(allocationUnconfirmed = true)
      ))
      val cachedUserMetadata = CachedUserWithSchemeData(userDataWithApp.user, app, Schemes.AllSchemes, Seq.empty)

      val allocation = CandidateAllocationWithEvent(
        cachedUserMetadata.application.applicationId.toString,
        "",
        AllocationStatuses.UNCONFIRMED,
        EventsExamples.Event1
      )
      val page = PostOnlineTestsPage.apply(cachedUserMetadata, Some(allocation), None, hasAnalysisExercise = false)

      page.stage mustBe PostOnlineTestsStage.ALLOCATED_TO_EVENT

    }

  }

}
