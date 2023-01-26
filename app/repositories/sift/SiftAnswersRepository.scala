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

package repositories.sift

import com.mongodb.client.model.Projections

import javax.inject.{Inject, Singleton}
import model.Exceptions.{NotFoundException, SiftAnswersIncomplete, SiftAnswersSubmitted}
import model.SchemeId
import model.persisted.sift.SiftAnswersStatus.SiftAnswersStatus
import model.persisted.sift.{GeneralQuestionsAnswers, SchemeSpecificAnswer, SiftAnswers, SiftAnswersStatus}
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.UpdateOptions
import repositories.insertIfNoRecordFound
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import repositories.{BaseBSONReader, CollectionNames, ReactiveRepositoryHelpers}
import uk.gov.hmrc.mongo.MongoComponent

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

trait SiftAnswersRepository {
  def addSchemeSpecificAnswer(applicationId: String, schemeId: SchemeId, answer: SchemeSpecificAnswer): Future[Unit]
  def addGeneralAnswers(applicationId: String, answer: GeneralQuestionsAnswers): Future[Unit]
  def findSiftAnswers(applicationId: String): Future[Option[SiftAnswers]]
  def findSchemeSpecificAnswer(applicationId: String, schemeId: SchemeId): Future[Option[SchemeSpecificAnswer]]
  def findGeneralQuestionsAnswers(applicationId: String): Future[Option[GeneralQuestionsAnswers]]
  def findSiftAnswersStatus(applicationId: String): Future[Option[SiftAnswersStatus.Value]]
  def submitAnswers(applicationId: String, requiredSchemes: Set[SchemeId]): Future[Unit]
  def removeSiftAnswers(applicationId: String): Future[Unit]
  def setSiftAnswersStatus(applicationId: String, status: SiftAnswersStatus.SiftAnswersStatus): Future[Unit]
}

