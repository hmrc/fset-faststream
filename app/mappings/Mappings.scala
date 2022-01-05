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

package mappings

import forms._
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.validation._
import play.api.data.{ FormError, Mapping }
import play.api.i18n.Messages

// scalastyle:off line.size.limit
// Adapted from:
// https://github.com/dvla/vehicles-presentation-common/blob/9a494e4e4c3732fa827b2d1e7cc4499ed44d42c0/app/uk/gov/dvla/vehicles/presentation/common/views/helpers/FormExtensions.scala
// scalastyle:one line.size.limit
object Mappings {
  // Max error key not always required: may only be intended to prevent attackers, so helpful error message not required.
  def nonEmptyTrimmedText(
    emptyErrorKey: String
  ): Mapping[String] =
    trimmedText(None, None, None, None, Some(emptyErrorKey))

  def nonEmptyTrimmedText(
    emptyErrorKey: String,
    maxLength: Int
  ): Mapping[String] =
    trimmedText(Some(maxLength), None, None, None, Some(emptyErrorKey))

  // When user is expected to exceed max length under normal usage.
  def nonEmptyTrimmedText(
    emptyErrorKey: String,
    maxLength: Int,
    maxErrorKey: String
  ): Mapping[String] =
    trimmedText(Some(maxLength), Some(maxErrorKey), None, None, Some(emptyErrorKey))

  def nonEmptyTrimmedText(
    emptyErrorKey: String,
    maxLength: Int,
    maxErrorKey: String,
    minLength: Int,
    minErrorKey: String
  ): Mapping[String] =
    trimmedText(Some(maxLength), Some(maxErrorKey), Some(minLength), Some(minErrorKey), Some(emptyErrorKey))

  // Max error key not always required: may only be intended to prevent attackers, so helpful error message not required.
  def optionalTrimmedText(maxLength: Int): Mapping[Option[String]] =
    optional(trimmedText(Some(maxLength), None, None, None, None))

  // When user is expected to exceed max length under normal usage.
  def optionalTrimmedText(maxLength: Int, maxErrorKey: String): Mapping[Option[String]] =
    optional(trimmedText(Some(maxLength), Some(maxErrorKey), None, None, None))

  def optionalTrimmedText(
    maxLength: Int,
    maxErrorKey: String,
    minLength: Int,
    minErrorKey: String
  ): Mapping[Option[String]] =
    optional(trimmedText(Some(maxLength), Some(maxErrorKey), Some(minLength), Some(minErrorKey), None))

  private def trimmedText(
    maxLength: Option[Int],
    maxErrorKey: Option[String],
    minLength: Option[Int],
    minErrorKey: Option[String],
    emptyErrorKey: Option[String]
  ): Mapping[String] =
    textWithTransform(_.replaceAll("(^\\h*)|(\\h*$)", "").trim)(maxLength, maxErrorKey, minLength, minErrorKey, emptyErrorKey)

  private def textWithTransform(transform: String => String)(
    maxLength: Option[Int],
    maxErrorKey: Option[String],
    minLength: Option[Int],
    minErrorKey: Option[String],
    emptyErrorKey: Option[String]
  ): Mapping[String] = {

    val emptyError = emptyErrorKey.getOrElse("error.required")
    val minError = minErrorKey.getOrElse("error.minLength")
    val maxError = maxErrorKey.getOrElse("error.maxLength")

    new Formatter[String] {
      def bind(key: String, data: Map[String, String]) = {
        val nonEmptyValue = data.get(key).map(dataValue => transform(dataValue)).filterNot(_.isEmpty)
        nonEmptyValue match {
          case Some(value) =>
            if (minLength.exists(_ > value.length)) {
              Left(Seq(FormError(key, minError, Nil)))
            } else if (maxLength.exists(_ < value.length)) {
              Left(Seq(FormError(key, maxError, Nil)))
            } else {
              Right(value)
            }
          case None =>
            Left(Seq(FormError(key, emptyError, Nil)))
        }
      }

      def unbind(key: String, value: String) = Map(key -> value)
    }

    val fieldMapping = of[String](transformedStringFormat(emptyError, transform))

    (minLength, maxLength) match {
      case (Some(0) | None, Some(Int.MaxValue)) => fieldMapping
      case (Some(0) | None, Some(max)) => fieldMapping verifying Constraints.maxLength(max)
      case (Some(min), None | Some(Int.MaxValue)) => fieldMapping verifying Constraints.minLength(min)
      case (Some(min), Some(max)) => fieldMapping verifying (Constraints.minLength(min), Constraints.maxLength(max))
      case (None, None) => fieldMapping
    }
  }

