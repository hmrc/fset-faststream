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

import play.api.data.FormError
import play.api.data.format.Formatter
import play.api.i18n.Messages

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
      val requiredField: Option[String] = data.get(requiredKey)
      val keyField: Option[String] = data.get(key)

      (requiredField, keyField) match {
        case (Some("Yes"), None) => Left(List(FormError(key, Messages(s"error.$key.required"))))
        case (Some("Yes"), Some("")) => Left(List(FormError(key, Messages(s"error.$key.required"))))
        case _ => if (maxLength.isDefined && keyField.isDefined && keyField.get.size > maxLength.get) {
          Left(List(FormError(key, Messages(s"error.$key.maxLength")))) } else { Right(keyField) }
      }
    }

    override def unbind(key: String, value: Option[String]): Map[String, String] = Map(key -> value.getOrElse(""))
  }

  def requiredFormatterWithMaxLengthCheckAndSeparatePreferNotToSay(requiredKey: String,
                                                                   key: String,
                                                                   keyPreferNotToSay: String,
                                                                   maxLength: Option[Int]) = new Formatter[Option[String]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      val requiredField: Option[String] = data.get(requiredKey)
      val keyField: Option[String] = data.get(key)
      val keyPreferNotToSayField: Option[String] = data.get(keyPreferNotToSay)

      (requiredField, keyField, keyPreferNotToSayField) match {
        case (Some("Yes"), None, None) => Left(List(FormError(key, Messages(s"error.$key.required"))))
        case (Some("Yes"), Some(""), None) => Left(List(FormError(key, Messages(s"error.$key.required"))))
        case (Some("Yes"), Some(""), Some("Yes")) => Right(Some("I don't know/prefer not to say"))
        case (Some("Yes"), None, Some("Yes")) => Right(Some("I don't know/prefer not to say"))
        case _ => if (maxLength.isDefined && keyField.isDefined && keyField.get.size > maxLength.get) {
          Left(List(FormError(key, Messages(s"error.$key.maxLength")))) } else { Right(keyField) }
      }
    }

    override def unbind(key: String, value: Option[String]): Map[String, String] = Map(key -> value.getOrElse(""))
  }

}
