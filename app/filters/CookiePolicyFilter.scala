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

package filters

import akka.stream.Materializer
import com.google.inject.{Inject, Singleton}
import models.CookiePolicy
import play.api.mvc.{EssentialFilter, Filter, RequestHeader, Result}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CookiePolicyFilter @Inject() (implicit val ec: ExecutionContext, val mat: Materializer)
  extends Filter with EssentialFilter {
  override def apply(next: RequestHeader => Future[Result])(rh: RequestHeader): Future[Result] =
    next(rh).map { result =>
      if (result.header.status == 200 && !CookiePolicy.bannerSeen(rh)) {
        result.withCookies(CookiePolicy.bannerSeenCookie)
      } else {
        result
      }
    }
}
