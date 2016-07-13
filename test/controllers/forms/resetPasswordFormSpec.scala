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

package controllers.forms

import controllers.BaseSpec
import forms.ResetPasswordForm

class resetPasswordFormSpec extends BaseSpec {

  "the validate method" should {
    "validate an email" in {
      ResetPasswordForm.validateEmail("test@test.com") must be(true)
    }

    "return false on invalid email" in {
      ResetPasswordForm.validateEmail("not_an_email") must be(false)
    }
  }

}
