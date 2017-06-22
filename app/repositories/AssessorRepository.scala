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

package repositories

import model.persisted.Assessor
import play.api.libs.json.Json
import reactivemongo.api.DB
import reactivemongo.bson._
import services.assessoravailability.AssessorService
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AssessorRepository {

  def find(userId: String): Future[Option[Assessor]]

  def save(settings: Assessor): Future[Unit]

  def countSubmittedAvailability: Future[Int]
}

class AssessorMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[Assessor, BSONObjectID](CollectionNames.ASSESSOR, mongo,
    Assessor.persistedAssessorFormat,
    ReactiveMongoFormats.objectIdFormats) with AssessorRepository with ReactiveRepositoryHelpers {

  override def find(userId: String): Future[Option[Assessor]] = {
    val query = BSONDocument(
      "userId" -> userId
    )

    collection.find(query).one[BSONDocument].map { docOpt =>
      docOpt.map { doc =>
        assessorHandler.read(doc)
      }
    }
  }

  override def save(assessor: Assessor): Future[Unit] = {
    val query = BSONDocument("userId" -> assessor.userId)
    val saveBson: BSONDocument = BSONDocument("$set" -> assessor)
    val insertIfNoRecordFound = true

    val assessorValidator = singleUpdateValidator(assessor.userId, actionDesc = "saveAssessor")
    collection.update(query, saveBson, upsert = insertIfNoRecordFound) map assessorValidator
  }

  override def countSubmittedAvailability: Future[Int] = {
    AssessorService.locations.map { locations =>
      locations.map { location =>
        s"availability.${location.name}" -> Json.toJsFieldJsValueWrapper(Json.obj("$exists" -> true))
      }
    }.flatMap { fields =>
      val query = Json.obj(fields.toSeq: _*)
      collection.count(Some(query))
    }
  }
}
