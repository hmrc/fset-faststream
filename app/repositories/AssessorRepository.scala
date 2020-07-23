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

package repositories

import javax.inject.{ Inject, Singleton }
import model.UniqueIdentifier
import model.persisted.assessor.{ Assessor, AssessorStatus }
import model.persisted.eventschedules.Location
import model.persisted.eventschedules.SkillType.SkillType
import org.joda.time.LocalDate
import play.api.libs.json.{ JsObject, Json }
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.api.{ Cursor, ReadPreference }
import reactivemongo.bson._
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

trait AssessorRepository {
  def find(userId: String): Future[Option[Assessor]]
  def findByIds(userIds: Seq[String]): Future[Seq[Assessor]]
  def findAll(readPreference: ReadPreference = ReadPreference.primaryPreferred)(implicit ec: ExecutionContext): Future[List[Assessor]]
  def save(settings: Assessor): Future[Unit]
  def countSubmittedAvailability: Future[Long]
  def findAvailabilitiesForLocationAndDate(location: Location, date: LocalDate, skills: Seq[SkillType]): Future[Seq[Assessor]]
  def findAssessorsForEvent(eventId: String): Future[Seq[Assessor]]
  def findUnavailableAssessors(skills: Seq[SkillType], location: Location, date: LocalDate): Future[Seq[Assessor]]
  def remove(userId: UniqueIdentifier): Future[Unit]
}

@Singleton
class AssessorMongoRepository @Inject() (mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[Assessor, BSONObjectID](
    CollectionNames.ASSESSOR,
    mongoComponent.mongoConnector.db,
    Assessor.persistedAssessorFormat,
    ReactiveMongoFormats.objectIdFormats) with AssessorRepository with ReactiveRepositoryHelpers {

  private val unlimitedMaxDocs = -1

  override def indexes: Seq[Index] = Seq(
    Index(Seq(("userId", Ascending)), unique = true)
  )

  def find(userId: String): Future[Option[Assessor]] = {
    val query = BSONDocument(
      "userId" -> userId
    )

    collection.find(query, projection = Option.empty[JsObject]).one[Assessor]
  }

  def findByIds(userIds: Seq[String]): Future[Seq[Assessor]] = {
    val query = BSONDocument("userId" -> BSONDocument("$in" -> userIds))
    collection.find(query, projection = Option.empty[JsObject]).cursor[Assessor]()
      .collect[Seq](unlimitedMaxDocs, Cursor.FailOnError[Seq[Assessor]]())
  }

  def save(assessor: Assessor): Future[Unit] = {
    require(assessor.availability.isEmpty || assessor.status == AssessorStatus.AVAILABILITIES_SUBMITTED,
      "Can't submit assessor availabilities with new status")
    val query = BSONDocument("userId" -> assessor.userId)
    val saveBson: BSONDocument = BSONDocument("$set" -> assessor)
    val insertIfNoRecordFound = true

    val assessorValidator = singleUpdateValidator(assessor.userId, actionDesc = "saveAssessor")
    collection.update(ordered = false).one(query, saveBson, upsert = insertIfNoRecordFound) map assessorValidator
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

    collection.find(query, projection = Option.empty[JsObject]).cursor[Assessor]()
      .collect[Seq](unlimitedMaxDocs, Cursor.FailOnError[Seq[Assessor]]())
  }

  def findAssessorsForEvent(eventId: String): Future[Seq[Assessor]] = {
    val query = BSONDocument("allocation" -> BSONDocument("$elemMatch" -> BSONDocument(
      "id" -> eventId
    )))

    collection.find(query, projection = Option.empty[JsObject]).cursor[Assessor]()
      .collect[Seq](unlimitedMaxDocs, Cursor.FailOnError[Seq[Assessor]]())
  }

  def countSubmittedAvailability: Future[Long] = {
    val query = Json.obj(Seq("status" -> Json.toJsFieldJsValueWrapper(AssessorStatus.AVAILABILITIES_SUBMITTED.toString)): _*)
    collection.count(Some(query), limit = Some(0), skip = 0, hint = None, readConcern = reactivemongo.api.ReadConcern.Local)
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
    collection.find(query, projection = Option.empty[JsObject]).cursor[Assessor]()
      .collect[Seq](unlimitedMaxDocs, Cursor.FailOnError[Seq[Assessor]]())
  }

  def remove(userId: UniqueIdentifier): Future[Unit] = {
    val validator = singleRemovalValidator(userId.toString, actionDesc = "deleting assessor")
    collection.delete().one(BSONDocument("userId" -> userId)) map validator
  }
}
