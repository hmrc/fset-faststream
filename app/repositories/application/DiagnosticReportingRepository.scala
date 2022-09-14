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

package repositories.application

import akka.stream.scaladsl.Source
import model.Exceptions.ApplicationNotFound

import javax.inject.{Inject, Singleton}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Projections
import play.api.libs.json.{JsValue, Json}
import repositories.CollectionNames
import uk.gov.hmrc.mongo.MongoComponent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait DiagnosticReportingRepository {
  def findByApplicationId(applicationId: String): Future[Seq[JsValue]]
  def findAll: Source[JsValue, _]
}

@Singleton
class DiagnosticReportingMongoRepository @Inject() (mongo: MongoComponent) extends DiagnosticReportingRepository {

  val collection: MongoCollection[Document] = mongo.database.getCollection(CollectionNames.APPLICATION)

  private val defaultExclusions = Projections.exclude(
    "_id",
    "personal-details"
  )

  private val largeFields = Projections.exclude(
    "testGroups.PHASE1.tests.reportLinkURL",
    "testGroups.PHASE1.tests.testUrl",
    "testGroups.PHASE2.tests.reportLinkURL",
    "testGroups.PHASE2.tests.testUrl",
    "testGroups.PHASE3.tests.callbacks.viewBrandedVideo",
    "testGroups.PHASE3.tests.callbacks.setupProcess",
    "testGroups.PHASE3.tests.callbacks.viewPracticeQuestion",
    "testGroups.PHASE3.tests.callbacks.question",
    "testGroups.PHASE3.tests.callbacks.finalCallback",
    "testGroups.PHASE3.tests.callbacks.finished",
    "testGroups.PHASE3.tests.callbacks.reviewed.reviews"
  )

  override def findByApplicationId(appId: String): Future[Seq[JsValue]] = {
    val projection = defaultExclusions
    val result = collection.find(Document("applicationId" -> appId)).projection(projection).toFuture()

    result.map { list =>
      if (list.isEmpty) {
        throw ApplicationNotFound(appId) }
      else {
        list.map ( doc => Json.parse(doc.toJson()) )
      }
    }
  }

  override def findAll: Source[JsValue, _] = {
    // Combine the 2 projections
    val projection = Projections.fields(defaultExclusions, largeFields)

    Source.fromPublisher {
      collection.find().projection(projection).transform((doc: Document) => Json.parse(doc.toJson()),
        (e: Throwable) => throw e
      )
    }
  }
}
