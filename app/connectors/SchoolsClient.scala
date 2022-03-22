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

package connectors

import config.{CSRHttp, FrontendAppConfig}
import connectors.SchoolsClient.SchoolsNotFound
import connectors.exchange.School
import play.api.http.Status._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class SchoolsClient @Inject() (config: FrontendAppConfig, http: CSRHttp)(implicit ec: ExecutionContext) {

  val url = config.faststreamBackendConfig.url

  def getSchools(term: String)(implicit hc: HeaderCarrier) = {
    http.GET[List[School]](
      s"${url.host}${url.base}/schools",
      Seq("term" -> term)
    ).recover {
      case e: UpstreamErrorResponse if e.statusCode == NOT_FOUND  => throw new SchoolsNotFound
    }
  }
}

object SchoolsClient {
  sealed class SchoolsNotFound extends Exception
}
