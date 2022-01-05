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

import connectors.exchange.SelectedSchemes
import connectors.exchange.referencedata.Scheme
import forms.SelectedSchemesForm.SchemePreferences
import models.page.SelectedSchemesPage
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.{ Form, FormError }
import play.api.i18n.Messages

import scala.language.implicitConversions

class SelectedSchemesForm(allSchemes: Seq[Scheme], isSdipFaststream: Boolean) {

  private val page = SelectedSchemesPage(allSchemes)

  // Sdip FS candidates are automatically given the Sdip scheme so they can have 5 in total
  // This is why we filter out the Sdip scheme below when performing the validation checks
  private val maxFaststreamSchemes = 4

  def form(implicit messages: Messages) = {
    Form(
      mapping(
        "schemes" -> of(schemeFormatter("schemes")),
        "orderAgreed" -> checked(Messages("orderAgreed.required")),
        "eligible" -> checked(Messages("eligible.required"))
      )(SchemePreferences.apply)(SchemePreferences.unapply))
  }

  //scalastyle:off cyclomatic.complexity
  def schemeFormatter(formKey: String)(implicit messages: Messages) = new Formatter[List[String]] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], List[String]] = {
      page.getSchemesByPriority(data) match {
        case selectedSchemes if selectedSchemes.isEmpty || (isSdipFaststream && selectedSchemes.map(_.toLowerCase) == Seq("sdip")) =>
          Left(List(FormError(formKey, Messages("schemes.required"))))
        case selectedSchemes if isSdipFaststream && selectedSchemes.filterNot(_.toLowerCase == "sdip").size > maxFaststreamSchemes =>
          Left(List(FormError(formKey, Messages("schemes.tooMany"))))
        case selectedSchemes if !isSdipFaststream && selectedSchemes.size > maxFaststreamSchemes =>
          Left(List(FormError(formKey, Messages("schemes.tooMany"))))
        case selectedSchemes if selectedSchemes.size > allSchemes.size =>
          Left(List(FormError(formKey, Messages("schemes.required"))))
        case selectedSchemes if page.getInvalidSchemes(selectedSchemes).nonEmpty =>
          Left(List(FormError(formKey, Messages("schemes.required"))))
        case selectedSchemes =>
          Right(selectedSchemes)
      }
    }

    def unbind(key: String, value: List[String]): Map[String, String] = {
      value.map(key => key -> Messages("scheme." + key + ".description")).toMap
    }
  } //scalastyle:on
}

object SelectedSchemesForm {

  case class SchemePreferences(schemes: List[String], orderAgreed: Boolean, eligible: Boolean)

  implicit def toSchemePreferences(selectedSchemes: SelectedSchemes): SchemePreferences = SchemePreferences(
    selectedSchemes.schemes,
    selectedSchemes.orderAgreed,
    selectedSchemes.eligible
  )

  implicit def toSelectedSchemes(schemePreferences: SchemePreferences): SelectedSchemes = SelectedSchemes(
    schemePreferences.schemes,
    schemePreferences.orderAgreed,
    schemePreferences.eligible
  )
}
