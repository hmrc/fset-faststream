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

package connectors

import config.WSHttp
import connectors.ExchangeObjects.Implicits._
import connectors.ExchangeObjects._
import mockws.MockWS
import model.Exceptions.ConnectorException
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{Json, _}
import play.api.mvc.Action
import play.api.mvc.Results._
import play.api.test.Helpers._
import testkit.ShortTimeout
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse, _}

import scala.concurrent.Future

class AuthProviderClientSpec extends PlaySpec with MockitoSugar with ScalaFutures with ShortTimeout {

  "AuthProviderClient getRole" should {
    "return valid roles when passed valid strings" in {
      import AuthProviderClient._
      val validStrings = Map(
        "fasttrack-team" -> FaststreamTeamRole,
        "service-support" -> ServiceSupportRole,
        "service-admin" -> ServiceAdminRole,
        "super-admin" -> SuperAdminRole,
        "tech-admin" -> TechnicalAdminRole
      )

      validStrings.foreach { case (validString, expectedRole) =>
        AuthProviderClient.getRole(validString) must be(expectedRole)
      }
    }

    "throw an exception when passed invalid strings" in {
      val invalidStrings = List(
        "",
        "someText"
      )

      invalidStrings.foreach { invalidString =>
        import AuthProviderClient._
        intercept[UserRoleDoesNotExist] {
          AuthProviderClient.getRole(invalidString)
        }
      }
    }
  }

}