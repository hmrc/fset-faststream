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

package connectors.exchange

import models.UniqueIdentifier
import play.api.libs.json.{ Json, OFormat }

case class UserResponse(
  firstName: String,
  lastName: String,
  preferredName: Option[String],
  isActive: Boolean,
  userId: UniqueIdentifier,
  email: String,
  disabled: Boolean,
  lockStatus: String,
  roles: List[String],
  service: String,
  phoneNumber: Option[String]
)

object UserResponse {
  implicit val format: OFormat[UserResponse] = Json.format[UserResponse]

  implicit class exchangeUserToCachedUser(exchUser: UserResponse) {
    def toCached: models.CachedUser =
      models.CachedUser(exchUser.userId, exchUser.firstName, exchUser.lastName,
        exchUser.preferredName, exchUser.email, exchUser.isActive, exchUser.lockStatus)
  }
}
