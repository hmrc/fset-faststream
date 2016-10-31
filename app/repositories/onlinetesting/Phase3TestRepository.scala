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

package repositories.onlinetesting

import common.Phase3TestConcern
import factories.DateTimeFactory
import model.ApplicationStatus
import model.ApplicationStatus.ApplicationStatus
import model.Exceptions.UnexpectedException
import model.OnlineTestCommands.OnlineTestApplication
import model.ProgressStatuses._
import model.persisted.phase3tests.{ LaunchpadTest, Phase3TestGroup }
import play.api.Logger
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, _ }
import repositories.CommonBSONDocuments
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Phase3TestRepository extends OnlineTestRepository with Phase3TestConcern {
  this: ReactiveRepository[_, _] =>

  def getTestGroup(applicationId: String): Future[Option[Phase3TestGroup]]

  def getTestGroupByToken(token: String): Future[Phase3TestGroup]

  def insertOrUpdateTestGroup(applicationId: String, phase3TestGroup: Phase3TestGroup): Future[Unit]
}

class Phase3TestMongoRepository(dateTime: DateTimeFactory)(implicit mongo: () => DB)
  extends ReactiveRepository[Phase3TestGroup, BSONObjectID]("application", mongo,
    model.persisted.phase3tests.Phase3TestGroup.phase3TestGroupFormat, ReactiveMongoFormats.objectIdFormats
  ) with Phase3TestRepository with CommonBSONDocuments {

  override val phaseName = "PHASE3"
  override val thisApplicationStatus: ApplicationStatus = ApplicationStatus.PHASE3_TESTS
  override val dateTimeFactory = dateTime
  // TO DO: expiredTestQuery need to be changed once we tackle the expiry test in phase 3
  override val expiredTestQuery: BSONDocument = BSONDocument()

  override implicit val bsonHandler: BSONHandler[BSONDocument, Phase3TestGroup] = Phase3TestGroup.bsonHandler

  override def nextApplicationsReadyForOnlineTesting: Future[List[OnlineTestApplication]] = {
    val query = BSONDocument("applicationStatus" -> ApplicationStatus.PHASE2_TESTS_PASSED)

    implicit val reader = bsonReader(repositories.bsonDocToOnlineTestApplication)
    selectRandom[OnlineTestApplication](query, 5)
  }

  override def insertOrUpdateTestGroup(applicationId: String, phase3TestGroup: Phase3TestGroup): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val appStatusBSON = BSONDocument("$set" -> applicationStatusBSON(PHASE3_TESTS_INVITED)
    ) ++ BSONDocument("$set" -> BSONDocument(s"testGroups.$phaseName" -> phase3TestGroup)
    )

    collection.update(query, appStatusBSON, upsert = false) map { status =>
      if (status.n != 1) {
        val msg = s"${status.n} rows affected when inserting or updating instead of 1! (App Id: $applicationId)"
        Logger.warn(msg)
        throw UnexpectedException(msg)
      }
      ()
    }
  }

  override def getTestGroup(applicationId: String): Future[Option[Phase3TestGroup]] = {
    getTestGroup(applicationId, phaseName)
  }

  override def getTestGroupByToken(token: String): Future[Phase3TestGroup] = {
    getTestProfileByToken(token, phaseName)
  }
}
