/*
 * Copyright 2025 HM Revenue & Customs
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

package repositories.upscan

import model.exchange.upscan.{Reference, UploadDetails, UploadId, UploadStatuses}
import org.bson.types.ObjectId
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model.{FindOneAndUpdateOptions, IndexModel, IndexOptions, Indexes}
import org.mongodb.scala.{Document, SingleObservableFuture}
import play.api.libs.functional.syntax.*
import play.api.libs.json.*
import repositories.CollectionNames
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

object UpscanRepository {

  private given Format[UploadId] =
    Format.at[String](__ \ "value")
      .inmap[UploadId](UploadId.apply, _.value)

  private given Format[Reference] =
    Format.at[String](__ \ "value")
      .inmap[Reference](Reference.apply, _.value)

  private[upscan] val mongoFormat: Format[UploadDetails] =
    given Format[ObjectId] = MongoFormats.objectIdFormat

    ((__ \ "_id").format[ObjectId]
      ~ (__ \ "applicationId").format[String]
      ~ (__ \ "uploadId").format[UploadId]
      ~ (__ \ "reference").format[Reference]
      ~ (__ \ "status").format[UploadStatuses.UploadStatus]
      )(UploadDetails.apply, Tuple.fromProductTyped _)
}

@Singleton
class UpscanRepository @Inject()(
                                       mongoComponent: MongoComponent
                                     )(using
                                       ExecutionContext
                                     ) extends PlayMongoRepository[UploadDetails](
  collectionName = CollectionNames.UPSCAN,
  mongoComponent = mongoComponent,
  domainFormat   = UpscanRepository.mongoFormat,
  indexes        = Seq(
    IndexModel(Indexes.ascending("uploadId"), IndexOptions().unique(true)),
    IndexModel(Indexes.ascending("reference"), IndexOptions().unique(true))
  )
) {

  import UpscanRepository.given

  def deleteAllData(): Future[Unit] = {
    val query = Document.empty
    collection.deleteMany(query)
      .toFuture()
      .map(_ => ())
  }

  def insert(details: UploadDetails): Future[Unit] =
    collection.insertOne(details)
      .toFuture()
      .map(_ => ())

  def findByUploadId(uploadId: UploadId): Future[Option[UploadDetails]] = {
    collection.find(equal("uploadId", Codecs.toBson(uploadId))).headOption()
  }

  def findByFileReference(fileReference: Reference): Future[Option[UploadDetails]] = {
    collection.find(equal("reference", Codecs.toBson(fileReference))).headOption()
  }

  def updateStatus(reference: Reference, newStatus: UploadStatuses.UploadStatus): Future[UploadStatuses.UploadStatus] = {
    collection
      .findOneAndUpdate(
        filter = equal("reference", Codecs.toBson(reference)),
        update = set("status", Codecs.toBson(newStatus)),
        options = FindOneAndUpdateOptions().upsert(true)
      )
      .toFuture()
      .map(_.status)
  }
}
