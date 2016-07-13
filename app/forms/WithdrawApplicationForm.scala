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
import models.WithdrawReasons
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.{ Form, FormError }
import play.api.i18n.Messages

object WithdrawApplicationForm {

  val mandatoryReason = WithdrawReasons.list.find(_._2).map(_._1).get

  def otherReasonFormatter = new Formatter[Option[String]] {
    override def bind(key: String, data: Map[String, String]) = (data.get("reason"), data.get(key)) match {
      case (Some(`mandatoryReason`), None | Some("")) => Left(Seq(FormError(key, Messages("error.required.reason.more_info"))))
      case _ => Right(data.get(key))
    }

    override def unbind(key: String, value: Option[String]): Map[String, String] = Map(key -> value.getOrElse(""))
  }

  val form = Form(
    mapping(
      "reason" -> nonEmptyTrimmedText("error.required.reason", 64),
      "otherReason" -> of(otherReasonFormatter)
    )(Data.apply)(Data.unapply)
  )

  case class Data(
    reason: String,
    otherReason: Option[String]
  )

}
