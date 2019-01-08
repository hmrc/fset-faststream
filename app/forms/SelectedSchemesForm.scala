/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.i18n.Messages.Implicits._
import play.api.Play.current

class SelectedSchemesForm(allSchemes: Seq[Scheme]) {

  private val page = SelectedSchemesPage(allSchemes)

  private val maxSchemes = 4

  def form = {
    Form(
      mapping(
        "schemes" -> of(schemeFormatter("schemes")),
        "orderAgreed" -> checked(Messages("orderAgreed.required")),
        "eligible" -> checked(Messages("eligible.required"))
      )(SchemePreferences.apply)(SchemePreferences.unapply))
  }

  def schemeFormatter(formKey: String) = new Formatter[List[String]] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], List[String]] = {
      page.getSchemesByPriority(data) match {
        case selectedSchemes if selectedSchemes.isEmpty => Left(List(FormError(formKey, Messages("schemes.required"))))
        case selectedSchemes if selectedSchemes.size > maxSchemes => Left(List(FormError(formKey, Messages("schemes.tooMany"))))
        case selectedSchemes if selectedSchemes.size > allSchemes.size => Left(List(FormError(formKey, Messages("schemes.required"))))
        case selectedSchemes if page.getInvalidSchemes(selectedSchemes).nonEmpty => Left(List(FormError(formKey, Messages("schemes.required"))))
        case selectedSchemes => Right(selectedSchemes)
      }
    }

    def unbind(key: String, value: List[String]): Map[String, String] = {
      value.map(key => key -> Messages("scheme." + key + ".description")).toMap
    }
  }
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
