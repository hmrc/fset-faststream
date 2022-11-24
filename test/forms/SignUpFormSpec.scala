/*
 * Copyright 2022 HM Revenue & Customs
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

import forms.SignUpForm.{Data, _}
import models.ApplicationRoute
import play.api.data.Form
import play.api.i18n.Messages

class SignUpFormSpec extends BaseFormSpec {

  "the sign up form" should {
    "be valid when all the data is correct" in {
      val (data, signUpForm) = SignupFormGenerator().get
      // No need to check for zero errors because this method will throw an exception if form errors are present
      signUpForm.get mustBe data
    }

    "throw an error if email is invalid" in {
      val (_, signUpForm) = SignupFormGenerator(email = "some_wrong_email").get
      signUpForm.hasErrors mustBe true
      signUpForm.errors.length mustBe 2
      signUpForm.errors("email").head.messages mustBe Seq("error.email")
    }

    "throw an error if password is less than 9 characters" in {
      val (_, signUpForm) = SignupFormGenerator(password = "Small1", confirmpwd = "Small1").get
      signUpForm.hasErrors mustBe true
      signUpForm.errors.length mustBe 1
      signUpForm.errors("password").head.messages mustBe Seq(Messages("error.password"))
    }

    "throw an error if passwords don't match" in {
      val (_, signUpForm) = SignupFormGenerator(confirmpwd = "wrong_password").get
      signUpForm.hasErrors mustBe true
      signUpForm.errors.length mustBe 1
      signUpForm.errors("password").head.messages mustBe Seq(Messages("error.password.dontmatch"))
    }

    "throw an error if password doesn't have an uppercase letter" in {
      val (_, signUpForm) = SignupFormGenerator(password = "lowercasepassword", confirmpwd = "lowercasepassword").get
      signUpForm.hasErrors mustBe true
      signUpForm.errors.length mustBe 1
      signUpForm.errors("password").head.messages mustBe Seq(Messages("error.password"))
    }

    "throw an error if password doesn't have an lowercase letter" in {
      val (_, signUpForm) = SignupFormGenerator(password = "ALLCAPITAL", confirmpwd = "ALLCAPITAL").get
      signUpForm.hasErrors mustBe true
      signUpForm.errors.length mustBe 1
      signUpForm.errors("password").head.messages mustBe Seq(Messages("error.password"))
    }

    "throw an error if password doesn't have a number" in {
      val (_, signUpForm) = SignupFormGenerator(password = "noNumbers", confirmpwd = "noNumbers").get
      signUpForm.hasErrors must be(true)
      signUpForm.errors.length must be(1)
      signUpForm.errors("password").head.messages mustBe Seq(Messages("error.password"))
    }

    "throw an error if I haven't clicked on the I am eligible for Fast Stream" in {
      val (_, signUpForm) = SignupFormGenerator(faststreamEligible = false).get
      signUpForm.hasErrors mustBe true
      signUpForm.errors.length mustBe 2
      signUpForm.errors("faststreamEligible").head.messages mustBe Seq(Messages("agree.faststreamEligible"))
      signUpForm.errors("sdipFastStreamConsider").head.messages mustBe Seq(Messages("sdipFastStream.consider"))
    }

    "throw an error if I have clicked on the I am eligible for Fast Stream but not confirmed if I want to be considered for SDIP" in {
      val (_, signupForm) = SignupFormGenerator(faststreamEligible = true, sdipFastStreamConsider = None).get
      signupForm.hasErrors mustBe true
      signupForm.errors.length mustBe 1
      signupForm.errors("sdipFastStreamConsider").head.messages mustBe Seq(Messages("sdipFastStream.consider"))
    }

    "throw errors if I have clicked on I am eligible to apply for Fast Stream and confirmed I want to be considered for SDIP," +
      " but not confirmed I am eligible for SDIP or I want to be considered for the diversity strand of SDIP" in {
      val (_, signupForm) = SignupFormGenerator(
        // No answers provided for sdipFastStreamEligible or sdipFastStreamDiversity
        faststreamEligible = true, sdipFastStreamConsider = Some(true), sdipFastStreamEligible = None, sdipFastStreamDiversity = None
      ).get
      signupForm.hasErrors mustBe true
      signupForm.errors.length mustBe 2
      // Note the sdip error message is also used for sdip faststream
      signupForm.errors("sdipFastStreamEligible").head.messages mustBe Seq(Messages("agree.sdipEligible"))
      signupForm.errors("sdipFastStreamDiversity").head.messages mustBe Seq(Messages("agree.sdipDiversity"))
    }

    "pass validation if I have clicked on I am eligible to apply for Fast Stream and confirmed I want to be considered for SDIP," +
      " and have confirmed I am eligible for SDIP and I want to be considered for the diversity strand of SDIP" in {
      val (_, signupForm) = SignupFormGenerator(
        faststreamEligible = true, sdipFastStreamConsider = Some(true), sdipFastStreamEligible = Some(true), sdipFastStreamDiversity = Some(true)
      ).get
      signupForm.hasErrors mustBe false
    }

    "pass validation if I have clicked on I am eligible to apply for Fast Stream and confirmed I want to be considered for SDIP," +
      " and have confirmed I am not eligible for SDIP and I do not want to be considered for the diversity strand of SDIP" in {
      // sdipFastStreamDiversity has a  default value of Some(false) so we just use that here
      val (_, signupForm) = SignupFormGenerator(
        faststreamEligible = true, sdipFastStreamConsider = Some(true), sdipFastStreamEligible = Some(true)).get
      signupForm.hasErrors mustBe false
    }

    "generate 2 errors if I am an SDIP Faststream candidate but I haven't clicked on diversity strand of SDIP" +
      " or whether I am eligible for SDIP" in {
      val formData = Map(
        "firstName" -> "name",
        "lastName" -> "last name",
        "email" -> "test@email.com",
        "email_confirm" ->"test@email.com",
        "password" -> "aA1234567",
        "confirmpwd" -> "aA1234567",
        "applicationRoute" -> ApplicationRoute.Faststream.toString,
        "faststreamEligible" -> "true",
        "sdipFastStreamConsider" -> "true",
        "campaignReferrer" -> "Recruitment website",
        "agree" -> "true"
      )

      val form = Form(new SignUpForm().form.mapping).bind(formData)
      form.hasErrors mustBe true
      form.errors.length mustBe 2
      // This uses the same error message as the sdip diversity question
      form.errors("sdipFastStreamDiversity").head.messages mustBe Seq(Messages("agree.sdipDiversity"))
      // This uses the same error message as the sdip eligible question
      form.errors("sdipFastStreamEligible").head.messages mustBe Seq(Messages("agree.sdipEligible"))
    }

    "generate 2 errors if I am an SDIP candidate but I haven't clicked on diversity strand of SDIP or whether I am eligible for SDIP" in {
      val formData = Map(
        "firstName" -> "name",
        "lastName" -> "last name",
        "email" -> "test@email.com",
        "email_confirm" ->"test@email.com",
        "password" -> "aA1234567",
        "confirmpwd" -> "aA1234567",
        "applicationRoute" -> ApplicationRoute.Sdip.toString,
        "campaignReferrer" -> "Recruitment website",
        "agree" -> "true"
      )

      val form = Form(new SignUpForm().form.mapping).bind(formData)
      form.hasErrors mustBe true
      form.errors.length mustBe 2
      form.errors("sdipDiversity").head.messages mustBe Seq(Messages("agree.sdipDiversity"))
      form.errors("sdipEligible").head.messages mustBe Seq(Messages("agree.sdipEligible"))
    }

    "pass validation if I am an SDIP Faststream candidate who is eligible but does not want to be considered for the diversity strand" in {
      val formData = Map(
        "firstName" -> "name",
        "lastName" -> "last name",
        "email" -> "test@email.com",
        "email_confirm" ->"test@email.com",
        "password" -> "aA1234567",
        "confirmpwd" -> "aA1234567",
        "applicationRoute" -> ApplicationRoute.Faststream.toString,
        "faststreamEligible" -> "true",
        "sdipFastStreamConsider" -> "true",
        "sdipFastStreamEligible" -> "true",
        "sdipFastStreamDiversity" -> "false",
        "campaignReferrer" -> "Recruitment website",
        "agree" -> "true"
      )

      val form = Form(new SignUpForm().form.mapping).bind(formData)
      form.hasErrors mustBe false
    }

    "pass validation if I am an SDIP Faststream candidate who is eligible and wants to be considered for the diversity strand" in {
      val formData = Map(
        "firstName" -> "name",
        "lastName" -> "last name",
        "email" -> "test@email.com",
        "email_confirm" ->"test@email.com",
        "password" -> "aA1234567",
        "confirmpwd" -> "aA1234567",
        "applicationRoute" -> ApplicationRoute.Faststream.toString,
        "faststreamEligible" -> "true",
        "sdipFastStreamConsider" -> "true",
        "sdipFastStreamEligible" -> "true",
        "sdipFastStreamDiversity" -> "true",
        "campaignReferrer" -> "Recruitment website",
        "agree" -> "true"
      )

      val form = Form(new SignUpForm().form.mapping).bind(formData)
      form.hasErrors mustBe false
    }

    "pass validation if I am an SDIP candidate who is eligible but does not want to be considered for the diversity strand" in {
      val formData = Map(
        "firstName" -> "name",
        "lastName" -> "last name",
        "email" -> "test@email.com",
        "email_confirm" ->"test@email.com",
        "password" -> "aA1234567",
        "confirmpwd" -> "aA1234567",
        "applicationRoute" -> ApplicationRoute.Sdip.toString,
        "sdipEligible" -> "true",
        "sdipDiversity" -> "false",
        "campaignReferrer" -> "Recruitment website",
        "agree" -> "true"
      )

      val form = Form(new SignUpForm().form.mapping).bind(formData)
      form.hasErrors mustBe false
    }

    "pass validation if I am an SDIP candidate who is eligible and wants to be considered for the diversity strand" in {
      val formData = Map(
        "firstName" -> "name",
        "lastName" -> "last name",
        "email" -> "test@email.com",
        "email_confirm" ->"test@email.com",
        "password" -> "aA1234567",
        "confirmpwd" -> "aA1234567",
        "applicationRoute" -> ApplicationRoute.Sdip.toString,
        "sdipEligible" -> "true",
        "sdipDiversity" -> "true",
        "campaignReferrer" -> "Recruitment website",
        "agree" -> "true"
      )

      val form = Form(new SignUpForm().form.mapping).bind(formData)
      form.hasErrors mustBe false
    }

    "throw an error if I haven't clicked on the I am eligible for EDIP" in {
      val (_, signUpForm) = SignupFormGenerator(applicationRoute = Some(ApplicationRoute.Edip),
        faststreamEligible = false, edipEligible = false).get
      signUpForm.hasErrors mustBe true
      signUpForm.errors.length mustBe 1
      signUpForm.errors("edipEligible").head.messages mustBe Seq(Messages("agree.edipEligible"))
    }

    "throw an error if I haven't clicked on the I am eligible for SDIP" in {
      val (_, signUpForm) = SignupFormGenerator(applicationRoute = Some(ApplicationRoute.Sdip),
                                                faststreamEligible = false, sdipEligible = false).get
      signUpForm.hasErrors mustBe true
      signUpForm.errors.length mustBe 1
      signUpForm.errors("sdipEligible").head.messages mustBe Seq(Messages("agree.sdipEligible"))
    }

    "throw an error if I haven't clicked on the I agree" in {
      val (_, signUpForm) = SignupFormGenerator(agree = false).get
      signUpForm.hasErrors mustBe true
      signUpForm.errors.length mustBe 1
      signUpForm.errors("agree").head.messages mustBe Seq(Messages("agree.accept"))
    }

    "throw and error if I haven't selected a route" in {
      val (_, signUpForm) = SignupFormGenerator(applicationRoute = None).get
      signUpForm.hasErrors mustBe true
      signUpForm.errors.length mustBe 1
      signUpForm.errors("applicationRoute").head.messages mustBe Seq(Messages("error.appRoute"))
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
  confirmpwd: String = "aA1234567",
  campaignReferrer: Option[String] = Some("Recruitment website"),
  campaignOther: Option[String] = None,
  applicationRoute: Option[ApplicationRoute.ApplicationRoute] = Some(ApplicationRoute.Faststream),
  agree: Boolean = true,
  faststreamEligible: Boolean = true,
  sdipFastStreamConsider: Option[Boolean] = Option(false),
  sdipFastStreamEligible: Option[Boolean] = Option(false),
  sdipFastStreamDiversity: Option[Boolean] = Option(false),
  edipEligible: Boolean = false,
  sdipEligible: Boolean = false,
  sdipDiversity: Boolean = false
) {

  private val data = Data(
    firstName,
    lastName,
    email,
    confirmEmail,
    password,
    confirmpwd,
    campaignReferrer,
    campaignOther,
    applicationRoute.map(_.toString).getOrElse(""),
    agree,
    faststreamEligible,
    sdipFastStreamConsider,
    sdipFastStreamEligible,
    sdipFastStreamDiversity,
    edipEligible,
    sdipEligible,
    sdipDiversity
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
    "applicationRoute" -> applicationRoute.map(_.toString).getOrElse(""),
    "agree" -> data.agree.toString,
    "faststreamEligible" -> data.faststreamEligible.toString,
    "edipEligible" -> data.edipEligible.toString,
    "sdipEligible" -> data.sdipEligible.toString,
    "sdipDiversity" -> data.sdipDiversity.toString
  ) ++ data.sdipFastStreamConsider.map(x => "sdipFastStreamConsider" -> x.toString) ++
    data.sdipFastStreamEligible.map(x => "sdipFastStreamEligible" -> x.toString) ++
    data.sdipFastStreamDiversity.map(x => "sdipFastStreamDiversity" -> x.toString)

  private val formWrapper = new SignUpForm()
  private def signUpForm(implicit messages: Messages) = Form(formWrapper.form.mapping).bind(validFormData)

  def get(implicit messages: Messages) = (data, signUpForm)
}
