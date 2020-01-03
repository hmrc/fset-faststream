/*
 * Copyright 2020 HM Revenue & Customs
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

package repositories.onlinetesting

import common.Phase1TestConcern2
import factories.DateTimeFactory
import model.ApplicationStatus.ApplicationStatus
import model.ProgressStatuses._
import model._
import model.persisted.Phase1TestProfile2
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, _ }
import repositories.CollectionNames
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.Future

//TODO: can delete this
trait MyPhase1TestRepository extends MyOnlineTestRepository with Phase1TestConcern2 {
  // Use the self type to make all members of ReactiveRepository class available to this trait
  this: ReactiveRepository[_, _] =>

  def getTestGroup(applicationId: String): Future[Option[Phase1TestProfile2]]
}

class MyPhase1TestMongoRepository(dateTime: DateTimeFactory)(implicit mongo: () => DB)
  extends ReactiveRepository[Phase1TestProfile2, BSONObjectID](CollectionNames.APPLICATION, mongo,
    Phase1TestProfile2.phase1TestProfile2Format, ReactiveMongoFormats.objectIdFormats
  ) with MyPhase1TestRepository {

  override val phaseName = "PHASE1"
  override val thisApplicationStatus: ApplicationStatus = ApplicationStatus.PHASE1_TESTS
  override val resetStatuses = List[String](ApplicationStatus.PHASE1_TESTS, ApplicationStatus.PHASE1_TESTS_FAILED)
  override val dateTimeFactory = dateTime
  override val expiredTestQuery: BSONDocument = {
    BSONDocument("$and" -> BSONArray(
      BSONDocument(s"progress-status.$PHASE1_TESTS_COMPLETED" -> BSONDocument("$ne" -> true)),
      BSONDocument(s"progress-status.$PHASE1_TESTS_EXPIRED" -> BSONDocument("$ne" -> true))
    ))
  }

  override implicit val bsonHandler: BSONHandler[BSONDocument, Phase1TestProfile2] = Phase1TestProfile2.bsonHandler

  override def getTestGroup(applicationId: String): Future[Option[Phase1TestProfile2]] = {
    getTestGroup(applicationId, phaseName)
  }
}
