/*
 * Copyright 2020 HM Revenue & Customs
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

import javax.inject.Singleton
import mappings.Mappings._
import models.ApplicationRoute
import models.view.CampaignReferrers
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.validation.Constraints
import play.api.data.{Form, FormError}
import play.api.i18n.Messages

@Singleton
class SignUpForm {
  val passwordMinLength = 9
  val passwordMaxLength = 128

  // scalastyle:off cyclomatic.complexity
  def passwordFormatter(implicit messages: Messages) = new Formatter[String] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], String] = {
      val passwd = data("password").trim
      val confirm = data("confirmpwd").trim

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

    override def unbind(key: String, value: String): Map[String, String] = Map(key -> value)
  }
  // scalastyle:on cyclomatic.complexity

  def emailConfirm(implicit messages: Messages) = new Formatter[String] {

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

  def applicationRouteFormatter(implicit messages: Messages) = new Formatter[String] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], String] = {
      data.get(key) match {
        case Some(appRoute) if appRoute.nonEmpty =>
          ApplicationRoute.withName(appRoute) match {
            case ApplicationRoute.Faststream => fastStreamCheck(appRoute, data)

            case ApplicationRoute.Edip => edipEligibilityCheck(data)

            case ApplicationRoute.Sdip => sdipEligibiliyCheck(data)

            case unknown => Left(List(FormError("eligible", s"Unrecognised application route $unknown")))
          }

        case _ => Left(List(FormError("applicationRoute", Messages("error.appRoute"))))
      }
    }

    override def unbind(key: String, value: String): Map[String, String] = Map(key -> value)
  }

  private def edipEligibilityCheck(data: Map[String, String])(implicit messages: Messages): Either[Seq[FormError], String] = {
    data.get("edipEligible").map(_.toLowerCase) match {
      case Some("true") => Right(ApplicationRoute.Edip)
      case _ => Left(List(FormError("edipEligible", Messages("agree.edipEligible"))))
    }
  }

  private def fastStreamCheck(appRoute: String, data: Map[String, String])
    (implicit messages: Messages): Either[Seq[FormError], String] = {
    val fastStreamEligible = data.get("faststreamEligible").map(_.toLowerCase)
    val sdipFastStreamConsider = data.get("sdipFastStreamConsider").map(_.toLowerCase)
    (fastStreamEligible, sdipFastStreamConsider) match {
      case (Some("true"), Some("false")) => Right(appRoute)
      case (Some("true"), Some("true")) => sdipFsCheck(data)
      case (Some("true"), None) => Left(List(FormError("sdipFastStreamConsider", Messages("sdipFastStream.consider"))))
      case (_ , _) => Left(List(
        FormError("sdipFastStreamConsider", Messages("sdipFastStream.consider")),
        FormError("faststreamEligible", Messages("agree.faststreamEligible"))
      ))
    }

  }

  private def sdipFsCheck(data: Map[String, String])
    (implicit messages: Messages): Either[Seq[FormError], String] = {
    data.get("sdipFastStreamEligible").map(_.toLowerCase) match {
      case Some("true") => Right(ApplicationRoute.SdipFaststream)
      case _ => Left(List(FormError("sdipFastStreamEligible", Messages("agree.sdipEligible"))))
    }
  }

  private def sdipEligibiliyCheck(postData: Map[String, String])
    (implicit messages: Messages): Either[Seq[FormError], String] = {
    val sdipEligible = postData.get("sdipEligible").map(_.toLowerCase)

    val errors = if (!sdipEligible.contains("true")) {
      List(FormError("sdipEligible", Messages("agree.sdipEligible")))
    } else {
      Nil
    }

    if (errors.isEmpty) {
      Right(ApplicationRoute.Sdip)
    } else {
      Left(errors)
    }
  }

  def form(implicit messages: Messages): Form[SignUpForm.Data] = Form(
    mapping(
      "firstName" -> nonEmptyTrimmedText("error.firstName", 256),
      "lastName" -> nonEmptyTrimmedText("error.lastName", 256),
      "email" -> (email verifying Constraints.maxLength(128)),
      "email_confirm" -> of(emailConfirm),
      SignUpForm.passwordField -> of(passwordFormatter),
      SignUpForm.confirmPasswordField -> nonEmptyTrimmedText("error.confirmpwd", passwordMaxLength),
      "campaignReferrer" -> optionalTrimmedText(64),
      "campaignOther" -> of(campaignOtherFormatter),
      "applicationRoute" -> of(applicationRouteFormatter),
      "agree" -> checked(Messages("agree.accept")),
      "faststreamEligible" -> boolean,
      "sdipFastStreamConsider" -> optional(boolean),
      "sdipFastStreamEligible" -> optional(boolean),
      "edipEligible" -> boolean,
      "sdipEligible" -> boolean
    )(SignUpForm.Data.apply)(SignUpForm.Data.unapply)
  )

  import SignUpForm.RequestValidation

  def campaignOtherFormatter(implicit messages: Messages) = new Formatter[Option[String]] {
    override def bind(key: String, request: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      val optCampaignOther = request.get("campaignOther")
      if (request.hasOptionalInfoProvided) {
        optCampaignOther match {
          case Some(campaignOther) if campaignOther.trim.length > 256 => Left(List(FormError(key, Messages(s"error.$key.maxLength"))))
          case _ => Right(optCampaignOther.map(_.trim))
        }
      } else {
        Right(None)
      }
    }

    override def unbind(key: String, value: Option[String]): Map[String, String] = Map(key -> value.map(_.trim).getOrElse(""))
  }
}

object SignUpForm {
  implicit class RequestValidation(request: Map[String, String]) {
    def hasOptionalInfoProvided = CampaignReferrers.list.find(pair =>
      pair._1 == request.getOrElse("campaignReferrer", "")).exists(_._2)

    def sanitize = request.filterKeys {
      case "campaignOther" => hasOptionalInfoProvided
      case _ => true
    }
  }

  val passwordField = "password"
  val confirmPasswordField = "confirmpwd"
  val fakePasswordField = "fake-password" // Used only in view (to prevent auto-fill)

  case class Data(
    firstName: String,
    lastName: String,
    email: String,
    confirmEmail: String,
    password: String,
    confirmpwd: String,
    campaignReferrer: Option[String],
    campaignOther: Option[String],
    applicationRoute: String,
    agree: Boolean,
    faststreamEligible: Boolean,
    sdipFastStreamConsider: Option[Boolean],
    sdipFastStreamEligible: Option[Boolean],
    edipEligible: Boolean,
    sdipEligible: Boolean
  )
}
