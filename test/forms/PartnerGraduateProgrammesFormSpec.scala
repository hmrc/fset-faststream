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

package forms

import forms.PartnerGraduateProgrammesForm.Data
import forms.PartnerGraduateProgrammesFormExamples._
import org.scalatestplus.play.PlaySpec
import play.api.data.Form

class PartnerGraduateProgrammesFormSpec extends PlaySpec {

  "the partner graduate programmes form" should {
    "be valid when the user selects no in the interested" in new Fixture {
      val (data, form) = NotInterested
      form.get must be(data)
    }

    "be valid when the user selects yes in interested and selects several programmes" in new Fixture {
      val (data, form) = InterestedNotAll
      form.get must be(data)
    }

    "be invalid when user is interested but no programme is selected" in new Fixture {
      assertFormError(Seq(
        "error.partnerGraduateProgrammes.chooseone"
      ), PartnerGraduateProgrammesFormExamples.InterestedButNoProgrammeSelectedMap)
    }
  }

  trait Fixture {

    val NotInterested = (NotInterestedForm, PartnerGraduateProgrammesForm.form.fill(NotInterestedForm))

    val InterestedNotAll = (InterestedNotAllForm, PartnerGraduateProgrammesForm.form.fill(InterestedNotAllForm))

    val InterestedButNotProgrammesSelected = (InterestedButNoProgrammeSelectedForm,
      PartnerGraduateProgrammesForm.form.fill(InterestedButNoProgrammeSelectedForm))

    def assertFormError(expectedError: Seq[String], invalidFormValues: Map[String, String]) = {
      val invalidForm: Form[Data] = PartnerGraduateProgrammesForm.form.bind(invalidFormValues)
      invalidForm.hasErrors mustBe true
      invalidForm.errors.map(_.message) mustBe expectedError
    }
  }
}
