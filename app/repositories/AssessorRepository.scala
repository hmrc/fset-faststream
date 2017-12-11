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

package repositories

import model.UniqueIdentifier
import model.persisted.assessor.{ Assessor, AssessorStatus }
import model.persisted.eventschedules.{ Event, Location }
import model.persisted.eventschedules.SkillType.SkillType
import org.joda.time.LocalDate
import play.api.libs.json.Json
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

trait AssessorRepository {
  def find(userId: String): Future[Option[Assessor]]
  def findByIds(userIds: Seq[String]): Future[Seq[Assessor]]
  def findAll(readPreference: ReadPreference = ReadPreference.primaryPreferred)(implicit ec: ExecutionContext): Future[List[Assessor]]
  def save(settings: Assessor): Future[Unit]
  def countSubmittedAvailability: Future[Int]
  def findAvailabilitiesForLocationAndDate(location: Location, date: LocalDate, skills: Seq[SkillType]): Future[Seq[Assessor]]
  def findAssessorsForEvent(eventId: String): Future[Seq[Assessor]]
  def findUnavailableAssessors(skills: Seq[SkillType], location: Location, date: LocalDate): Future[Seq[Assessor]]
  def remove(userId: UniqueIdentifier): Future[Unit]
}

class AssessorMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[Assessor, BSONObjectID](CollectionNames.ASSESSOR, mongo,
    Assessor.persistedAssessorFormat,
    ReactiveMongoFormats.objectIdFormats) with AssessorRepository with ReactiveRepositoryHelpers {

  def find(userId: String): Future[Option[Assessor]] = {
    val query = BSONDocument(
      "userId" -> userId
    )

    collection.find(query).one[Assessor]
  }

  def findByIds(userIds: Seq[String]): Future[Seq[Assessor]] = {
    val query = BSONDocument("userId" -> BSONDocument("$in" -> userIds))
    collection.find(query).cursor[Assessor]().collect[Seq]()
  }

  def save(assessor: Assessor): Future[Unit] = {
    require(assessor.availability.isEmpty || assessor.status == AssessorStatus.AVAILABILITIES_SUBMITTED,
      "Can't submit assessor availabilities with new status")
    val query = BSONDocument("userId" -> assessor.userId)
    val saveBson: BSONDocument = BSONDocument("$set" -> assessor)
    val insertIfNoRecordFound = true

    val assessorValidator = singleUpdateValidator(assessor.userId, actionDesc = "saveAssessor")
    collection.update(query, saveBson, upsert = insertIfNoRecordFound) map assessorValidator
  }

  def findAvailabilitiesForLocationAndDate(location: Location, date: LocalDate, skills: Seq[SkillType]): Future[Seq[Assessor]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("skills" -> BSONDocument("$in" -> skills)),
      BSONDocument("availability" ->
        BSONDocument("$elemMatch" -> BSONDocument(
          "location.name" -> BSONDocument("$in" -> BSONArray(location.name, "Home")),
          "date" -> date
        )))
    ))

    collection.find(query).cursor[Assessor]().collect[Seq]()
  }

  def findAssessorsForEvent(eventId: String): Future[Seq[Assessor]] = {
    val query = BSONDocument("allocation" -> BSONDocument("$elemMatch" -> BSONDocument(
      "id" -> eventId
    )))

    collection.find(query).cursor[Assessor]().collect[Seq]()
  }

  def countSubmittedAvailability: Future[Int] = {
    val query = Json.obj(Seq("status" -> Json.toJsFieldJsValueWrapper(AssessorStatus.AVAILABILITIES_SUBMITTED.toString)): _*)
    collection.count(Some(query))
  }

  def findUnavailableAssessors(skills: Seq[SkillType], location: Location, date: LocalDate): Future[Seq[Assessor]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("skills" -> BSONDocument("$in" -> skills)),
      BSONDocument("availability" ->
        BSONDocument("$not" ->
          BSONDocument("$elemMatch" -> BSONDocument("location" -> location.name, "date" -> date))
        )
      )
    ))
    collection.find(query).cursor[Assessor]().collect[Seq]()
  }

  def remove(userId: UniqueIdentifier): Future[Unit] = {
    val validator = singleRemovalValidator(userId.toString, actionDesc = "deleting assessor")
    collection.remove(BSONDocument("userId" -> userId)) map validator
  }
}
