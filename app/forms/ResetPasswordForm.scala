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

import forms.Mappings._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.{ Constraints, Valid }

object ResetPasswordForm {

  def validateEmail(e: String) = Constraints.emailAddress(e) match {
    case Valid => true
    case _ => false
  }

  val form = Form(
    mapping(
      "email" -> email,
      "code" -> (nonEmptyTrimmedText("passwordreset.required", 7, "passwordreset.wrong-format") verifying
        ("activation.wrong-format", value => value.matches("[\\w]{7}"))),
      "password" -> of(SignUpForm.passwordFormatter),
      "confirmpwd" -> nonEmptyTrimmedText("error.confirmpwd", SignUpForm.passwordMaxLength)
    )(Data.apply)(Data.unapply)
  )

  val resendCodeForm = Form(
    mapping(
      "email" -> email,
      "resend" -> optionalTrimmedText(4) // Some("true") or None
    )(ResendCodeData.apply)(ResendCodeData.unapply)
  )

  case class Data(
    email: String,
    code: String,
    password: String,
    confirmpwd: String
  )

  case class ResendCodeData(email: String, resend: Option[String])
}
