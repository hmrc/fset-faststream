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

import config.{ CSRHttp, FrontendAppConfig }
import connectors.exchange.referencedata.{ ReferenceData, Scheme, SchemeId }

import javax.inject.{ Inject, Singleton }
import play.api.Logging
import play.api.libs.json.OFormat

import scala.concurrent.ExecutionContext
import play.api.http.Status.OK

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

@Singleton
class ReferenceDataClient @Inject() (config: FrontendAppConfig, http: CSRHttp)(implicit ec: ExecutionContext) extends Logging {
  val url = config.faststreamBackendConfig.url
  val apiBaseUrl = s"${url.host}${url.base}"

  private val referenceDataCache = TrieMap[String, Any]()

  def allSchemes()(implicit hc: HeaderCarrier): Future[List[Scheme]] = {
    val data = getReferenceDataAsList[Scheme]("schemes", "/reference/schemes")
    // Filter out GCFS for 2021 campaign
    data.map { allSchemes => allSchemes.filterNot(s => s.id == SchemeId("GovernmentCommunicationService")) }
  }

  private def getReferenceDataAsList[T](
    key: String,
    endpointPath: String)(implicit hc: HeaderCarrier, jsonFormat: OFormat[T]): Future[List[T]] = {
    val values: List[T] = referenceDataCache.getOrElse(key, List.empty[T]).asInstanceOf[List[T]]

    logger.info(s"Values successfully fetched from reference data cache for key: $key = $values")
    if (values.isEmpty) {
      http.GET(s"$apiBaseUrl$endpointPath").map { response =>
        if (response.status == OK) {
          val dataResponse = response.json.as[List[T]]
          referenceDataCache.update(key, dataResponse)
          // Set to warn so we see it in live logs
          logger.warn(s"Reference data cache is empty for key: $key. It will be populated with the following data: $dataResponse")
          dataResponse
        } else {
          throw new Exception(s"Error retrieving $key for")
        }
      }
    } else {
      Future.successful(values)
    }
  }

  private def getReferenceDataTyped[T](
    key: String,
    endpointPath: String
  )(implicit hc: HeaderCarrier, jsonFormat: OFormat[T]): Future[ReferenceData[T]] = {
    referenceDataCache.get(key).map(_.asInstanceOf[ReferenceData[T]]) match {
      case None =>
        http.GET(s"$apiBaseUrl$endpointPath").map { response =>
          if (response.status == OK) {
            val dataResponse = response.json.as[ReferenceData[T]]
            referenceDataCache.update(key, dataResponse)
            dataResponse
          } else {
            throw new Exception(s"Error retrieving $key for")
          }
        }
      case Some(referenceData) => Future.successful(referenceData)
    }
  }
}
