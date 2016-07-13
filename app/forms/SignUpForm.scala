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
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.validation.Constraints
import play.api.data.{ Form, FormError }
import play.api.i18n.Messages

object SignUpForm {
  val passwordField = "password"
  val confirmPasswordField = "confirmpwd"
  val fakePasswordField = "fake-password" // Used only in view (to prevent auto-fill)

  val passwordMinLength = 9
  val passwordMaxLength = 128

  val passwordFormatter = new Formatter[String] {
    // scalastyle:off cyclomatic.complexity
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], String] = {
      val passwd = data.get("password").get.trim
      val confirm = data.get("confirmpwd").get.trim

      def formError(id: String) = Left(List(FormError("password", Messages(id))))

      (passwd, confirm) match {
        case (password, _) if password.length == 0 => formError("error.password.empty")
        case (password, _) if password.length < passwordMinLength => formError("error.password")
        case (password, _) if password.length > passwordMaxLength => formError("error.password")
        case (password, _) if "[A-Z]".r.findFirstIn(password).isEmpty => formError("error.password")
        case (password, _) if "[a-z]".r.findFirstIn(password).isEmpty => formError("error.password")
        case (password, _) if "[0-9]".r.findFirstIn(password).isEmpty => formError("error.password")
        case (password, confipw) if password != confipw => formError("error.password.dontmatch")
        case _ => Right(passwd)
      }
    }
    // scalastyle:on cyclomatic.complexity

    override def unbind(key: String, value: String): Map[String, String] = Map(key -> value)
  }

  val emailConfirm = new Formatter[String] {

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], String] = {
      val email: Option[String] = data.get("email")
      val confirm: Option[String] = data.get("email_confirm")

      (email, confirm) match {
        case (Some(e), Some(v)) if e.toLowerCase == v.toLowerCase => Right(e)
        case _ => Left(List(
          FormError("email_confirm", Messages("error.emailconfirm.notmatch"))
        ))
      }

    }

    override def unbind(key: String, value: String): Map[String, String] = Map(key -> value)
  }

  val form = Form(
    mapping(
      "firstName" -> nonEmptyTrimmedText("error.firstName", 256),
      "lastName" -> nonEmptyTrimmedText("error.lastName", 256),
      "email" -> (email verifying Constraints.maxLength(128)),
      "email_confirm" -> of(emailConfirm),
      passwordField -> of(passwordFormatter),
      confirmPasswordField -> nonEmptyTrimmedText("error.confirmpwd", passwordMaxLength),
      "agree" -> checked(Messages("agree.accept")),
      "agreeEligibleToApply" -> checked(Messages("agree.eligible"))
    )(Data.apply)(Data.unapply)
  )

  case class Data(
    firstName: String,
    lastName: String,
    email: String,
    confirmEmail: String,
    password: String,
    confirmpwd: String,
    agree: Boolean,
    agreeEligibleToApply: Boolean
  )
}
