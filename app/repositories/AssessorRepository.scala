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

import javax.inject.{Inject, Singleton}
import model.UniqueIdentifier
import model.persisted.assessor.{Assessor, AssessorStatus}
import model.persisted.eventschedules.Location
import model.persisted.eventschedules.SkillType.SkillType
import org.joda.time.LocalDate
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.{IndexModel, IndexOptions, UpdateOptions}
import org.mongodb.scala.model.Indexes.ascending
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

trait AssessorRepository {
  def find(userId: String): Future[Option[Assessor]]
  def findByIds(userIds: Seq[String]): Future[Seq[Assessor]]
  //TODO: mongo fix - only used in test class
//  def findAll(readPreference: ReadPreference = ReadPreference.primaryPreferred)(implicit ec: ExecutionContext): Future[List[Assessor]]
  def findAll: Future[Seq[Assessor]]
  def save(settings: Assessor): Future[Unit]
  def countSubmittedAvailability: Future[Long]
  def findAvailabilitiesForLocationAndDate(location: Location, date: LocalDate, skills: Seq[SkillType]): Future[Seq[Assessor]]
  //TODO: no usages
  def findAssessorsForEvent(eventId: String): Future[Seq[Assessor]]
  def findUnavailableAssessors(skills: Seq[SkillType], location: Location, date: LocalDate): Future[Seq[Assessor]]
  def remove(userId: UniqueIdentifier): Future[Unit]
}

@Singleton
class AssessorMongoRepository @Inject() (mongo: MongoComponent)
  extends PlayMongoRepository[Assessor](
    collectionName = CollectionNames.ASSESSOR,
    mongoComponent = mongo,
    domainFormat = Assessor.persistedAssessorFormat,
    indexes = Seq(
      IndexModel(ascending("userId"), IndexOptions().unique(true))
    )
  ) with AssessorRepository with ReactiveRepositoryHelpers {

  def find(userId: String): Future[Option[Assessor]] = {
    val query = Document("userId" -> userId)
    collection.find(query).headOption()
  }

  def findByIds(userIds: Seq[String]): Future[Seq[Assessor]] = {
    val query = Document("userId" -> Document("$in" -> userIds))
    collection.find(query).toFuture()
  }

  override def findAll: Future[Seq[Assessor]] = {
    val query = Document.empty
    collection.find(query).toFuture()
  }

  def save(assessor: Assessor): Future[Unit] = {
    require(assessor.availability.isEmpty || assessor.status == AssessorStatus.AVAILABILITIES_SUBMITTED,
      "Can't submit assessor availabilities with new status")
    val query = Document("userId" -> assessor.userId)
    val update: Document = Document("$set" -> Codecs.toBson(assessor))
//TODO: mongo we are doing an upsert here so if the upsert happens it looks like the modified count = 0 as it is a new record?
    val assessorValidator = singleUpdateValidator(assessor.userId, actionDesc = "saveAssessor", ignoreNotFound = true)
    collection.updateOne(query, update, UpdateOptions().upsert(insertIfNoRecordFound)).toFuture() map assessorValidator
  }

  def findAvailabilitiesForLocationAndDate(location: Location, date: LocalDate, skills: Seq[SkillType]): Future[Seq[Assessor]] = {
    import play.api.libs.json.JodaWrites._ // This is needed for LocalDate serialization
    val query = Document("$and" -> BsonArray(
      Document("skills" -> Document("$in" -> Codecs.toBson(skills))),
      Document("availability" ->
        Document("$elemMatch" -> Document(
          "location.name" -> Document("$in" -> BsonArray(location.name, "Home")),
          "date" -> Codecs.toBson(date)
        )))
    ))

    collection.find(query).toFuture()
  }

  def findAssessorsForEvent(eventId: String): Future[Seq[Assessor]] = {
    val query = Document("allocation" -> Document("$elemMatch" -> Document("id" -> eventId)))
    collection.find(query).toFuture()
  }

  def countSubmittedAvailability: Future[Long] = {
    val query = Document("status" -> AssessorStatus.AVAILABILITIES_SUBMITTED.toString)
    collection.countDocuments(query).head()
  }

  def findUnavailableAssessors(skills: Seq[SkillType], location: Location, date: LocalDate): Future[Seq[Assessor]] = {
    import play.api.libs.json.JodaWrites._ // This is needed for LocalDate serialization
    val query = Document("$and" -> BsonArray(
      Document("skills" -> Document("$in" -> Codecs.toBson(skills))),
      Document("availability" ->
        Document("$not" ->
          Document("$elemMatch" -> Document("location" -> location.name, "date" -> Codecs.toBson(date)))
        )
      )
    ))
    collection.find(query).toFuture()
  }

  def remove(userId: UniqueIdentifier): Future[Unit] = {
    val validator = singleRemovalValidator(userId.toString, actionDesc = "deleting assessor")
    collection.deleteOne(Document("userId" -> userId.toBson)).toFuture() map validator
  }
}
