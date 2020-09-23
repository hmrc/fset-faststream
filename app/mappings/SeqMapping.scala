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

package mappings

import play.api.data.FormError
import play.api.data.format.Formatter
import play.api.i18n.Messages

object SeqMapping {
  def requiredSetFormatter(allowedValues: Seq[String])(implicit messages: Messages)
  = conditionalRequiredSetFormatter(_ => true, allowedValues)

  def conditionalRequiredSetFormatter(
    enableCondition: (Map[String, String]) => Boolean,
    allowedValues: Seq[String]
  )(implicit messages: Messages) = new Formatter[Option[String]] {

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      enableCondition(data) match {
        case false => Right(None)
        case true => data.get(key) match {
          case None => Left(List(FormError(key, Messages(s"$key.chooseone"))))
          case Some(value) if !allowedValues.contains(value)
            => Left(Seq(FormError(key, Messages(s"$key.unsupported.value"))))
          case Some(value) => Right(Some(value))
        }
      }
    }

    override def unbind(key: String, value: Option[String]): Map[String, String] = Map(key -> value.getOrElse(""))
  }
}
