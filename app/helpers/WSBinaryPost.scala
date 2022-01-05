/*
 * Copyright 2022 HM Revenue & Customs
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

package helpers

import play.api.libs.ws.DefaultBodyWritables
import uk.gov.hmrc.http.hooks.HttpHooks
import uk.gov.hmrc.http.logging.ConnectionTracing
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, HttpVerb}
import uk.gov.hmrc.play.http.ws.{WSHttpResponse, WSRequest}

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}

// scalastyle:off

trait WSBinaryPost extends HttpBinaryPost with WSRequest {

  override protected def doBinaryPost(url: String, body: Array[Byte])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    buildRequest(url, Nil).post(body)(DefaultBodyWritables.writeableOf_ByteArray).map(WSHttpResponse(_))
  }
}

trait HttpBinaryPost extends HttpVerb with ConnectionTracing with HttpHooks {
  import play.api.http.HttpVerbs.{ POST => POST_VERB }

  protected def doBinaryPost(url: String, body: Array[Byte])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]

  def POSTBinary[O](url: String, body: Array[Byte])(implicit rds: HttpReads[O], hc: HeaderCarrier, ec: ExecutionContext): Future[O] = {
    withTracing(POST_VERB, url) {
      val httpResponse = doBinaryPost(url, body)
      executeHooks(POST_VERB, new URL(url), Nil, None, httpResponse)
      mapErrors(POST_VERB, url, httpResponse).map(rds.read(POST_VERB, url, _))
    }
  }
}
// scalastyle:on
