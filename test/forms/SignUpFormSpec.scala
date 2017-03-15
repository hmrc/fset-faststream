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

package forms

import forms.SignUpForm.{ Data, _ }
import models.ApplicationRoute
import play.api.data.Form
import testkit.UnitWithAppSpec
import play.api.i18n.Messages.Implicits._
import play.api.i18n.Messages

class SignUpFormSpec extends UnitWithAppSpec {

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

    "throw an error if I haven't clicked on the I am eligible for Fast Stream" in {
      val (_, signUpForm) = SignupFormGenerator(faststreamEligible = false).get
      signUpForm.hasErrors must be(true)
      signUpForm.errors.length must be(1)
      signUpForm.errors("faststreamEligible").head.messages must be(Seq(Messages("agree.faststreamEligible")))
    }

    "throw an error if I haven't clicked on the I am eligible for EDIP" in {
      val (_, signUpForm) = SignupFormGenerator(applicationRoute = Some(ApplicationRoute.Edip),
        faststreamEligible = false, edipEligible = false).get
      signUpForm.hasErrors must be(true)
      signUpForm.errors.length must be(1)
      signUpForm.errors("edipEligible").head.messages must be(Seq(Messages("agree.edipEligible")))
    }

    "throw an error if I haven't clicked on the I am eligible for SDIP" in {
      val (_, signUpForm) = SignupFormGenerator(applicationRoute = Some(ApplicationRoute.Sdip),
                                                faststreamEligible = false, sdipEligible = false).get
      signUpForm.hasErrors must be(true)
      signUpForm.errors.length must be(2)
      signUpForm.errors("sdipEligible").head.messages must be(Seq(Messages("agree.sdipEligible")))
      signUpForm.errors("hasAppliedToFaststream").head.messages must be(Seq(Messages("agree.hasAppliedToFaststream")))
    }

    "throw an error if I haven't clicked on the I am eligible for SDIP but have clicked that I've applied to Fast Stream this year" in {
      val (_, signUpForm) = SignupFormGenerator(applicationRoute = Some(ApplicationRoute.Sdip),
                                                faststreamEligible = false, sdipEligible = false, hasAppliedToFaststream = Some(true)).get
      signUpForm.hasErrors must be(true)
      signUpForm.errors.length must be(2)
      signUpForm.errors("sdipEligible").head.messages must be(Seq(Messages("agree.sdipEligible")))
      signUpForm.errors("hasAppliedToFaststream").head.messages must be(Seq(Messages("error.hasAppliedToFaststream")))
    }

    "throw an error if I haven't clicked on the I am eligible for SDIP and haven't clicked that I've applied to Fast Stream this year" in {
      val (_, signUpForm) = SignupFormGenerator(applicationRoute = Some(ApplicationRoute.Sdip),
                                                faststreamEligible = false, sdipEligible = false, hasAppliedToFaststream = Some(false)).get
      signUpForm.hasErrors must be(true)
      signUpForm.errors.length must be(1)
      signUpForm.errors("sdipEligible").head.messages must be(Seq(Messages("agree.sdipEligible")))
      signUpForm.errors("hasAppliedToFaststream") mustBe Nil
    }

    "throw an error if I haven't clicked on the I agree" in {
      val (_, signUpForm) = SignupFormGenerator(agree = false).get
      signUpForm.hasErrors must be(true)
      signUpForm.errors.length must be(1)
      signUpForm.errors("agree").head.messages must be(Seq(Messages("agree.accept")))
    }

    "throw and error if I haven't selected a route" in {
      val (_, signUpForm) = SignupFormGenerator(applicationRoute = None).get
      signUpForm.hasErrors must be(true)
      signUpForm.errors.length must be(1)
      signUpForm.errors("applicationRoute").head.messages must be(Seq(Messages("error.appRoute")))
    }

  }

  "sanitize" should {
    "clean up campaignOther field when campaignReferrer is not selected as Other" in {
      val (_, signUpForm) = SignupFormGenerator(campaignReferrer = Some("Friend from Fast Stream"),
        campaignOther = Some("Heard about campaign from other sources")).get
      signUpForm.data.sanitize mustNot contain key "campaignOther"
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
  campaignReferrer: Option[String] = Some("Recruitment website"),
  campaignOther: Option[String] = None,
  agree: Boolean = true,
  applicationRoute: Option[ApplicationRoute.ApplicationRoute] = Some(ApplicationRoute.Faststream),
  faststreamEligible: Boolean = true,
  edipEligible: Boolean = false,
  sdipEligible: Boolean = false,
  hasAppliedToFaststream: Option[Boolean] = None
) {

  private val data = Data(
    firstName,
    lastName,
    email,
    confirmEmail,
    password,
    confirm,
    campaignReferrer,
    campaignOther,
    applicationRoute.map(_.toString).getOrElse(""),
    agree,
    faststreamEligible,
    edipEligible,
    sdipEligible,
    hasAppliedToFaststream
  )

  private val validFormData = Map(
    "firstName" -> data.firstName,
    "lastName" -> data.lastName,
    "email" -> data.email,
    "email_confirm" -> data.confirmEmail,
    "password" -> data.password,
    "confirmpwd" -> data.confirmpwd,
    "campaignReferrer" -> data.campaignReferrer.get,
    "campaignOther" -> data.campaignOther.getOrElse(""),
    "agree" -> data.agree.toString,
    "applicationRoute" -> applicationRoute.map(_.toString).getOrElse(""),
    "faststreamEligible" -> data.faststreamEligible.toString,
    "edipEligible" -> data.edipEligible.toString,
    "sdipEligible" -> data.sdipEligible.toString
  ) ++ data.hasAppliedToFaststream.map( x => "hasAppliedToFaststream" -> x.toString )

  private def signUpForm = Form(SignUpForm.form.mapping).bind(validFormData)

  def get = (data, signUpForm)
}
