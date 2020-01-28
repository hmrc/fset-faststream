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

package models

import play.api.libs.json.{ Format, JsString, JsSuccess, JsValue }
import scala.language.implicitConversions

object ApplicationRoute extends Enumeration {
  type ApplicationRoute = Value

  val Faststream, Edip, Sdip, SdipFaststream = Value

  implicit val applicationRouteFormat = new Format[ApplicationRoute] {
    def reads(json: JsValue) = JsSuccess(ApplicationRoute.withName(json.as[String]))
    def writes(routeEnum: ApplicationRoute) = JsString(routeEnum.toString)
  }

  implicit def applicationRouteToStr(appRoute: ApplicationRoute): String = appRoute.toString
}
