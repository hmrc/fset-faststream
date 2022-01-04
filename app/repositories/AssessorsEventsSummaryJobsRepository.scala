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

package repositories

import javax.inject.{ Inject, Singleton }
import model.AssessorNewEventsJobInfo
import play.api.libs.json.JsObject
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.bson.{ BSONDocument, BSONObjectID }
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AssessorsEventsSummaryJobsRepository {
  def save(info: AssessorNewEventsJobInfo): Future[Unit]
  def lastRun: Future[Option[AssessorNewEventsJobInfo]]
}

@Singleton
class AssessorsEventsSummaryJobsMongoRepository @Inject() (mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[AssessorNewEventsJobInfo, BSONObjectID](
    CollectionNames.ASSESSOR_EVENTS_SUMMARY_JOBS,
    mongoComponent.mongoConnector.db,
    AssessorNewEventsJobInfo.format,
    ReactiveMongoFormats.objectIdFormats
  ) with AssessorsEventsSummaryJobsRepository {

  override def save(info: AssessorNewEventsJobInfo): Future[Unit] = {
    collection.update(ordered = false).one(BSONDocument.empty, info, upsert = true).map(_ => ())
  }

  override def lastRun: Future[Option[AssessorNewEventsJobInfo]] = {
    collection.find(BSONDocument.empty, projection = Option.empty[JsObject]).one[AssessorNewEventsJobInfo]
  }
}