  private def transformedStringFormat(whenEmptyErrorKey: String, transform: String => String): Formatter[String] = new Formatter[String] {
    def bind(key: String, data: Map[String, String]) = {
      val value = data.get(key).map(dataValue => transform(dataValue)).filterNot(_.isEmpty)
      value.toRight(Seq(FormError(key, whenEmptyErrorKey, Nil)))
    }

    def unbind(key: String, value: String) = Map(key -> value)
  }

  def fieldWithCheckBox(max: Int, conditionKey: Option[String] = None,
    skipValues: Seq[String] = Seq.empty[String], relationField: Option[String] = None)
(implicit messages: Messages)= new Formatter[Option[String]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      val conditionValue = conditionKey.flatMap(key => data.get(key).map(_.trim).filter(_.length < max).sanitize)
      val valueField = data.get(key).map(_.trim).filter(_.length < max).sanitize
      val preferNotToSayField = data.get(s"preferNotSay_$key").sanitize

      val preconditionMet = if (skipValues.contains("none") && conditionValue.isEmpty) {
        Some(true)
      } else {
        conditionValue.map(value => skipValues.contains(value))
      }

      (preconditionMet, preferNotToSayField, valueField) match {
        case (Some(true), _, _) => Right(None)
        case (_, None, None) => Left(List(FormError(key, Messages(s"error.required.$key"))))
        case (_, Some(_), _) => Right(None)
        case _ => Right(valueField)
      }
    }

    override def unbind(key: String, value: Option[String]): Map[String, String] = Map(key -> value.getOrElse(""))
  }

  def nonemptyBooleanText(emptyErrorKey: String) = {
    nonEmptyTrimmedText(emptyErrorKey, 5)
      .verifying(emptyErrorKey, paramVal => paramVal == true.toString || paramVal == false.toString)
  }

  object BooleanMapping {
    type BooleanMapping = Option[Boolean]

    def validateBoolean(boolean: String): Boolean = ("true" :: "false" :: Nil).contains(boolean)

    def booleanFormatter(errorPrefix: String) = new Formatter[Option[Boolean]] {
      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[Boolean]] = {
        val booleanString: Option[String] = data.get(key)

        booleanString match {
          case None | Some("") => Left(List(FormError(key, s"$errorPrefix.required")))
          case Some(m) if !m.isEmpty && !validateBoolean(m) => Left(List(FormError(key, s"$errorPrefix.format")))
          case _ => Right(booleanString.map(_.trim.toBoolean))
        }
      }

      override def unbind(key: String, value: Option[Boolean]) = Map(key -> value.map(_.toString).getOrElse(""))
    }
  }

  def mayBeOptionalString(emptyErrorKey: String, maxLength: Int,
                          required: Map[String, String] => Boolean)
(implicit messages: Messages)= new Formatter[Option[String]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      if (required(data)) {
        data.getOrElse(key, "").trim match {
          case param if param.isEmpty => Left(List(FormError(key, Messages(emptyErrorKey))))
          case param if param.length > maxLength => Left(List(FormError(key, Messages("error.maxLength"))))
          case param => Right(Some(param))
        }
      } else {
        Right(None)
      }
    }
    override def unbind(key: String, value: Option[String]): Map[String, String] = Map(key -> value.getOrElse(""))
  }

  def mayBeOptionalString(emptyErrorKey: String, additionalCheckEmptyErrorKey: String, maxLength: Int,
                           required: Map[String, String] => Boolean,
                           additionalCheck: Map[String, String] => Boolean)
(implicit messages: Messages)= new Formatter[Option[String]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      val additionalCheckResult = additionalCheck(data)

      if (required(data)) {
        data.getOrElse(key, "").trim match {
          case param if param.isEmpty =>
            if (additionalCheckResult) {
              Left(List(FormError(key, Messages(additionalCheckEmptyErrorKey))))
            } else {
              Left(List(FormError(key, Messages(emptyErrorKey))))
            }
          case param if param.length > maxLength => Left(List(FormError(key, Messages("error.maxLength"))))
          case param => Right(Some(param))
        }
      } else {
        Right(None)
      }
    }
    override def unbind(key: String, value: Option[String]): Map[String, String] = Map(key -> value.getOrElse(""))
  }
}
