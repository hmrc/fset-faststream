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
import connectors.exchange.SchemeEvaluationResultWithFailureDetails
import ReferenceDataExamples.Schemes._
import models.ApplicationData.ApplicationStatus
import models.SchemeStatus.{ Green, Red, Withdrawn }
import models._
import org.scalatest.{ Matchers, WordSpec }

class CachedUserWithSchemeDataSpec extends WordSpec with Matchers {

  "Successful schemes for display" should {
    "Display sift greens when candidate was sifted and has ambers at assessment centre" in new TestFixture {

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
      sut.withdrawnSchemes should contain theSameElementsAs Seq(GovOps)
    }
  }

  "Successful schemes" should {
    "Display successful schemes from the current scheme status" in new TestFixture {
      sut.successfulSchemes.map(_.scheme) should contain theSameElementsAs Seq(
        Commercial,
        Finance,
        Dip
      )
    }
  }

  "Schemes for sift forms" should {
    "Display schemes from the current scheme status greens that require forms" in new TestFixture {
      sut.schemesForSiftForms should contain theSameElementsAs Seq(ReferenceDataExamples.Schemes.Dip)
    }
  }

  "Number of schemes for display" should {
    "Display counts for display success/failure schemes" in new TestFixture {

    }
  }

  "Has form requirement" should {
    "Return true if any current scheme status successful schemes need form sift" in new TestFixture {
      sut.hasFormRequirement shouldBe true
    }

    "Return false if no current scheme status successful schemes need form sift" in new TestFixture {
      sutWithNoFormRequiredSchemes.hasFormRequirement shouldBe false
    }
  }

  "Has numeric requirement" should {
    "Return true if any current scheme status successful schemes need numeric sift" in new TestFixture {
      sut.hasNumericRequirement shouldBe true
    }

    "Return false if any current scheme status successful schemes need numeric sift" in new TestFixture {
      sutWithNoNumericRequiredSchemes.hasNumericRequirement shouldBe false
    }
  }

  trait TestFixture {
    val userId = UniqueIdentifier(UUID.randomUUID())
    val applicationId = UniqueIdentifier(UUID.randomUUID())

    val progressResponse = Progress(

    )

    val siftApplicationData = ApplicationData(
      applicationId, userId, ApplicationStatus.SIFT, ApplicationRoute.Faststream, progressResponse, None, None, None
    )

    val cachedUser = CachedUser(userId, "Test", "User", None, "a@b.com", isActive = true, "")

    def buildCachedUserWithSchemeData(currentSchemes: Seq[SchemeEvaluationResultWithFailureDetails]): CachedUserWithSchemeData = {
      CachedUserWithSchemeData(
        cachedUser,
        siftApplicationData,
        Seq(
          Commercial,
          Finance,
          GovOps,
          GovEconomics,
          Generalist,
          Dip
        ),
        None,
        None,
        currentSchemes
      )
    }

    val sut: CachedUserWithSchemeData = buildCachedUserWithSchemeData(
      Seq(
        SchemeEvaluationResultWithFailureDetails(
          Commercial.id, Green.toString, None
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
        )
      )
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