@Singleton
class SiftAnswersMongoRepository @Inject() (mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[SiftAnswers](
    collectionName = CollectionNames.SIFT_ANSWERS,
    mongoComponent = mongoComponent,
    domainFormat = SiftAnswers.siftAnswersFormat,
    indexes = Nil
  ) with SiftAnswersRepository with ReactiveRepositoryHelpers with BaseBSONReader {

  override def addSchemeSpecificAnswer(applicationId: String, schemeId: SchemeId, answer: SchemeSpecificAnswer): Future[Unit] = {
    failIfAlreadySubmitted(applicationId) {
      val query = Document("applicationId" -> applicationId)
      val validator = singleUpsertValidator(applicationId, "adding scheme specific answer")

      findSiftAnswers(applicationId).flatMap { result =>
        val updatedSiftAnswers: SiftAnswers = result match {
          case Some(existing) => existing.copy(schemeAnswers = existing.schemeAnswers + (schemeId.value -> answer))
          case _ => SiftAnswers(applicationId, SiftAnswersStatus.DRAFT, generalAnswers = None, Map(schemeId.value -> answer))
        }

        collection.updateOne(
          query,
          Document("$set" -> Codecs.toBson(updatedSiftAnswers)),
          UpdateOptions().upsert(insertIfNoRecordFound)
        ).toFuture() map validator
      }
    }
  }

  override def addGeneralAnswers(applicationId: String, answers: GeneralQuestionsAnswers): Future[Unit] = {
    failIfAlreadySubmitted(applicationId) {
      val query = Document("applicationId" -> applicationId)
      val validator = singleUpsertValidator(applicationId, actionDesc = "adding general answers")

      findSiftAnswers(applicationId).flatMap { result =>
        val updatedSiftAnswers: SiftAnswers = result match {
          case Some(existing) => existing.copy(generalAnswers = Some(answers))
          case _ => SiftAnswers(applicationId, SiftAnswersStatus.DRAFT, Some(answers), Map.empty)
        }

        collection.updateOne(
          query,
          Document("$set" -> Codecs.toBson(updatedSiftAnswers)),
          UpdateOptions().upsert(insertIfNoRecordFound)
        ).toFuture() map validator
      }
    }
  }

  override def findSiftAnswers(applicationId: String): Future[Option[SiftAnswers]] = {
    val query = Document("applicationId" -> applicationId)
    collection.find(query).headOption()
  }

  override def findSchemeSpecificAnswer(applicationId: String, schemeId: SchemeId): Future[Option[SchemeSpecificAnswer]] = {
    val query = Document(
      "applicationId" -> applicationId,
      s"schemeAnswers.$schemeId" -> Document("$exists" -> true)
    )

    val projection = Projections.include(s"schemeAnswers.$schemeId")
    collection.find[Document](query).projection(projection).headOption().map(_.flatMap {
      _.get("schemeAnswers").map { sa =>
        Codecs.fromBson[SchemeSpecificAnswer](sa.asDocument().get(s"$schemeId"))
      }
    })
  }

  override def findGeneralQuestionsAnswers(applicationId: String): Future[Option[GeneralQuestionsAnswers]] = {
    val query = Document(
      "applicationId" -> applicationId,
      "generalAnswers" -> Document("$exists" -> true)
    )

    val projection = Projections.include("generalAnswers")
    collection.find[Document](query).projection(projection).headOption().map {
      _.map { doc =>
        Codecs.fromBson[GeneralQuestionsAnswers](doc.get("generalAnswers").get)
      }
    }
  }

  override def findSiftAnswersStatus(applicationId: String): Future[Option[SiftAnswersStatus]] = {
    val query = Document(
      "applicationId" -> applicationId,
      "status" -> Document("$exists" -> true)
    )
    val projection = Projections.include("status")
    collection.find[Document](query).projection(projection).headOption().map {
      _.map { doc =>
        Codecs.fromBson[SiftAnswersStatus](doc.get("status").get)
      }
    }
  }

  // TODO: mongo this method is badly named. It is an attempt to change the sift status to submitted
  // TODO: it is NOT submitting answers
  override def submitAnswers(applicationId: String, requiredSchemes: Set[SchemeId]): Future[Unit] = {
    failIfAlreadySubmitted(applicationId) {

      val queryAndArray = Document("$and" -> BsonArray(
        Document("applicationId" -> applicationId),
        Document("generalAnswers" ->  Document("$exists" -> true))
      ))

      val filter = if (requiredSchemes.isEmpty) {
        queryAndArray
      } else {
        Document("$and" -> BsonArray(
          queryAndArray,
          // Note that we do not need to wrap the requiredSchemes in a BsonArray. This happens automatically with the document collection
          Document("$and" -> (requiredSchemes map { schemeId =>
            Document(s"schemeAnswers.$schemeId" -> Document("$exists" -> true))
          } toSeq))
        ))
      }

      val errorMsg = if (requiredSchemes.isEmpty) {
        s"Cannot update sift status to submitted because additional questions are missing general answers for $applicationId"
      } else {
        s"Cannot update sift status to submitted because additional questions are missing general or scheme specific " +
          s"(${requiredSchemes.map(_.value).mkString(", ")}) answers for $applicationId"
      }

      val validator = singleUpdateValidator(applicationId, actionDesc = "Submitting sift answers",
        SiftAnswersIncomplete(errorMsg))

      collection.updateOne(
        filter,
        Document("$set" -> Document("status" -> Codecs.toBson(SiftAnswersStatus.SUBMITTED)))
      ).toFuture() map validator
    }
  }

  override def setSiftAnswersStatus(applicationId: String, status: SiftAnswersStatus): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)

    val validator = singleUpdateValidator(applicationId,
      actionDesc = s"Setting sift answers status to $status",
      error = new NotFoundException(
        s"Failed to match a sift answers document to set status to $status for id $applicationId"
      )
    )

    collection.updateOne(
      query,
      Document("$set" -> Document("status" -> status.toBson))
    ).toFuture() map validator
  }

  override def removeSiftAnswers(applicationId: String): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    collection.deleteOne(query).toFuture().map(_ => ())
  }

  private def failIfAlreadySubmitted(applicationId: String)(action: => Future[Unit]): Future[Unit] = {
    findSiftAnswersStatus(applicationId).flatMap {
      case Some(SiftAnswersStatus.SUBMITTED) =>
        Future.failed(SiftAnswersSubmitted(s"Additional answers for applicationId: $applicationId have already been submitted"))
      case _ => action
    }
  }
}
