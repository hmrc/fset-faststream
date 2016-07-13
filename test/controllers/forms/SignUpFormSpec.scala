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
import forms.SignUpForm
import forms.SignUpForm.Data
import play.api.data.Form
import play.api.i18n.Messages

class SignUpFormSpec extends BaseSpec {

  "the sign up form" should {
    "be valid when all the data are correct" in {
      val (data, signUpForm) = SignupFormGenerator().get
      signUpForm.get must be(data)
    }

    "throw an error if email is invalid" in {
      val (_, signUpForm) = SignupFormGenerator(email = "some_wrong_email").get
      signUpForm.hasErrors must be(true)
      signUpForm.errors.length must be(2)
      signUpForm.errors("email").head.messages must be(Seq("error.email"))
    }

    "throw an error if password is less than 9 characters" in {
      val (_, signUpForm) = SignupFormGenerator(password = "Small1", confirm = "Small1").get
      signUpForm.hasErrors must be(true)
      signUpForm.errors.length must be(1)
      signUpForm.errors("password").head.messages must be(Seq(Messages("error.password")))
    }

    "throw an error if passwords don't match" in {
      val (_, signUpForm) = SignupFormGenerator(confirm = "wrong_password").get
      signUpForm.hasErrors must be(true)
      signUpForm.errors.length must be(1)
      signUpForm.errors("password").head.messages must be(Seq(Messages("error.password.dontmatch")))
    }

    "throw an error if password doesn't have an uppercase letter" in {
      val (_, signUpForm) = SignupFormGenerator(password = "lowercasepassword", confirm = "lowercasepassword").get
      signUpForm.hasErrors must be(true)
      signUpForm.errors.length must be(1)
      signUpForm.errors("password").head.messages must be(Seq(Messages("error.password")))
    }

    "throw an error if password doesn't have an lowercase letter" in {
      val (_, signUpForm) = SignupFormGenerator(password = "ALLCAPITAL", confirm = "ALLCAPITAL").get
      signUpForm.hasErrors must be(true)
      signUpForm.errors.length must be(1)
      signUpForm.errors("password").head.messages must be(Seq(Messages("error.password")))
    }

    "throw an error if password doesn't have a number" in {
      val (_, signUpForm) = SignupFormGenerator(password = "noNumbers", confirm = "noNumbers").get
      signUpForm.hasErrors must be(true)
      signUpForm.errors.length must be(1)
      signUpForm.errors("password").head.messages must be(Seq(Messages("error.password")))
    }

    "throw an error if I haven't click on the I am eligible" in {
      val (_, signUpForm) = SignupFormGenerator(agreeEligibleToApply = false).get
      signUpForm.hasErrors must be(true)
      signUpForm.errors.length must be(1)
      signUpForm.errors("agreeEligibleToApply").head.messages must be(Seq(Messages("agree.eligible")))
    }

    "throw an error if I haven't click on the I agree" in {
      val (_, signUpForm) = SignupFormGenerator(agree = false).get
      signUpForm.hasErrors must be(true)
      signUpForm.errors.length must be(1)
      signUpForm.errors("agree").head.messages must be(Seq(Messages("agree.accept")))
    }

  }

}

/**
 * A class that generates a proper form and data.
 * Usage:
 *
 *    val (data, signupForm) = SignupFormGenerator(email = "some_wrong_email).get
 *
 */
case class SignupFormGenerator(
  firstName: String = "name",
  lastName: String = "last name",
  email: String = "test@email.com",
  confirmEmail: String = "test@email.com",
  password: String = "aA1234567",
  confirm: String = "aA1234567",
  agree: Boolean = true,
  agreeEligibleToApply: Boolean = true
) {

  private val data = Data(firstName, lastName, email, confirmEmail, password, confirm, agree, agreeEligibleToApply)

  private val validFormData = Map(
    "firstName" -> data.firstName,
    "lastName" -> data.lastName,
    "email" -> data.email,
    "email_confirm" -> data.confirmEmail,
    "password" -> data.password,
    "confirmpwd" -> data.confirmpwd,
    "agree" -> data.agree.toString,
    "agreeEligibleToApply" -> data.agreeEligibleToApply.toString
  )

  private def signUpForm = Form(SignUpForm.form.mapping).bind(validFormData)

  def get = (data, signUpForm)
}
