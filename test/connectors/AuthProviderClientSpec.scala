/*
 * Copyright 2020 HM Revenue & Customs
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

import testkit.{ ShortTimeout, UnitSpec }

class AuthProviderClientSpec extends UnitSpec with ShortTimeout {

  "AuthProviderClient getRole" should {
    "return valid roles when passed valid strings" in {
      import AuthProviderClient._
      val validStrings = Map(
        "faststream-team" -> FaststreamTeamRole,
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
        intercept[UserRoleDoesNotExistException] {
          AuthProviderClient.getRole(invalidString)
        }
      }
    }
  }
}
