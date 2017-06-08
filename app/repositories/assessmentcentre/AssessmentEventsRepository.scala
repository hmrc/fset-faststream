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

package repositories.assessmentcentre

import model.persisted.assessmentcentre.{ Event, Location }
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, BSONObjectID }
import repositories.CollectionNames
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Created by fayimora on 08/06/2017.
  */
trait AssessmentEventsRepository {
  def save(event: Event): Future[Unit]
}


class AssessmentEventsMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[Location, BSONObjectID](CollectionNames.ASSESSMENT_EVENTS,
    mongo, Location.locationFormat, ReactiveMongoFormats.objectIdFormats)
  with AssessmentEventsRepository {

  override def save(event: Event): Future[Unit] = {
    collection.insert(event).map(_ => ())
  }
}
