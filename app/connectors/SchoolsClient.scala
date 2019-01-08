/*
 * Copyright 2019 HM Revenue & Customs
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

import config.CSRHttp
import connectors.SchoolsClient.SchoolsNotFound
import connectors.exchange.School

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.{ HeaderCarrier, NotFoundException }

trait SchoolsClient {

  val http: CSRHttp

  import config.FrontendAppConfig.faststreamConfig._

  def getSchools(term: String)(implicit hc: HeaderCarrier) = {
    http.GET(
      s"${url.host}${url.base}/schools",
      Seq("term" -> term)
    ).map(
      httpResponse => httpResponse.json.as[List[School]]
    ).recover {
      case e: NotFoundException => throw new SchoolsNotFound
    }
  }
}

object SchoolsClient extends SchoolsClient {

  override val http: CSRHttp = CSRHttp

  sealed class SchoolsNotFound extends Exception

}
