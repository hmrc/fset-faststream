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

import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.validation.Constraints
import play.api.data.{ FormError, Mapping }
import play.api.i18n.Messages

// scalastyle:off line.size.limit
// Adapted from:
// https://github.com/dvla/vehicles-presentation-common/blob/9a494e4e4c3732fa827b2d1e7cc4499ed44d42c0/app/uk/gov/dvla/vehicles/presentation/common/views/helpers/FormExtensions.scala
// scalastyle:one line.size.limit
object Mappings {

  // Max error key not always required: may only be intended to prevent attackers, so helpful error message not required.
  def nonEmptyTrimmedText(
    emptyErrorKey: String,
    maxLength: Int
  ): Mapping[String] =
    trimmedText(maxLength, None, None, None, Some(emptyErrorKey))

  // When user is expected to exceed max length under normal usage.
  def nonEmptyTrimmedText(
    emptyErrorKey: String,
    maxLength: Int,
    maxErrorKey: String
  ): Mapping[String] =
    trimmedText(maxLength, Some(maxErrorKey), None, None, Some(emptyErrorKey))

  def nonEmptyTrimmedText(
    emptyErrorKey: String,
    maxLength: Int,
    maxErrorKey: String,
    minLength: Int,
    minErrorKey: String
  ): Mapping[String] =
    trimmedText(maxLength, Some(maxErrorKey), Some(minLength), Some(minErrorKey), Some(emptyErrorKey))

  // Max error key not always required: may only be intended to prevent attackers, so helpful error message not required.
  def optionalTrimmedText(maxLength: Int): Mapping[Option[String]] =
    optional(trimmedText(maxLength, None, None, None, None))

  // When user is expected to exceed max length under normal usage.
  def optionalTrimmedText(maxLength: Int, maxErrorKey: String): Mapping[Option[String]] =
    optional(trimmedText(maxLength, Some(maxErrorKey), None, None, None))

  def optionalTrimmedText(
    maxLength: Int,
    maxErrorKey: String,
    minLength: Int,
    minErrorKey: String
  ): Mapping[Option[String]] =
    optional(trimmedText(maxLength, Some(maxErrorKey), Some(minLength), Some(minErrorKey), None))

  private def trimmedText(
    maxLength: Int,
    maxErrorKey: Option[String],
    minLength: Option[Int],
    minErrorKey: Option[String],
    emptyErrorKey: Option[String]
  ): Mapping[String] =
    textWithTransform(_.replaceAll("(^\\h*)|(\\h*$)", "").trim)(maxLength, maxErrorKey, minLength, minErrorKey, emptyErrorKey)

  private def textWithTransform(transform: String => String)(
    maxLength: Int,
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
            } else if (maxLength < value.length) {
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
      case (Some(0) | None, Int.MaxValue) => fieldMapping
      case (Some(0) | None, max) => fieldMapping verifying Constraints.maxLength(max)
      case (Some(min), Int.MaxValue) => fieldMapping verifying Constraints.minLength(min)
      case (Some(min), max) => fieldMapping verifying (Constraints.minLength(min), Constraints.maxLength(max))
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
    skipValues: Seq[String] = Seq.empty[String], relationField: Option[String] = None) = new Formatter[Option[String]] {
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

}
