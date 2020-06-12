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

import play.api.data.FormError
import play.api.data.format.Formatter
import play.api.i18n.Messages
import play.api.i18n.Messages.Implicits._
import play.api.Play.current

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

package object forms {

  implicit class sanitizeOptions(value: Option[String]) {
    def sanitize = value match {
      case Some(v) if !v.trim.isEmpty => Some(v)
      case _ => None
    }
  }

  def requiredFormatterWithMaxLengthCheck(requiredKey: String, key: String, maxLength: Option[Int]) = new Formatter[Option[String]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      val requiredField: Option[String] = if (data.isEmpty) None else data.get(requiredKey)
      val keyField: Option[String] = if (data.isEmpty) None else data.get(key).map(_.trim)

      (requiredField, keyField) match {
        case (Some("Yes"), None) => Left(List(FormError(key, Messages(s"error.$key.required"))))
        case (Some("Yes"), Some("")) => Left(List(FormError(key, Messages(s"error.$key.required"))))
        case _ => if (maxLength.isDefined && keyField.isDefined && keyField.get.length > maxLength.get) {
          Left(List(FormError(key, Messages(s"error.$key.maxLength"))))
        } else {
          Right(keyField)
        }
      }
    }

    override def unbind(key: String, value: Option[String]): Map[String, String] = Map(key -> value.getOrElse(""))
  }

  // scalastyle:off cyclomatic.complexity
  def requiredFormatterWithValidationCheckAndSeparatePreferNotToSay(
        requiredKey: String,
        key: String,
        keyPreferNotToSay: String,
        maxLength: Option[Int],
        msgRequiredError: Option[String] = None
      )(
        implicit invalidFn: (String => Boolean) = inputValue => maxLength.exists(_ < inputValue.trim.length),
        validationErrorKey:String = s"error.$key.maxLength"
      ) = new Formatter[Option[String]] {
          override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] = {
            val requiredField: Option[String] = if (data.isEmpty) None else data.get(requiredKey)
            val keyField: Option[String] = if (data.isEmpty) None else data.get(key).map(_.trim)
            val keyPreferNotToSayField: Option[String] = if (data.isEmpty) None else data.get(keyPreferNotToSay)
            def requiredErrorMsg = msgRequiredError.getOrElse(Messages(s"error.$key.required"))

            (requiredField, keyField, keyPreferNotToSayField) match {
              case (Some("Yes"), None, None) => Left(List(FormError(key, requiredErrorMsg)))
              case (Some("Yes"), Some(keyValue), None) if keyValue.trim.isEmpty => Left(List(FormError(key, requiredErrorMsg)))
              case (Some("Yes"), Some(keyValue), None) if invalidFn(keyValue) => Left(List(FormError(key, Messages(validationErrorKey))))
              case (Some("Yes"), _, Some("Yes")) => Right(Some("I don't know/prefer not to say"))
              case _ => Right(keyField)
            }
          }

          override def unbind(key: String, value: Option[String]): Map[String, String] = Map(key -> value.getOrElse(""))
  }
  // scalastyle:on
}
