/*
 * Copyright 2019 HM Revenue & Customs
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

class SignInFormSpec extends UnitWithAppSpec {

  import SignInForm.{ form => signInForm }

  "Sign In form" should {
    "be valid for non empty email and non empty password" in {
      val form = signInForm.bind(Map("signIn" -> "ABCDEFG", "signInPassword" -> "123456"))
      form.hasErrors must be(false)
      form.hasGlobalErrors must be(false)
    }

    "be invalid for empty email" in {
      val form = signInForm.bind(Map("signIn" -> "", "signInPassword" -> "123456"))
      form.hasErrors must be(true)
      form.hasGlobalErrors must be(false)
    }

    "be invalid for empty password" in {
      val form = signInForm.bind(Map("signIn" -> "ABCDEFG", "signInPassword" -> ""))
      form.hasErrors must be(true)
      form.hasGlobalErrors must be(false)
    }

    "be invalid for too long email" in {
      val form = signInForm.bind(Map("signIn" -> "ABCDEFG", "signInPassword" -> ("012345678901234567890123456789012345678901234567890123456789" +
        "01234567890123456789012345678901234567890123456789012345678901234567890123456789")))
      form.hasErrors must be(true)
      form.hasGlobalErrors must be(false)
    }

    "be invalid for too long password" in {
      val form = signInForm.bind(Map("signIn" -> ("012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789" +
        "0123456789012345678901234567890123456789"), "signInPassword" -> "123456"))
      form.hasErrors must be(true)
      form.hasGlobalErrors must be(false)
    }
  }
}
