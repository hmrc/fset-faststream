/*
 * Copyright 2023 HM Revenue & Customs
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

package repositories

import model.EvaluationResults.{Green, Red, Withdrawn}
import model.Schemes
import model.persisted.SchemeEvaluationResult
import testkit.UnitSpec

class CurrentSchemeStatusHelperSpec extends UnitSpec with Schemes {

  val helper = new CurrentSchemeStatusHelper {}

  "Current scheme status helper" must {
    "update status when one does not exist" in {
      val currentStatus = Nil
      val newStatus = SchemeEvaluationResult(Commercial, Green.toString) ::
        SchemeEvaluationResult(GovernmentSocialResearchService, Red.toString) :: Nil

      helper.calculateCurrentSchemeStatus(currentStatus, newStatus) mustBe newStatus
    }

    "update existing statuses" in {
      val currentStatus = SchemeEvaluationResult(Commercial, Green.toString) ::
        SchemeEvaluationResult(GovernmentSocialResearchService, Green.toString) :: Nil

      val newStatus = SchemeEvaluationResult(Commercial, Red.toString) ::
       SchemeEvaluationResult(Digital, Red.toString) :: Nil

      helper.calculateCurrentSchemeStatus(currentStatus, newStatus) mustBe
       SchemeEvaluationResult(Commercial, Red.toString) ::
       SchemeEvaluationResult(GovernmentSocialResearchService, Green.toString) ::
       SchemeEvaluationResult(Digital, Red.toString) :: Nil
    }

    "not return a first residual preference when faststream schemes are red or withdrawn, sdip is green and we are a " +
      "sdip faststream candidate (we ignore the sdip scheme)" in {
      helper.firstResidualPreference(Seq(
        SchemeEvaluationResult(Digital, Red.toString),
        SchemeEvaluationResult(Commercial, Withdrawn.toString),
        SchemeEvaluationResult(Sdip, Green.toString)), ignoreSdip = true
      ) mustBe None
    }

    "return a first residual preference when a faststream candidate has at least one green scheme" in {
      helper.firstResidualPreference(Seq(
        SchemeEvaluationResult(Digital, Red.toString),
        SchemeEvaluationResult(Commercial, Withdrawn.toString),
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString))
      ) mustBe Some(SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString))
    }

    "return the correct first residual preference when a faststream candidate has at least one green scheme" in {
      helper.firstResidualPreference(Seq(
        SchemeEvaluationResult(Digital, Red.toString),
        SchemeEvaluationResult(Commercial, Green.toString),
        SchemeEvaluationResult(DiplomaticAndDevelopment, Green.toString))
      ) mustBe Some(SchemeEvaluationResult(Commercial, Green.toString))
    }
  }
}
