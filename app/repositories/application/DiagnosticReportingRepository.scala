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

package repositories.application

import model.{ Commands, CreateApplicationRequest }
import model.Exceptions.ApplicationNotFound
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.{ JsObject, JsValue, Json }
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories.CollectionNames
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait DiagnosticReportingRepository {
  def findByApplicationId(userId: String): Future[List[JsObject]]
  def findAll(): Enumerator[JsValue]
}

class DiagnosticReportingMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[CreateApplicationRequest, BSONObjectID](CollectionNames.APPLICATION, mongo,
    CreateApplicationRequest.createApplicationRequestFormat, ReactiveMongoFormats.objectIdFormats) with DiagnosticReportingRepository {

  private val defaultExclusions = Json.obj(
    "_id" -> 0,
    "personal-details" -> 0)  // these reports should not export personally identifiable data

  private val largeFields = Json.obj(
    "testGroups.PHASE1.tests.reportLinkURL" -> 0,
    "testGroups.PHASE1.tests.testUrl" -> 0,
    "testGroups.PHASE2.tests.reportLinkURL" -> 0,
    "testGroups.PHASE2.tests.testUrl" -> 0,
    "testGroups.PHASE3.tests.callbacks.viewBrandedVideo" -> 0,
    "testGroups.PHASE3.tests.callbacks.setupProcess" -> 0,
    "testGroups.PHASE3.tests.callbacks.viewPracticeQuestion" -> 0,
    "testGroups.PHASE3.tests.callbacks.question" -> 0,
    "testGroups.PHASE3.tests.callbacks.finalCallback" -> 0,
    "testGroups.PHASE3.tests.callbacks.finished" -> 0,
    "testGroups.PHASE3.tests.callbacks.reviewed.reviews" -> 0
  )

  def findByApplicationId(userId: String): Future[List[JsObject]] = {
    val projection = defaultExclusions

    val results = collection.find(Json.obj("applicationId" -> userId), projection)
      .cursor[JsObject](ReadPreference.primaryPreferred)
      .collect[List]()

    results.map { r =>
      if (r.isEmpty) { throw ApplicationNotFound(userId) }
      else { r }
    }
  }

  def findAll(): Enumerator[JsValue] = {
    val projection = defaultExclusions ++ largeFields
    collection.find(Json.obj(), projection)
      .cursor[JsValue](ReadPreference.primaryPreferred)
      .enumerate()
  }
}
