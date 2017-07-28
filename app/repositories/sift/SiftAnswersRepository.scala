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

package repositories.sift

import model.Exceptions.{ SchemeSpecificAnswerNotFound, SiftAnswersIncomplete, SiftAnswersSubmitted }
import model.SchemeId
import model.persisted.sift.SiftAnswersStatus.SiftAnswersStatus
import model.persisted.sift.{ GeneralQuestionsAnswers, SchemeSpecificAnswer, SiftAnswers, SiftAnswersStatus }
import reactivemongo.api.DB
import reactivemongo.bson.Producer.nameValue2Producer
import reactivemongo.bson._
import repositories.{ BaseBSONReader, CollectionNames, ReactiveRepositoryHelpers }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps

trait SiftAnswersRepository {
  def addSchemeSpecificAnswer(applicationId: String, schemeId: SchemeId, answer: SchemeSpecificAnswer): Future[Unit]
  def addGeneralAnswers(applicationId: String, answer: GeneralQuestionsAnswers): Future[Unit]
  def findSiftAnswers(applicationId: String): Future[Option[SiftAnswers]]
  def findSchemeSpecificAnswer(applicationId: String, schemeId: SchemeId): Future[Option[SchemeSpecificAnswer]]
  def findGeneralQuestionsAnswers(applicationId: String): Future[Option[GeneralQuestionsAnswers]]
  def findSiftAnswersStatus(applicationId: String): Future[Option[SiftAnswersStatus.Value]]
  def submitAnswers(applicationId: String, requiredSchemes: Set[SchemeId]): Future[Unit]
}

class SiftAnswersMongoRepository()(implicit mongo: () => DB)
  extends ReactiveRepository[SiftAnswers, BSONObjectID](CollectionNames.SIFT_ANSWERS, mongo,
    SiftAnswers.siftAnswersFormat, ReactiveMongoFormats.objectIdFormats) with SiftAnswersRepository
    with ReactiveRepositoryHelpers with BaseBSONReader {

  override def addSchemeSpecificAnswer(applicationId: String, schemeId: SchemeId, answer: SchemeSpecificAnswer): Future[Unit] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument("status" -> BSONDocument("$ne" -> SiftAnswersStatus.SUBMITTED))
    ))
    val validator = singleUpsertValidator(applicationId, "adding scheme specific answer",
      SchemeSpecificAnswerNotFound(""))

    findSiftAnswers(applicationId).map { result =>
      val updatedSiftAnswers: SiftAnswers = result match {
        case Some(existing) => existing.copy(schemeAnswers = existing.schemeAnswers + (schemeId.value -> answer))
        case _ => SiftAnswers(applicationId, SiftAnswersStatus.DRAFT, None, Map(schemeId.value -> answer))
      }

      collection.update(
        query,
        BSONDocument("$set" -> updatedSiftAnswers),
        upsert = true
      ) map validator
    }
  }

  override def addGeneralAnswers(applicationId: String, answers: GeneralQuestionsAnswers): Future[Unit] = {
    findSiftAnswersStatus(applicationId).flatMap { status =>
      status match {
        case Some(SiftAnswersStatus.SUBMITTED) =>
          Future.failed(SiftAnswersSubmitted(s"SiftAnswers for applicationId: $applicationId have already been submitted"))
        case _ => {
          val query = BSONDocument("applicationId" -> applicationId)
          val validator = singleUpsertValidator(applicationId, actionDesc = "adding general answers")

          findSiftAnswers(applicationId).map { result =>
            val updatedSiftAnswers: SiftAnswers = result match {
              case Some(existing) => existing.copy(generalAnswers = Some(answers))
              case _ => SiftAnswers(applicationId, SiftAnswersStatus.DRAFT, Some(answers), Map.empty)
            }

            collection.update(
              query,
              BSONDocument("$set" -> updatedSiftAnswers),
              upsert = true
            ) map validator
          }
        }
      }
    }
  }

  override def findSiftAnswers(applicationId: String): Future[Option[SiftAnswers]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    collection.find(query).one[SiftAnswers]
  }

  override def findSchemeSpecificAnswer(applicationId: String, schemeId: SchemeId): Future[Option[SchemeSpecificAnswer]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument(s"schemeAnswers.$schemeId" -> BSONDocument("$exists" -> true))
    ))
    val projection = BSONDocument(s"schemeAnswers.$schemeId" -> 1, "_id" -> 0)
    collection.find(query, projection).one[SchemeSpecificAnswer]
  }

  override def findGeneralQuestionsAnswers(applicationId: String): Future[Option[GeneralQuestionsAnswers]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument(s"generalAnswers" -> BSONDocument("$exists" -> true))
    ))
    val projection = BSONDocument(s"generalAnswers" -> 1, "_id" -> 0)
    collection.find(query, projection).one[GeneralQuestionsAnswers]
  }

  override def findSiftAnswersStatus(applicationId: String): Future[Option[SiftAnswersStatus]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("status" -> 1, "_id" -> 0)
    collection.find(query, projection).one[SiftAnswersStatus]
  }

  override def submitAnswers(applicationId: String, requiredSchemes: Set[SchemeId]): Future[Unit] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument("status" -> SiftAnswersStatus.DRAFT)
    ))
    val validator = singleUpdateValidator(applicationId, actionDesc = "Submitting sift answers",
      SiftAnswersIncomplete(""))

    collection.update(
      query,
      BSONDocument("$set" -> BSONDocument("status" -> SiftAnswersStatus.SUBMITTED))
    ) map validator
  }
}
