/*
 * Copyright 2023 HM Revenue & Customs
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

package controllers.upscan

import play.api.libs.json._

import java.net.URL
import scala.util.Try

object HttpUrlFormat {

  given format: Format[URL] = new Format[URL] {

    override def reads(json: JsValue): JsResult[URL] =
      json
        .validate[String]
        .flatMap(parseUrl(_).fold(invalidUrlError)(JsSuccess(_)))

    override def writes(o: URL): JsValue =
      JsString(o.toString)

    private def parseUrl(s: String): Option[URL] =
      Try(URL(s)).toOption

    private def invalidUrlError: JsError =
      JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.url"))))
  }
}
