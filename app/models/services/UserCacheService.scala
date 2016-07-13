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

package models.services

import com.mohiva.play.silhouette.api.LoginInfo
import config.CSRCache
import models.{ CachedData, SecurityUser }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserCacheService extends UserService {

  override def retrieve(loginInfo: LoginInfo): Future[Option[SecurityUser]] =
    Future.successful(Some(SecurityUser(userID = loginInfo.providerKey)))

  override def save(user: CachedData)(implicit hc: HeaderCarrier): Future[CachedData] =
    CSRCache.cache[CachedData](user.user.userID.toString, user).map(_ => user)

}
