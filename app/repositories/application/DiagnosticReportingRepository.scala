/*
 * Copyright 2016 HM Revenue & Customs
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

import model.Commands
import model.Commands.CreateApplicationRequest
import model.Exceptions.ApplicationNotFound
import play.api.libs.json.{ JsObject, Json }
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait DiagnosticReportingRepository {
  def findByUserId(userId: String): Future[List[JsObject]]
  def findAll(): Future[List[JsObject]]
}

class DiagnosticReportingMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[CreateApplicationRequest, BSONObjectID]("application", mongo,
    Commands.Implicits.createApplicationRequestFormats, ReactiveMongoFormats.objectIdFormats) with DiagnosticReportingRepository {

  def findByUserId(userId: String): Future[List[JsObject]] = {
    val projection = Json.obj("personal-details" -> 0)
    val results = collection.find(Json.obj("userId" -> userId), projection)
      .cursor[JsObject](ReadPreference.primaryPreferred)
      .collect[List]()

    results.map { r =>
      if (r.isEmpty) { throw ApplicationNotFound(userId) }
      else { r }
    }
  }

  def findAll(): Future[List[JsObject]] = {
    val projection = Json.obj("personal-details" -> 0)
    collection.find(Json.obj(), projection)
      .cursor[JsObject](ReadPreference.primaryPreferred)
      .collect[List]()
  }
}
