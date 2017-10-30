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

package helpers

import java.util.UUID

import connectors.ReferenceDataExamples
import connectors.ReferenceDataExamples.Schemes.GovOps
import connectors.exchange.{ AssessmentCentre, SchemeEvaluationResult, SchemeEvaluationResultWithFailureDetails }
import ReferenceDataExamples.Schemes._
import models.ApplicationData.ApplicationStatus
import models.SchemeStatus.{ Amber, Green, Red, Withdrawn }
import models._
import org.scalatest.{ Matchers, WordSpec }

class CachedUserWithSchemeDataSpec extends WordSpec with Matchers {

  "Successful schemes for display" should {
    "Display sift greens when candidate was sifted and has ambers at assessment centre" in new TestFixture {
      sutWithFormAndNumericRequiredSchemes.successfulSchemesForDisplay.map(_.scheme.id) should contain theSameElementsAs Seq(
        Commercial.id,
        Finance.id,
        Generalist.id,
        Dip.id,
        GovOps.id,
        GovStats.id
      )
    }

    "Display PHASE3 greens when candidate was not sifted and has ambers at assessment centre" in new TestFixture {

    }

    "Display all greens when candidate is fastpass (has no test results) and " +
      "was not sifted and has ambers at assessment centre" in new TestFixture {

    }

    "Display current scheme status greens when no ambers are present" in new TestFixture {

    }
  }

  "Failed schemes for display" should {
    "Display sift fails when candidate was sifted and has ambers at assessment centre" in new TestFixture {
      sutWithFormAndNumericRequiredSchemes.failedSchemesForDisplay.map(_.scheme.id) should contain theSameElementsAs Seq(
        GovEconomics.id
      )
    }

    "Display PHASE3 fails when candidate was not sifted and has ambers at assessment centre" in new TestFixture {

    }

    "Display no failures when candidate is fastpass (has no test results) and " +
      "was not sifted and has ambers at assessment centre" in new TestFixture {

    }

    "Display current scheme status fails when no ambers are present" in new TestFixture {

    }
  }

  "Withdrawn schemes" should {
    "Display withdrawn schemes from the current scheme status" in new TestFixture {
      sutWithFormAndNumericRequiredSchemes.withdrawnSchemes should contain theSameElementsAs Seq(GovOps)
    }
  }

  "Successful schemes" should {
    "Display successful schemes from the current scheme status" in new TestFixture {
      sutWithFormAndNumericRequiredSchemes.successfulSchemes.map(_.scheme) should contain theSameElementsAs Seq(
        Dip,
        Finance
      )
    }
  }

  "Schemes for sift forms" should {
    "Display schemes from the current scheme status greens that require forms" in new TestFixture {
      sutWithFormAndNumericRequiredSchemes.schemesForSiftForms should contain theSameElementsAs Seq(ReferenceDataExamples.Schemes.Dip)
    }
  }

  "Number of schemes for display" should {
    "Display counts for display success/failure schemes" in new TestFixture {

    }
  }

  "Has form requirement" should {
    "Return true if any current scheme status successful schemes need form sift" in new TestFixture {
      sutWithFormAndNumericRequiredSchemes.hasFormRequirement shouldBe true
    }

    "Return false if no current scheme status successful schemes need form sift" in new TestFixture {
      sutWithNoFormRequiredSchemes.hasFormRequirement shouldBe false
    }
  }

  "Has numeric requirement" should {
    "Return true if any current scheme status successful schemes need numeric sift" in new TestFixture {
      sutWithFormAndNumericRequiredSchemes.hasNumericRequirement shouldBe true
    }

    "Return false if any current scheme status successful schemes need numeric sift" in new TestFixture {
      sutWithNoNumericRequiredSchemes.hasNumericRequirement shouldBe false
    }
  }

  trait TestFixture {
    val userId = UniqueIdentifier(UUID.randomUUID())
    val applicationId = UniqueIdentifier(UUID.randomUUID())

