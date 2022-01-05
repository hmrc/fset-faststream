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

import javax.inject.Singleton
import mappings.Mappings._
import models.frameworks.Alternatives
import play.api.data.Form
import play.api.data.Forms._

import scala.language.implicitConversions

@Singleton
class AlternateLocationsForm {
  val form = Form(
    mapping(
      "alternativeLocation" -> nonEmptyTrimmedText("error.alternativeLocation", 3),
      "alternativeScheme" -> nonEmptyTrimmedText("error.alternativeScheme", 3)
    )(AlternateLocationsForm.Data.apply)(AlternateLocationsForm.Data.unapply)
  )

  import forms.AlternateLocationsForm._

  implicit def fromAlternativesToFormData(alt: Alternatives): AlternateLocationsForm.Data =
    AlternateLocationsForm.Data(
      if (alt.location) yes else no, if (alt.framework) yes else no
    )

  implicit def fromFormDataToAlternatives(data: Data): Alternatives = Alternatives(
    data.alternativeLocation == yes, data.alternativeScheme == yes
  )
}

object AlternateLocationsForm {
  type YesNo = String
  val yes = "Yes"
  val no = "No"

  case class Data(
    alternativeLocation: YesNo,
    alternativeScheme: YesNo
  )
}
