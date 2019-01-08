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

package connectors.addresslookup

import java.net.URLEncoder

import config.CSRHttp

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

/**
  * The following client has been take from taken from https://github.com/hmrc/address-reputation-store. The project has
  * not been added as a dependency, as it brings in many transitive dependencies that are not needed,
  * as well as data cleansing/ingestion and backward compatibility logic that is not needed for this project.
  * If the version 2 api gets deprecated, then these DTOs will have to change.
  * There have been some minor changes made to the code to ensure that it compiles and passes scalastyle,
  * but there is some copied code that is not idiomatic Scala and should be changed at some point in the future
  */

object AddressLookupClient extends AddressLookupClient {
  val http = CSRHttp
  val addressLookupEndpoint = config.FrontendAppConfig.addressLookupConfig.url
}

trait AddressLookupClient {

  val addressLookupEndpoint: String
  val http: CSRHttp

  private def url = s"$addressLookupEndpoint/v2/uk/addresses"

  def findByPostcode(postcode: String, filter: Option[String])(implicit hc: HeaderCarrier): Future[List[AddressRecord]] = {
    val safePostcode = postcode.replaceAll("[^A-Za-z0-9]", "")
    val pq = "?postcode=" + enc(safePostcode)
    val fq = filter.map(fi => "&filter=" + enc(fi)).getOrElse("")
    http.GET[List[AddressRecord]](url + pq + fq)
  }

  def findByAddressId(id: String, filter: Option[String])(implicit hc: HeaderCarrier): Future[AddressRecord] = {
    http.GET[AddressRecord](s"$url/$id")
  }

  private def enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