    val inAssessmentCentreWithSiftProgress = Progress(
      assessmentCentre = AssessmentCentre(
        awaitingAllocation = true,
        allocationConfirmed = true,
        scoresAccepted = true
      ),
      siftProgress = SiftProgress(
        siftEntered = true,
        siftReady = true,
        siftCompleted = true
      )
    )

    val siftApplicationData = ApplicationData(
      applicationId, userId, ApplicationStatus.SIFT, ApplicationRoute.Faststream, inAssessmentCentreWithSiftProgress, None, None, None
    )

    val cachedUser = CachedUser(userId, "Test", "User", None, "a@b.com", isActive = true, "")

    def buildCachedUserWithSchemeData(currentSchemes: Seq[SchemeEvaluationResultWithFailureDetails],
      phase3Evaluation: Option[Seq[SchemeEvaluationResult]] = None,
      siftEvaluation: Option[Seq[SchemeEvaluationResult]] = None): CachedUserWithSchemeData = {
      CachedUserWithSchemeData(
        cachedUser,
        siftApplicationData,
        Seq(
          Commercial,
          Finance,
          GovOps,
          GovEconomics,
          Generalist,
          Dip,
          GovStats
        ),
        phase3Evaluation,
        siftEvaluation,
        currentSchemes
      )
    }

    val sutWithFormAndNumericRequiredSchemes: CachedUserWithSchemeData = buildCachedUserWithSchemeData(
      Seq(
        SchemeEvaluationResultWithFailureDetails(
          Commercial.id, Red.toString, Some("assessment centre")
        ),
        SchemeEvaluationResultWithFailureDetails(
          Finance.id, Green.toString, None
        ),
        SchemeEvaluationResultWithFailureDetails(
          Generalist.id, Red.toString, Some("video interview")
        ),
        SchemeEvaluationResultWithFailureDetails(
          Dip.id, Green.toString, None
        ),
        SchemeEvaluationResultWithFailureDetails(
          GovOps.id, Withdrawn.toString, None
        ),
        SchemeEvaluationResultWithFailureDetails(
          GovEconomics.id, Red.toString, Some("sift")
        ),
        SchemeEvaluationResultWithFailureDetails(
          GovStats.id, Amber.toString, None
        )
      ),
      None,
      Some(Seq(
        SchemeEvaluationResult(
          Commercial.id, Green.toString
        ),
        SchemeEvaluationResult(
          Finance.id, Green.toString
        ),
        SchemeEvaluationResult(
          Generalist.id, Green.toString
        ),
        SchemeEvaluationResult(
          Dip.id, Green.toString
        ),
        SchemeEvaluationResult(
          GovOps.id, Green.toString
        ),
        SchemeEvaluationResult(
          GovEconomics.id, Red.toString
        ),
        SchemeEvaluationResult(
          GovStats.id, Green.toString
        )
      ))
    )

    val sutWithNoFormRequiredSchemes: CachedUserWithSchemeData = buildCachedUserWithSchemeData(
      Seq(
        SchemeEvaluationResultWithFailureDetails(
          Commercial.id, Green.toString, None
        ),
        SchemeEvaluationResultWithFailureDetails(
          Finance.id, Green.toString, None
        ),
        SchemeEvaluationResultWithFailureDetails(
          Generalist.id, Red.toString, Some("video interview")
        )
      )
    )

    val sutWithNoNumericRequiredSchemes: CachedUserWithSchemeData = buildCachedUserWithSchemeData(
      Seq(
        SchemeEvaluationResultWithFailureDetails(
          Generalist.id, Red.toString, Some("video interview")
        ),
        SchemeEvaluationResultWithFailureDetails(
          Dip.id, Green.toString, None
        ),
        SchemeEvaluationResultWithFailureDetails(
          GovEconomics.id, Green.toString, None
        )

      )
    )
  }
}
