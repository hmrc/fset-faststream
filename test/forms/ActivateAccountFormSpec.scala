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

package forms

import controllers.UnitSpec
import testkit.UnitWithAppSpec

class ActivateAccountFormSpec extends UnitWithAppSpec {

  import ActivateAccountForm.{ form => activateAccountForm }

  "Activate Account form" should {
    "be valid for token with 7 characters" in {
      val form = activateAccountForm.bind(Map("activation" -> "ABCDEFG"))
      form.hasErrors must be(false)
      form.hasGlobalErrors must be(false)
    }

    "be invalid for too short token" in {
      val form = activateAccountForm.bind(Map("activation" -> "A"))
      form.hasErrors must be(true)
      form.hasGlobalErrors must be(false)
      form.errors.map(_.message) must be(List("activation.wrong-format"))
    }

    "be invalid for too too long token" in {
      val form = activateAccountForm.bind(Map("activation" -> "ABCDEFGH"))
      form.hasErrors must be(true)
      form.hasGlobalErrors must be(false)
      form.errors.map(_.message) must be(List("error.maxLength", "activation.wrong-format"))
    }
  }
}
