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

package models

import play.api.mvc.{ Cookie, RequestHeader }

object CookiePolicy {
  private val cookieName = "cookies-banner-shown"
  private val tenYears = 315360000
  private val twentyYears = tenYears * 2
  val bannerSeenCookie = Cookie(cookieName, "true", Some(twentyYears))

  def bannerSeen(requestHeader: RequestHeader): Boolean =
    requestHeader.cookies.exists(_.name == cookieName)
}
