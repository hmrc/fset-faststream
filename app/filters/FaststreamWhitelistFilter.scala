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

package filters

import java.util.Base64

import akka.stream.Materializer
import javax.inject.{Inject, Singleton}
import play.api.mvc.Results.{Forbidden, NotImplemented, Redirect}
import play.api.mvc.{Call, EssentialFilter, RequestHeader, Result}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.whitelist.AkamaiWhitelistFilter

import scala.concurrent.Future

// TODO: We need to keep this one, instead of trying to use this one
// https://github.com/hmrc/bootstrap-play/blob/
// master/bootstrap-frontend-play-26/src/main/scala/uk/gov/hmrc/play/bootstrap/frontend/filters/WhitelistFilter.scala
// They are slightly different.
@Singleton
class FaststreamWhitelistFilter @Inject() (
  val configuration: Configuration,
  val mat: Materializer)
  extends AkamaiWhitelistFilter with EssentialFilter {

  // Whitelist Configuration
  private def whitelistConfig(key: String): Seq[String] =
    Some(new String(Base64.getDecoder.decode(configuration.getOptional[String](key).getOrElse("")), "UTF-8"))
      .map(_.split(",")).getOrElse(Array.empty).toSeq

  // List of IP addresses
  override def whitelist: Seq[String] = whitelistConfig("whitelist")

  // List of allowed file upload addresses
  val whitelistFileUpload: Seq[String] = whitelistConfig("whitelistFileUpload")

  // List of prefixes that file uploads happen under
  val fileUploadPathPrefixes = List("/fset-fast-stream/file-submission/")

  // Es. /ping/ping,/admin/details
  override def excludedPaths: Seq[Call] = whitelistConfig("whitelistExcludedCalls").map {
    path => Call("GET", path)
  }

  def destination: Call = Call(
    "GET",
    "https://www.apply-civil-service-fast-stream.service.gov.uk/shutter/fset-faststream/index.html"
  )

  // Modified AkamaiWhitelistFilter (play-whitelist-filter)
  private def isCircularDestination(requestHeader: RequestHeader): Boolean =
    requestHeader.uri == destination.url

  private def toCall(rh: RequestHeader): Call =
    Call(rh.method, rh.uri)

  override def apply(f: (RequestHeader) => Future[Result])(rh: RequestHeader): Future[Result] = {
    Logger.debug(s"----- Calling WhiteListFilter.apply with method ${rh.method} uri ${rh.uri} from ip ${rh.headers.get(trueClient)}")
    if (excludedPaths.contains(toCall(rh))) {
      f(rh)
    } else {
      rh.headers.get(trueClient) map {
        ip =>
          if (fileUploadPathPrefixes.exists(pathPrefix => rh.uri.startsWith(pathPrefix))) {
            if (whitelistFileUpload.contains(ip)) {
              f(rh)
            } else {
              Future.successful(Forbidden)
            }
          }
          else if (whitelist.head == "*" || whitelist.contains(ip)) {
            f(rh)
          } else if (isCircularDestination(rh)) {
            Future.successful(Forbidden)
          } else {
            Future.successful(Redirect(destination))
          }
      } getOrElse Future.successful(NotImplemented)
    }
  }
}
