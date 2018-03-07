/*
 * Copyright 2018 HM Revenue & Customs
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
import connectors.exchange.{ EventsExamples, SchemeEvaluationResult, SchemeEvaluationResultWithFailureDetails, SelectedSchemes }
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

    val selectedSchemes = SelectedSchemes(Schemes.AllSchemes.map(_.id.value), orderAgreed = true, eligible = true)

    val userDataWithApp = CachedDataWithApp(
      CachedUser(randUUID, "firstname", "lastname", Some("prefName"), "email@email.com", isActive = true, "unlocked"),
      ApplicationData(randUUID, randUUID,
        ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED, ApplicationRoute.Faststream, ProgressResponseExamples.siftEntered,
        civilServiceExperienceDetails = None, edipCompleted = None, overriddenSubmissionDeadline = None
      )
    )

    "be correctly built candidates after phase 3" in {
      val phase3Results = SchemeEvaluationResultWithFailureDetails(SchemeId("Commercial"), SchemeStatus.Red.toString)  ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("DigitalAndTechnology"), SchemeStatus.Red.toString) ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("HumanResources"), SchemeStatus.Green.toString) ::
      Nil
      val cachedUserMetadata = CachedUserWithSchemeData(userDataWithApp.user, userDataWithApp.application,
        selectedSchemes, Schemes.AllSchemes, None, None, phase3Results)

      val page = PostOnlineTestsPage(cachedUserMetadata, Seq.empty, None, hasAnalysisExercise = false, List.empty)

      page.userDataWithSchemes.greenAndAmberSchemesForDisplay mustBe CurrentSchemeStatus(Schemes.HR, SchemeStatus.Green,
        failedAtStage = None) :: Nil

      page.userDataWithSchemes.failedSchemesForDisplay mustBe CurrentSchemeStatus(Schemes.Commercial, SchemeStatus.Red,
        failedAtStage = None) :: CurrentSchemeStatus(Schemes.DaT, SchemeStatus.Red, failedAtStage = None) :: Nil

      page.userDataWithSchemes.withdrawnSchemes mustBe Nil
      page.userDataWithSchemes.hasNumericRequirement mustBe false
      page.userDataWithSchemes.hasFormRequirement mustBe false
      page.fsacStage mustBe PostOnlineTestsStage.OTHER
    }

    "application is in correct stage" in {
      val app = userDataWithApp.application.copy(
        progress = userDataWithApp.application.progress.copy(
        assessmentCentre = userDataWithApp.application.progress.assessmentCentre.copy(allocationUnconfirmed = true)
      ))
      val cachedUserMetadata = CachedUserWithSchemeData(userDataWithApp.user, app, selectedSchemes, Schemes.AllSchemes, None, None, Seq.empty)

      val allocation = CandidateAllocationWithEvent(
        cachedUserMetadata.application.applicationId.toString,
        "",
        AllocationStatuses.UNCONFIRMED,
        EventsExamples.Event1
      )
      val page = PostOnlineTestsPage(cachedUserMetadata, Seq(allocation), None, hasAnalysisExercise = false, List.empty)

      page.fsacStage mustBe PostOnlineTestsStage.ALLOCATED_TO_EVENT
    }

    "indicate all schemes are failed when allSchemesFailed is called and all schemes are red" in {
      val phase3ResultsAllRed = SchemeEvaluationResultWithFailureDetails(SchemeId("Commercial"), SchemeStatus.Red.toString)  ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("DigitalAndTechnology"), SchemeStatus.Red.toString) ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("HumanResources"), SchemeStatus.Red.toString) ::
        Nil
      val cachedUserMetadata = CachedUserWithSchemeData(userDataWithApp.user, userDataWithApp.application,
        selectedSchemes, Schemes.AllSchemes, None, None, phase3ResultsAllRed)

      val page = PostOnlineTestsPage(cachedUserMetadata, Seq.empty, None, hasAnalysisExercise = false, List.empty)

      page.allSchemesFailed mustBe true
    }

    "not indicate all schemes are failed when allSchemesFailed is called and one scheme is green" in {
      val phase3ResultsOneGreen = SchemeEvaluationResultWithFailureDetails(SchemeId("Commercial"), SchemeStatus.Red.toString)  ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("DigitalAndTechnology"), SchemeStatus.Red.toString) ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("HumanResources"), SchemeStatus.Green.toString) ::
        Nil
      val cachedUserMetadata = CachedUserWithSchemeData(userDataWithApp.user, userDataWithApp.application,
        selectedSchemes, Schemes.AllSchemes, None, None, phase3ResultsOneGreen)

      val page = PostOnlineTestsPage(cachedUserMetadata, Seq.empty, None, hasAnalysisExercise = false, List.empty)

      page.allSchemesFailed mustBe false
    }

    "indicate passed when firstResidualPreferencePassed is called and schemes are: Green, Green, Green" in {
      val schemeResults = SchemeEvaluationResultWithFailureDetails(SchemeId("Commercial"), SchemeStatus.Green.toString)  ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("DigitalAndTechnology"), SchemeStatus.Green.toString) ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("HumanResources"), SchemeStatus.Green.toString) ::
        Nil
      val cachedUserMetadata = CachedUserWithSchemeData(userDataWithApp.user, userDataWithApp.application,
        selectedSchemes, Schemes.AllSchemes, None, None, schemeResults)

      val page = PostOnlineTestsPage(cachedUserMetadata, Seq.empty, None, hasAnalysisExercise = false, List.empty)

      page.firstResidualPreferencePassed mustBe true
    }

    "indicate passed when firstResidualPreferencePassed is called and schemes are: Red, Red, Green" in {
      val schemeResults = SchemeEvaluationResultWithFailureDetails(SchemeId("Commercial"), SchemeStatus.Red.toString)  ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("DigitalAndTechnology"), SchemeStatus.Red.toString) ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("HumanResources"), SchemeStatus.Green.toString) ::
        Nil
      val cachedUserMetadata = CachedUserWithSchemeData(userDataWithApp.user, userDataWithApp.application,
        selectedSchemes, Schemes.AllSchemes, None, None, schemeResults)

      val page = PostOnlineTestsPage(cachedUserMetadata, Seq.empty, None, hasAnalysisExercise = false, List.empty)

      page.firstResidualPreferencePassed mustBe true
    }

    "indicate failed when firstResidualPreferencePassed is called and schemes are: Red, Red, Red" in {
      val schemeResults = SchemeEvaluationResultWithFailureDetails(SchemeId("Commercial"), SchemeStatus.Red.toString)  ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("DigitalAndTechnology"), SchemeStatus.Red.toString) ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("HumanResources"), SchemeStatus.Red.toString) ::
        Nil
      val cachedUserMetadata = CachedUserWithSchemeData(userDataWithApp.user, userDataWithApp.application,
        selectedSchemes, Schemes.AllSchemes, None, None, schemeResults)

      val page = PostOnlineTestsPage(cachedUserMetadata, Seq.empty, None, hasAnalysisExercise = false, List.empty)

      page.firstResidualPreferencePassed mustBe false
    }

    "indicate failed when firstResidualPreferencePassed is called and schemes are: Amber, Amber, Amber" in {
      val schemeResults = SchemeEvaluationResultWithFailureDetails(SchemeId("Commercial"), SchemeStatus.Amber.toString)  ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("DigitalAndTechnology"), SchemeStatus.Amber.toString) ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("HumanResources"), SchemeStatus.Amber.toString) ::
        Nil
      val cachedUserMetadata = CachedUserWithSchemeData(userDataWithApp.user, userDataWithApp.application,
        selectedSchemes, Schemes.AllSchemes, None, None, schemeResults)

      val page = PostOnlineTestsPage(cachedUserMetadata, Seq.empty, None, hasAnalysisExercise = false, List.empty)

      page.firstResidualPreferencePassed mustBe false
    }

    "indicate failed when firstResidualPreferencePassed is called and schemes are: Amber, Green, Red" in {
      val schemeResults = SchemeEvaluationResultWithFailureDetails(SchemeId("Commercial"), SchemeStatus.Amber.toString)  ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("DigitalAndTechnology"), SchemeStatus.Green.toString) ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("HumanResources"), SchemeStatus.Red.toString) ::
        Nil
      val cachedUserMetadata = CachedUserWithSchemeData(userDataWithApp.user, userDataWithApp.application,
        selectedSchemes, Schemes.AllSchemes, None, None, schemeResults)

      val page = PostOnlineTestsPage(cachedUserMetadata, Seq.empty, None, hasAnalysisExercise = false, List.empty)

      page.firstResidualPreferencePassed mustBe false
    }

    "indicate final failed when assessment_centre failed and no Green schemes" in {
      val schemeResults = SchemeEvaluationResultWithFailureDetails(SchemeId("Commercial"), SchemeStatus.Red.toString) :: Nil
      val userApp = userDataWithApp.application.copy(
        progress = userDataWithApp.application.progress.copy(
          assessmentCentre = userDataWithApp.application.progress.assessmentCentre.copy(failed = true)
        ))
      val cachedUserMetadata = CachedUserWithSchemeData(userDataWithApp.user, userApp, selectedSchemes, Schemes.AllSchemes,
        None, None, schemeResults)
      val page = PostOnlineTestsPage(cachedUserMetadata, Seq.empty, None, hasAnalysisExercise = true, List.empty)

      page.isFinalFailure mustBe true
    }
  }
}
