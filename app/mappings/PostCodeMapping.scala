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

package mappings

import play.api.data.validation.{ Constraint, Invalid, Valid, ValidationError }

object PostCodeMapping {

  type PostCode = String
  // putting this on multiple lines won't make this regex any clearer
  // scalastyle:off line.size.limit
  val postcodePattern = """^(?i)(GIR 0AA)|((([A-Z][0-9][0-9]?)|(([A-Z][A-HJ-Y][0-9][0-9]?)|(([A-Z][0-9][A-Z])|([A-Z][A-HJ-Y][0-9]?[A-Z])))) ?[0-9][A-Z]{2})$""".r
  // scalastyle:on line.size.limit

  def validPostcode: Constraint[PostCode] = Constraint[PostCode]("constraint.postcode") { postcode =>
    postcodePattern.pattern.matcher(postcode).matches match {
      case true => Valid
      case false if postcode.isEmpty => Invalid(ValidationError("error.postcode.required"))
      case false => Invalid(ValidationError("error.postcode.invalid"))
    }
  }

  /*
   * Method copied from DVLA repo vehicles-presentation-common: https://github.com/dvla/vehicles-presentation-common
   * Class in DVLA repo: uk.gov.dvla.vehicles.presentation.common.views.constraints.Postcode
   */
  def formatPostcode(postcode: String): String = {
    val SpaceCharDelimiter = " "
    val A99AA = "([A-Z][0-9]{2}[A-Z]{2})".r
    val A099AA = "([A-Z][0][0-9]{2}[A-Z]{2})".r
    val A999AA = "([A-Z][0-9]{3}[A-Z]{2})".r
    val A9A9AA = "([A-Z][0-9][A-Z][0-9][A-Z]{2})".r
    val AA99AA = "([A-Z]{2}[0-9]{2}[A-Z]{2})".r
    val AA099AA = "([A-Z]{2}[0][0-9]{2}[A-Z]{2})".r
    val AA999AA = "([A-Z]{2}[0-9]{3}[A-Z]{2})".r
    val AA9A9AA = "([A-Z]{2}[0-9][A-Z][0-9][A-Z]{2})".r

    postcode.toUpperCase.replace(SpaceCharDelimiter, "") match {
      case A99AA(p) => p.substring(0, 2) + SpaceCharDelimiter + p.substring(2, 5)
      case A099AA(p) => p.substring(0, 1) + p.substring(2, 3) + SpaceCharDelimiter + p.substring(3, 6)
      case A999AA(p) => p.substring(0, 3) + SpaceCharDelimiter + p.substring(3, 6)
      case A9A9AA(p) => p.substring(0, 3) + SpaceCharDelimiter + p.substring(3, 6)
      case AA99AA(p) => p.substring(0, 3) + SpaceCharDelimiter + p.substring(3, 6)
      case AA099AA(p) => p.substring(0, 2) + p.substring(3, 4) + SpaceCharDelimiter + p.substring(4, 7)
      case AA999AA(p) => p.substring(0, 4) + SpaceCharDelimiter + p.substring(4, 7)
      case AA9A9AA(p) => p.substring(0, 4) + SpaceCharDelimiter + p.substring(4, 7)
      case _ => postcode.replaceAll("\\*", " ") // partial postcodes include asterisks so replace with spaces
    }
  }

}
