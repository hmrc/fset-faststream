/*
 * Copyright 2023 HM Revenue & Customs
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
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.UpdateOptions
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait AssessorsEventsSummaryJobsRepository {
  def save(info: AssessorNewEventsJobInfo): Future[Unit]
  def lastRun: Future[Option[AssessorNewEventsJobInfo]]
}

@Singleton
class AssessorsEventsSummaryJobsMongoRepository @Inject() (mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[AssessorNewEventsJobInfo](
    collectionName = CollectionNames.ASSESSOR_EVENTS_SUMMARY_JOBS,
    mongoComponent = mongoComponent,
    domainFormat = AssessorNewEventsJobInfo.format,
    indexes = Nil
  ) with AssessorsEventsSummaryJobsRepository {

  override def save(info: AssessorNewEventsJobInfo): Future[Unit] = {
    collection.updateOne(
      Document.empty,
      Document("$set" -> Codecs.toBson(info)),
      UpdateOptions().upsert(insertIfNoRecordFound)
    ).toFuture() map(_ => ())
  }

  override def lastRun: Future[Option[AssessorNewEventsJobInfo]] = {
    collection.find(Document.empty).headOption()
  }
}
