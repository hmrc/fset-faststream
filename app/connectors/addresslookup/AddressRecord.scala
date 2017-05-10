/*
 * Copyright 2017 HM Revenue & Customs
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

package connectors.addresslookup

import play.api.libs.json.Json


/**
  * The following DTOs are taken from https://github.com/hmrc/address-reputation-store. The project has
  * not been added as a dependency, as it brings in many transitive dependencies that are not needed,
  * as well as data cleansing/ingestion and backward compatibility logic that is not needed for this project.
  * If the version 2 api gets deprecated, then these DTOs will have to change.
  * There have been some minor changes made to the code to ensure that it compiles and passes scalastyle,
  * but there is some copied code that is not idiomatic Scala and should be changed at some point in the future
  */

case class LocalCustodian(code: Int, name: String)

object LocalCustodian {
  implicit val localCustodianReads = Json.reads[LocalCustodian]
  implicit val localCustodianWrites = Json.writes[LocalCustodian]
}

/** Represents a country as per ISO3166. */
case class Country(
                    // ISO3166-1 or ISO3166-2 code, e.g. "GB" or "GB-ENG" (note that "GB" is the official
                    // code for UK although "UK" is a reserved synonym and may be used instead)
                    // See https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2
                    // and https://en.wikipedia.org/wiki/ISO_3166-2:GB
                    code: String,
                    // The printable name for the country, e.g. "United Kingdom"
                    name: String)

object Country {
  implicit val countryReads = Json.reads[Country]
  implicit val countryWrites = Json.writes[Country]
}

case class Outcode(area: String, district: String) {
  override lazy val toString: String = area + district
}

object Outcode {
  implicit val outcodeReads = Json.reads[Outcode]
}

object Countries {
  // note that "GB" is the official ISO code for UK, although "UK" is a reserved synonym and is less confusing
  val UK = Country("UK", "United Kingdom")
  val GB = Country("GB", "United Kingdom") // special case provided for in ISO-3166
  val GG = Country("GG", "Guernsey")
  val IM = Country("IM", "Isle of Man")
  val JE = Country("JE", "Jersey")

  val England = Country("GB-ENG", "England")
  val Scotland = Country("GB-SCT", "Scotland")
  val Wales = Country("GB-WLS", "Wales")
  val Cymru = Country("GB-CYM", "Cymru")
  val NorthernIreland = Country("GB-NIR", "Northern Ireland")

  private val all = List(UK, GB, GG, IM, JE, England, Scotland, Wales, Cymru, NorthernIreland)

  def find(code: String): Option[Country] = all.find(_.code == code)

  def findByName(name: String): Option[Country] = all.find(_.name == name)

  // TODO this is possibly not good enough - should consult a reference HMG-approved list of countries
}

/**
  * Address typically represents a postal address.
  * For UK addresses, 'town' will always be present.
  * For non-UK addresses, 'town' may be absent and there may be an extra line instead.
  */
case class Address(lines: List[String],
                   town: Option[String],
                   county: Option[String],
                   postcode: String,
                   subdivision: Option[Country],
                   country: Country) {

  def nonEmptyFields: List[String] = lines ::: town.toList ::: county.toList ::: List(postcode)

  /** Gets a conjoined representation, excluding the country. */
  def printable(separator: String): String = nonEmptyFields.mkString(separator)

  /** Gets a single-line representation, excluding the country. */
  def printable: String = printable(", ")

  def line1: String = lines.lift(0).getOrElse("")

  def line2: String = lines.lift(1).getOrElse("")

  def line3: String = lines.lift(2).getOrElse("")

  def line4: String = lines.lift(3).getOrElse("")
}

object Address {
  implicit val addressReadFormat = Json.reads[Address]
  implicit val addressWriteFormat = Json.writes[Address]
}

case class Location(latitude: BigDecimal, longitude: BigDecimal) {
  override def toString: String = latitude + "," + longitude
}

object Location {
  implicit val locationFormat = Json.format[Location]
}

case class AddressRecord(
                          id: String,
                          uprn: Option[Long],
                          address: Address,
                          // ISO639-1 code, e.g. 'en' for English
                          // see https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes
                          language: String,
                          localCustodian: Option[LocalCustodian],
                          location: Option[Seq[BigDecimal]],
                          blpuState: Option[String],
                          logicalState: Option[String],
                          streetClassification: Option[String]) {

  def withoutMetadata: AddressRecord = copy(blpuState = None, logicalState = None, streetClassification = None)

  def locationValue: Option[Location] = location.map(loc => Location(loc.head, loc(1)))
}

object AddressRecord {
  implicit val addressRecordReadFormat = Json.reads[AddressRecord]
  implicit val addressRecordWriteFormat = Json.writes[AddressRecord]
}
