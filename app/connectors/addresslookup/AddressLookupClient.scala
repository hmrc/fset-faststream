/*
 * Copyright 2021 HM Revenue & Customs
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
import config.{CSRHttp, FrontendAppConfig}
import play.api.Logging
import play.api.http.Status.{BAD_REQUEST, NOT_FOUND}
import play.api.libs.json.{Json, Writes}

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.http.HttpReads.Implicits._

import scala.concurrent.{ExecutionContext, Future}

/**
 * The following client has been take from taken from https://github.com/hmrc/address-reputation-store. The project has
 * not been added as a dependency, as it brings in many transitive dependencies that are not needed,
 * as well as data cleansing/ingestion and backward compatibility logic that is not needed for this project.
 * If the version 2 api gets deprecated, then these DTOs will have to change.
 * There have been some minor changes made to the code to ensure that it compiles and passes scalastyle,
 * but there is some copied code that is not idiomatic Scala and should be changed at some point in the future
 */

@Singleton
class AddressLookupClient @Inject() (config: FrontendAppConfig, http: CSRHttp)(implicit val ec: ExecutionContext) extends Logging {

  val addressLookupEndpoint = config.addressLookupConfig.url

  def findByPostcode(postcode: String, filter: Option[String])(implicit hc: HeaderCarrier): Future[List[AddressRecord]] = {
    val safePostcode = postcode.replaceAll("[^A-Za-z0-9]", "")
    val request = LookupAddressByPostcodeRequest(enc(safePostcode), filter.map( f => enc(f)) )
    http.POST[LookupAddressByPostcodeRequest, List[AddressRecord]](s"$addressLookupEndpoint/lookup", request).recover {
      case e: UpstreamErrorResponse if UpstreamErrorResponse.WithStatusCode.unapply(e).contains(NOT_FOUND) =>
        logger.debug(s"AddressLookupClient received NOT_FOUND for postcode: $postcode. Error message: ${e.getMessage()}")
        Nil
      case e: UpstreamErrorResponse if UpstreamErrorResponse.WithStatusCode.unapply(e).contains(BAD_REQUEST) =>
        logger.debug(s"AddressLookupClient received BAD_REQUEST for postcode: $postcode. Error message: ${e.getMessage()}")
        throw new BadRequestException(e.getMessage())
    }
  }

  def findByUprn(uprn: String)(implicit hc: HeaderCarrier): Future[AddressRecord] = {
    http.POST[LookupAddressByUprnRequest, List[AddressRecord]](
      s"$addressLookupEndpoint/lookup/by-uprn",
      LookupAddressByUprnRequest(uprn)
    ).map( _.head )
  }

  private def enc(s: String) = URLEncoder.encode(s, "UTF-8")

  case class LookupAddressByPostcodeRequest(postcode: String, filter: Option[String])

  object LookupAddressByPostcodeRequest {
    implicit val writes: Writes[LookupAddressByPostcodeRequest] = Json.writes[LookupAddressByPostcodeRequest]
  }

  case class LookupAddressByUprnRequest(uprn: String)

  object LookupAddressByUprnRequest {
    implicit val writes: Writes[LookupAddressByUprnRequest] = Json.writes[LookupAddressByUprnRequest]
  }
}
