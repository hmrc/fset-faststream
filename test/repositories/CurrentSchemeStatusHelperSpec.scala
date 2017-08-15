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

package repositories

import model.EvaluationResults.{ Green, Red }
import model.{ SchemeId, SelectedSchemesExamples }
import model.persisted.SchemeEvaluationResult
import testkit.UnitSpec

class CurrentSchemeStatusHelperSpec extends UnitSpec {

  val helper = new CurrentSchemeStatusHelper {}

  "Current scheme status helper" must {

    "update status when one does not exist" in {
      val currentStatus = Nil
      val newStatus = SchemeEvaluationResult(SchemeId("Commercial"), Green.toString) ::
        SchemeEvaluationResult(SchemeId("International"), Red.toString) :: Nil

      helper.calculateCurrentSchemeStatus(currentStatus, newStatus) mustBe newStatus
    }

    "update existing statuses" in {
      val currentStatus = SchemeEvaluationResult(SchemeId("Commercial"), Green.toString) ::
        SchemeEvaluationResult(SchemeId("International"), Green.toString) :: Nil

      val newStatus = SchemeEvaluationResult(SchemeId("Commercial"), Red.toString) ::
       SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), Red.toString) :: Nil

      helper.calculateCurrentSchemeStatus(currentStatus, newStatus) mustBe
       SchemeEvaluationResult(SchemeId("Commercial"), Red.toString) ::
       SchemeEvaluationResult(SchemeId("International"), Green.toString) ::
       SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), Red.toString) :: Nil
    }

  }
}
