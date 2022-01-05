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

package controllers

import connectors.addresslookup.{Address, AddressLookupClient, AddressRecord, Country}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.http.Status.{BAD_REQUEST, NOT_FOUND, OK}
import play.api.libs.json.Json
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, status}
import testkit.MockitoImplicits._
import testkit.TestableSecureActions
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}

import scala.concurrent.Future

class AddressLookupControllerSpec extends BaseControllerSpec {

  "Address lookup controller address lookup by postcode" should {
    "return a list of addresses when addresses are found" in new TestFixture {
      val postcode = "AB99 9AB"
      val address1 = Address(lines = List("Address1 line1"), town = None, county = None, postcode, subdivision = None, Country("GB", "GB"))
      val address2 = Address(lines = List("Address2 line1"), town = None, county = None, postcode, subdivision = None, Country("GB", "GB"))

      val addresses = List(
        AddressRecord(id = "1000", uprn = Some(1000), address1, language = "", localCustodian = None, location = None,
          administrativeArea = None, poBox = None),
        AddressRecord(id = "1001", uprn = Some(1001), address2, language = "", localCustodian = None, location = None,
          administrativeArea = None, poBox = None)
      )

      when(mockAddressLookupClient.findByPostcode(any[String], any[Option[String]])(any[HeaderCarrier])).thenReturnAsync(addresses)

      val response = controller.addressLookupByPostcode(postcode)(fakeRequest)
      status(response) mustBe OK
      val data = Json.fromJson[List[AddressRecord]](Json.parse(contentAsString(response))).get
      data mustBe addresses
    }

    "return NOT_FOUND if no addresses are found" in new TestFixture {
      when(mockAddressLookupClient.findByPostcode(any[String], any[Option[String]])(any[HeaderCarrier])).thenReturnAsync(Nil)

      val response = controller.addressLookupByPostcode("AB99 9AA")(fakeRequest)
      status(response) mustBe NOT_FOUND
    }

    "return BAD_REQUEST if the postcode generates a BadRequestException" in new TestFixture {
      when(mockAddressLookupClient.findByPostcode(any[String], any[Option[String]])(any[HeaderCarrier]))
        .thenReturn(Future.failed(new BadRequestException("Invalid postcode")))

      val response = controller.addressLookupByPostcode("SW1")(fakeRequest)
      status(response) mustBe BAD_REQUEST
    }
  }

  trait TestFixture extends BaseControllerTestFixture {
    val mockAddressLookupClient = mock[AddressLookupClient]

    val controller = new AddressLookupController(
      mockConfig, stubMcc, mockSecurityEnv, mockSilhouetteComponent, mockNotificationTypeHelper, mockAddressLookupClient
    ) with TestableSecureActions
  }
}
