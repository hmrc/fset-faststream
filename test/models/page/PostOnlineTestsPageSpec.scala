/*
 * Copyright 2021 HM Revenue & Customs
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
import connectors.exchange.candidateevents.CandidateAllocationWithEvent
import connectors.exchange.referencedata.SchemeId
import connectors.exchange.{EventsExamples, SchemeEvaluationResultWithFailureDetails, SelectedSchemes}
import connectors.exchange.{ EventsExamples, SchemeEvaluationResultWithFailureDetails, SelectedSchemes }
import testkit.UnitSpec
import helpers.{CachedUserWithSchemeData, CurrentSchemeStatus}
import models.ApplicationData.ApplicationStatus
import models._
import models.events.AllocationStatuses
import models.page.DashboardPage.Flags.{ProgressActive, ProgressInactiveDisabled}
import models.page.DashboardPage.Flags.{ ProgressActive, ProgressInactiveDisabled }

class PostOnlineTestsPageSpec extends UnitSpec {
  val FsacGuideUrl = "localhost/fsac"

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
        SchemeEvaluationResultWithFailureDetails(SchemeId("DigitalDataTechnologyAndCyber"), SchemeStatus.Red.toString) ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("HumanResources"), SchemeStatus.Green.toString) ::
      Nil
      val cachedUserMetadata = CachedUserWithSchemeData(userDataWithApp.user, userDataWithApp.application,
        selectedSchemes, Schemes.AllSchemes, None, None, phase3Results)

      val page = PostOnlineTestsPage(cachedUserMetadata, allocationsWithEvent = Seq.empty, additionalQuestionsStatus = None,
        hasAnalysisExercise = false, schemes = List.empty, siftState = None, phase1DataOpt = None, phase2DataOpt = None, phase3DataOpt = None,
        FsacGuideUrl)

      page.userDataWithSchemes.greenAndAmberSchemesForDisplay mustBe CurrentSchemeStatus(Schemes.HR, SchemeStatus.Green,
        failedAtStage = None) :: Nil

      page.userDataWithSchemes.failedSchemesForDisplay mustBe CurrentSchemeStatus(Schemes.Commercial, SchemeStatus.Red,
        failedAtStage = None) :: CurrentSchemeStatus(Schemes.DDTaC, SchemeStatus.Red, failedAtStage = None) :: Nil

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
      val page = PostOnlineTestsPage(cachedUserMetadata, allocationsWithEvent = Seq(allocation), additionalQuestionsStatus = None,
        hasAnalysisExercise = false, schemes = List.empty, siftState = None, phase1DataOpt = None, phase2DataOpt = None, phase3DataOpt = None,
        FsacGuideUrl)

      page.fsacStage mustBe PostOnlineTestsStage.ALLOCATED_TO_EVENT
    }

    "indicate all schemes are failed when allSchemesFailed is called and all schemes are red" in {
      val phase3ResultsAllRed = SchemeEvaluationResultWithFailureDetails(SchemeId("Commercial"), SchemeStatus.Red.toString)  ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("DigitalDataTechnologyAndCyber"), SchemeStatus.Red.toString) ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("HumanResources"), SchemeStatus.Red.toString) ::
        Nil
      val cachedUserMetadata = CachedUserWithSchemeData(userDataWithApp.user, userDataWithApp.application,
        selectedSchemes, Schemes.AllSchemes, None, None, phase3ResultsAllRed)

      val page = PostOnlineTestsPage(cachedUserMetadata, allocationsWithEvent = Seq.empty, additionalQuestionsStatus = None,
        hasAnalysisExercise = false, schemes = List.empty, siftState = None, phase1DataOpt = None, phase2DataOpt = None, phase3DataOpt = None,
      FsacGuideUrl)

      page.allSchemesFailed mustBe true
    }

    "not indicate all schemes are failed when allSchemesFailed is called and one scheme is green" in {
      val phase3ResultsOneGreen = SchemeEvaluationResultWithFailureDetails(SchemeId("Commercial"), SchemeStatus.Red.toString)  ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("DigitalDataTechnologyAndCyber"), SchemeStatus.Red.toString) ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("HumanResources"), SchemeStatus.Green.toString) ::
        Nil
      val cachedUserMetadata = CachedUserWithSchemeData(userDataWithApp.user, userDataWithApp.application,
        selectedSchemes, Schemes.AllSchemes, None, None, phase3ResultsOneGreen)

      val page = PostOnlineTestsPage(cachedUserMetadata, allocationsWithEvent = Seq.empty, additionalQuestionsStatus = None,
        hasAnalysisExercise = false, schemes = List.empty, siftState = None, phase1DataOpt = None, phase2DataOpt = None, phase3DataOpt = None,
      FsacGuideUrl)

      page.allSchemesFailed mustBe false
    }

    "indicate passed when firstResidualPreferencePassed is called and schemes are: Green, Green, Green" in {
      val schemeResults = SchemeEvaluationResultWithFailureDetails(SchemeId("Commercial"), SchemeStatus.Green.toString)  ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("DigitalDataTechnologyAndCyber"), SchemeStatus.Green.toString) ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("HumanResources"), SchemeStatus.Green.toString) ::
        Nil
      val cachedUserMetadata = CachedUserWithSchemeData(userDataWithApp.user, userDataWithApp.application,
        selectedSchemes, Schemes.AllSchemes, None, None, schemeResults)

      val page = PostOnlineTestsPage(cachedUserMetadata, allocationsWithEvent = Seq.empty, additionalQuestionsStatus = None,
        hasAnalysisExercise = false, schemes = List.empty, siftState = None, phase1DataOpt = None, phase2DataOpt = None, phase3DataOpt = None,
        FsacGuideUrl)

      page.firstResidualPreferencePassed mustBe true
    }

    "indicate passed when firstResidualPreferencePassed is called and schemes are: Red, Red, Green" in {
      val schemeResults = SchemeEvaluationResultWithFailureDetails(SchemeId("Commercial"), SchemeStatus.Red.toString)  ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("DigitalDataTechnologyAndCyber"), SchemeStatus.Red.toString) ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("HumanResources"), SchemeStatus.Green.toString) ::
        Nil
      val cachedUserMetadata = CachedUserWithSchemeData(userDataWithApp.user, userDataWithApp.application,
        selectedSchemes, Schemes.AllSchemes, None, None, schemeResults)

      val page = PostOnlineTestsPage(cachedUserMetadata, allocationsWithEvent = Seq.empty, additionalQuestionsStatus = None,
        hasAnalysisExercise = false, schemes = List.empty, siftState = None, phase1DataOpt = None, phase2DataOpt = None, phase3DataOpt = None,
        FsacGuideUrl)

      page.firstResidualPreferencePassed mustBe true
    }

    "indicate failed when firstResidualPreferencePassed is called and schemes are: Red, Red, Red" in {
      val schemeResults = SchemeEvaluationResultWithFailureDetails(SchemeId("Commercial"), SchemeStatus.Red.toString)  ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("DigitalDataTechnologyAndCyber"), SchemeStatus.Red.toString) ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("HumanResources"), SchemeStatus.Red.toString) ::
        Nil
      val cachedUserMetadata = CachedUserWithSchemeData(userDataWithApp.user, userDataWithApp.application,
        selectedSchemes, Schemes.AllSchemes, None, None, schemeResults)

      val page = PostOnlineTestsPage(cachedUserMetadata, allocationsWithEvent = Seq.empty, additionalQuestionsStatus = None,
        hasAnalysisExercise = false, schemes = List.empty, siftState = None, phase1DataOpt = None, phase2DataOpt = None, phase3DataOpt = None,
        FsacGuideUrl)

      page.firstResidualPreferencePassed mustBe false
    }

    "indicate failed when firstResidualPreferencePassed is called and schemes are: Amber, Amber, Amber" in {
      val schemeResults = SchemeEvaluationResultWithFailureDetails(SchemeId("Commercial"), SchemeStatus.Amber.toString)  ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("DigitalDataTechnologyAndCyber"), SchemeStatus.Amber.toString) ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("HumanResources"), SchemeStatus.Amber.toString) ::
        Nil
      val cachedUserMetadata = CachedUserWithSchemeData(userDataWithApp.user, userDataWithApp.application,
        selectedSchemes, Schemes.AllSchemes, None, None, schemeResults)

      val page = PostOnlineTestsPage(cachedUserMetadata, allocationsWithEvent = Seq.empty, additionalQuestionsStatus = None,
        hasAnalysisExercise = false, schemes = List.empty, siftState = None, phase1DataOpt = None, phase2DataOpt = None, phase3DataOpt = None,
        FsacGuideUrl)

      page.firstResidualPreferencePassed mustBe false
    }

    "indicate failed when firstResidualPreferencePassed is called and schemes are: Amber, Green, Red" in {
      val schemeResults = SchemeEvaluationResultWithFailureDetails(SchemeId("Commercial"), SchemeStatus.Amber.toString)  ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("DigitalDataTechnologyAndCyber"), SchemeStatus.Green.toString) ::
        SchemeEvaluationResultWithFailureDetails(SchemeId("HumanResources"), SchemeStatus.Red.toString) ::
        Nil
      val cachedUserMetadata = CachedUserWithSchemeData(userDataWithApp.user, userDataWithApp.application,
        selectedSchemes, Schemes.AllSchemes, None, None, schemeResults)

      val page = PostOnlineTestsPage(cachedUserMetadata, allocationsWithEvent = Seq.empty, additionalQuestionsStatus = None,
        hasAnalysisExercise = false, schemes = List.empty, siftState = None, phase1DataOpt = None, phase2DataOpt = None, phase3DataOpt = None,
        FsacGuideUrl)

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
      val page = PostOnlineTestsPage(cachedUserMetadata, allocationsWithEvent = Seq.empty, additionalQuestionsStatus = None,
        hasAnalysisExercise = true, schemes = List.empty, siftState = None, phase1DataOpt = None, phase2DataOpt = None, phase3DataOpt = None,
        FsacGuideUrl)

      page.isFinalFailure mustBe true
    }

    "enable the steps according to the sift visibility rules" in {
      val schemeResults = SchemeEvaluationResultWithFailureDetails(SchemeId("Commercial"), SchemeStatus.Red.toString) :: Nil

      val siftUserDataWithApp = userDataWithApp.copy(
        application = ApplicationData(randUUID, randUUID,
          ApplicationStatus.SIFT, ApplicationRoute.Faststream, ProgressResponseExamples.siftExpired,
          civilServiceExperienceDetails = None, edipCompleted = None, overriddenSubmissionDeadline = None
        )
      )

      val cachedUserMetadata = CachedUserWithSchemeData(userDataWithApp.user, siftUserDataWithApp.application, selectedSchemes,
        Schemes.AllSchemes, None, None, schemeResults)

      val page = PostOnlineTestsPage(cachedUserMetadata, allocationsWithEvent = Seq.empty, additionalQuestionsStatus = None,
        hasAnalysisExercise = false, schemes = List.empty, siftState = None, phase1DataOpt = None, phase2DataOpt = None, phase3DataOpt = None,
        FsacGuideUrl)

      page.secondStepVisibility mustBe ProgressInactiveDisabled
      page.fourthStepVisibility mustBe ProgressInactiveDisabled
    }

    "disable the steps according to the sift visibility rules" in {
      val schemeResults = SchemeEvaluationResultWithFailureDetails(SchemeId("Commercial"), SchemeStatus.Red.toString) :: Nil

      val siftUserDataWithApp = userDataWithApp.copy(
        application = ApplicationData(randUUID, randUUID,
          ApplicationStatus.SIFT, ApplicationRoute.Faststream, ProgressResponseExamples.siftEntered,
          civilServiceExperienceDetails = None, edipCompleted = None, overriddenSubmissionDeadline = None
        )
      )

      val cachedUserMetadata = CachedUserWithSchemeData(userDataWithApp.user, siftUserDataWithApp.application, selectedSchemes,
        Schemes.AllSchemes, None, None, schemeResults)

      val page = PostOnlineTestsPage(cachedUserMetadata, allocationsWithEvent = Seq.empty, additionalQuestionsStatus = None,
        hasAnalysisExercise = false, schemes = List.empty, siftState = None, phase1DataOpt = None, phase2DataOpt = None, phase3DataOpt = None,
        FsacGuideUrl)

      page.secondStepVisibility mustBe ProgressActive
      page.fourthStepVisibility mustBe ProgressActive
    }
  }
}

