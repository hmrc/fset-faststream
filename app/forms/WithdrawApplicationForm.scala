/*
 * Copyright 2021 HM Revenue & Customs
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
import models.view.WithdrawReasons
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.{Form, FormError}
import play.api.i18n.Messages

@Singleton
class WithdrawApplicationForm {
  val mandatoryReason = WithdrawReasons.list.find(_._2).map(_._1).get

  def otherReasonFormatter(maxLength: Option[Int])(implicit messages: Messages) = new Formatter[Option[String]] {
    override def bind(key: String, data: Map[String, String]) = (data.get("reason"), data.get(key)) match {
      case (Some(`mandatoryReason`), None | Some("")) => Left(Seq(FormError(key, Messages("error.required.reason.more_info"))))
      case _ =>
        val fieldValue = data.get(key)
        if (maxLength.isDefined && fieldValue.isDefined && fieldValue.get.length > maxLength.get) {
          Left(List(FormError(key, Messages(s"error.$key.maxLength"))))
        } else {
          Right(fieldValue)
        }
    }

    override def unbind(key: String, value: Option[String]): Map[String, String] = Map(key -> value.getOrElse(""))
  }

  def form(implicit messages: Messages) = Form(
    mapping(
      "wantToWithdraw" -> nonEmptyTrimmedText("error.wantToWithdraw.required", 5),
      "reason" -> of(requiredFormatterWithMaxLengthCheck("wantToWithdraw", "reason", Some(64))),
      "otherReason" -> of(otherReasonFormatter(Some(300)))
    )(WithdrawApplicationForm.Data.apply)(WithdrawApplicationForm.Data.unapply)
  )
}

object WithdrawApplicationForm {
  case class Data(
                   wantToWithdraw: String,
                   reason: Option[String],
                   otherReason: Option[String]
                 )
}
