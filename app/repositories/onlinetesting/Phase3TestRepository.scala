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

import factories.DateTimeFactory
import model.ApplicationStatus.ApplicationStatus
import model.Exceptions.UnexpectedException
import org.joda.time.DateTime
import model.OnlineTestCommands.{ Phase1Test, Phase1TestProfile }
import model.persisted.{ ExpiringOnlineTest, NotificationExpiringOnlineTest, Phase1TestProfileWithAppId, TestResult }
import model.ProgressStatuses.{ PHASE1_TESTS_INVITED, _ }
import model.persisted.phase3tests.Phase3TestGroup
import model.{ ApplicationStatus, ProgressStatuses, ReminderNotice }
import play.api.Logger
import reactivemongo.api.DB
import reactivemongo.bson._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Phase3TestRepository extends OnlineTestRepository[Phase3TestGroup] {
  this: ReactiveRepository[_, _] =>

  def getTestGroup(applicationId: String): Future[Option[Phase3TestGroup]]

  def getTestGroupByToken(token: String): Future[Phase3TestGroup]
}

class Phase1TestMongoRepository(dateTime: DateTimeFactory)(implicit mongo: () => DB)
  extends ReactiveRepository[Phase3TestGroup, BSONObjectID]("application", mongo,
    model.persisted.phase3tests.Phase3TestGroup.phase3TestGroupFormat, ReactiveMongoFormats.objectIdFormats
  ) with Phase3TestRepository {

  val phaseName = "PHASE3"
  val thisApplicationStatus: ApplicationStatus = ApplicationStatus.PHASE3_TESTS
  val dateTimeFactory = dateTime

  override implicit val bsonHandler: BSONHandler[BSONDocument, Phase3TestGroup] = Phase3TestGroup.bsonHandler

  override def getTestGroup(applicationId: String): Future[Option[Phase3TestGroup]] = {
    getTestGroup(applicationId, phaseName)
  }

  override def getTestGroupByToken(token: String): Future[Phase3TestGroup] = {
    getTestProfileByToken(token, phaseName)
  }
}
