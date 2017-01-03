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

package repositories.parity

import factories.DateTimeFactory
import model.{ ApplicationStatus, Commands }
import model.Commands.CreateApplicationRequest
import model.persisted.ApplicationReadyForEvaluation
import play.api.libs.json.{ JsValue, Json }
import reactivemongo.api.DB
import reactivemongo.bson._
import repositories._
import repositories.parity.ParityExportRepository.ApplicationIdNotFoundException
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object ParityExportRepository {
  case class ApplicationIdNotFoundException(applicationId: String) extends Exception(applicationId)
}

case class ApplicationReadyForExport(applicationId: String)

object ApplicationReadyForExport {
  implicit val applicationReadyForExportFormat = Json.format[ApplicationReadyForExport]
  implicit val applicationReadyForExportBsonFormat = Macros.handler[ApplicationReadyForExport]
}

trait ParityExportRepository extends RandomSelection with CommonBSONDocuments with ReactiveRepositoryHelpers {
  this: ReactiveRepository[_, _] =>

  def nextApplicationsForExport(batchSize: Int): Future[List[ApplicationReadyForExport]]

  def getApplicationForExport(applicationId: String): Future[JsValue]
}

class ParityExportMongoRepository(dateTime: DateTimeFactory)(implicit mongo: () => DB)
  extends ReactiveRepository[CreateApplicationRequest, BSONObjectID]("application", mongo,
    Commands.Implicits.createApplicationRequestFormat, ReactiveMongoFormats.objectIdFormats
  ) with ParityExportRepository with CommonBSONDocuments {

  override def nextApplicationsForExport(batchSize: Int): Future[List[ApplicationReadyForExport]] = {
    val query = BSONDocument("applicationStatus" -> ApplicationStatus.READY_FOR_EXPORT.toString)

    selectRandom[ApplicationReadyForExport](query, batchSize)
  }

  override def getApplicationForExport(applicationId: String): Future[JsValue] = {
    val query = BSONDocument("applicationId" -> applicationId)

    collection.find(query).one[JsValue].map { resultOpt =>
      resultOpt.getOrElse(throw ApplicationIdNotFoundException(applicationId))
    }
  }
}
