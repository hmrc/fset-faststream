/*
 * Copyright 2018 HM Revenue & Customs
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

import model.AssessorNewEventsJobInfo
import model.Exceptions.LastRunInfoNotFound
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, BSONObjectID }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AssessorsEventsSummaryJobsRepository {
  def save(info: AssessorNewEventsJobInfo): Future[Unit]
  def lastRun: Future[Option[AssessorNewEventsJobInfo]]
}

class AssessorsEventsSummaryJobsMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[AssessorNewEventsJobInfo, BSONObjectID](CollectionNames.ASSESSOR_EVENTS_SUMMARY_JOBS,
    mongo, AssessorNewEventsJobInfo.format, ReactiveMongoFormats.objectIdFormats) with AssessorsEventsSummaryJobsRepository {

  def save(info: AssessorNewEventsJobInfo): Future[Unit] = {
    collection.update(BSONDocument.empty, info, upsert = true).map(_ => ())
  }

  def lastRun: Future[Option[AssessorNewEventsJobInfo]] = {
    collection.find(BSONDocument.empty).one[AssessorNewEventsJobInfo]
  }
}
