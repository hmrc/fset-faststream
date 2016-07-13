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

package models

import com.mohiva.play.silhouette.api.Identity
import config.CSRCache
import play.api.libs.json._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

/**
 * A model for the user. This should represent the logged in user, so it should contain information the user itself,
 * roles and status of the user.
 */
case class SecurityUser(userID: String) extends Identity

case class CachedUser(
  userID: UniqueIdentifier,
  firstName: String,
  lastName: String,
  preferredName: Option[String],
  email: String,
  isActive: Boolean,
  lockStatus: String
)

object CachedUser {
  implicit val userJsonFormats = Json.format[CachedUser]
}

case class CachedData(
  user: CachedUser,
  application: Option[ApplicationData]
)

case class CachedDataWithApp(
  user: CachedUser,
  application: ApplicationData
)

object CachedData {
  implicit val dataJsonFormats = Json.format[CachedData]
}

object SecurityUser {

  implicit class loginInfoToCachedUser(securityUser: SecurityUser) {
    def toUserFuture(implicit hc: HeaderCarrier): Future[Option[models.CachedData]] =
      CSRCache.fetchAndGetEntry[CachedData](securityUser.userID)
  }

}
