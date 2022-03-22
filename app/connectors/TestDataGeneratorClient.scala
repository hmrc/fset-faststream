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

import javax.inject.Singleton
import play.api.http.Status.{BAD_REQUEST, NOT_FOUND, OK}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.http.HttpReads.Implicits._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TestDataGeneratorClient(config: FrontendAppConfig, http: CSRHttp)(implicit val ec: ExecutionContext) {

  val url = config.faststreamBackendConfig.url

  @deprecated("Call the TDG using admin frontend, it is better supported")
  def getTestDataGenerator(path: String, queryParams: Map[String, String])(implicit hc: HeaderCarrier): Future[String] = {
    import TestDataGeneratorClient._
    val queryParamString = queryParams.toList.map { item => s"${item._1}=${item._2}" }.mkString("&")
    http.GET[HttpResponse](s"${url.host}${url.base}/test-data-generator/$path?$queryParamString").map { response =>
      response.status match {
        case OK => response.body
        case NOT_FOUND => throw new TestDataGeneratorException(
          s"There is no such test data generation endpoint $path. Original message: [${response.body}]")
        case BAD_REQUEST => throw new TestDataGeneratorException(
          s"There are errors in your request $path. Original message: [${response.body}]")
        case _ => throw new TestDataGeneratorException(
          s"There was an error during test data generation $path. Original message: [${response.body}]")
      }
    }
  }

}

object TestDataGeneratorClient {
  sealed class TestDataGeneratorException(message: String) extends Exception(message)
}
