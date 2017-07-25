package connectors

import connectors.exchange.referencedata.{ ReferenceData, Scheme }
import play.api.libs.json.OFormat
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.ws.WSHttp
import play.api.http.Status.OK

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

trait ReferenceDataClient {
  val http: WSHttp

  import config.FrontendAppConfig.faststreamConfig._
  val apiBaseUrl = s"${url.host}${url.base}"

  private val referenceDataCache = TrieMap[String, Any]()

  def allSchemes()(implicit hc: HeaderCarrier): Future[List[Scheme]] = getReferenceDataAsList[Scheme]("schemes", "/reference/schemes")

  private def getReferenceDataAsList[T](
    key: String,
    endpointPath: String)(implicit hc: HeaderCarrier, jsonFormat: OFormat[T]): Future[List[T]] = {
    val values: List[T] = referenceDataCache.getOrElse(key, List.empty[T]).asInstanceOf[List[T]]
    if (values.isEmpty) {
      http.GET(s"$apiBaseUrl$endpointPath").map { response =>
        if (response.status == OK) {
          val dataResponse = response.json.as[List[T]]
          referenceDataCache.update(key, dataResponse)
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
