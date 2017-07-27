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

import model.SchemeId
import model.persisted.{ QuestionnaireAnswer, QuestionnaireQuestion, SchemeSpecificAnswer, SiftAnswers }
import model.report.QuestionnaireReportItem
import play.api.libs.json._
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.Producer.nameValue2Producer
import reactivemongo.bson._
import repositories.{ BaseBSONReader, CollectionNames, ReactiveRepositoryHelpers }
import services.reporting.SocioEconomicScoreCalculator
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps

trait SiftAnswersRepository {
  def addSchemeSpecificAnswer(applicationId: String, schemeId: SchemeId, answer: SchemeSpecificAnswer): Future[Unit]
  def findSiftAnswers(applicationId: String): Future[Option[SiftAnswers]]
  def findSchemeSpecificAnswer(applicationId: String, schemeId: SchemeId) : Future[Option[SchemeSpecificAnswer]]
}

class SiftAnswersMongoRepository()(implicit mongo: () => DB)
  extends ReactiveRepository[SiftAnswers, BSONObjectID](CollectionNames.SIFT_ANSWERS, mongo,
    SiftAnswers.siftAnswersFormat, ReactiveMongoFormats.objectIdFormats) with SiftAnswersRepository
    with ReactiveRepositoryHelpers with BaseBSONReader {

  override def addSchemeSpecificAnswer(applicationId: String, schemeId: SchemeId, answer: SchemeSpecificAnswer): Future[Unit] = {

    val appId = "applicationId" -> applicationId

    val validator = singleUpsertValidator(applicationId, actionDesc = "adding scheme specific answer")

    findSiftAnswers(applicationId).map { result =>
      val updatedSiftAnswers: SiftAnswers = result match {
        case Some(existing) => existing.copy(answers = existing.answers + (schemeId.value -> answer))
        case _ => SiftAnswers(applicationId, Map(schemeId.value -> answer))
      }

      collection.update(
        BSONDocument(appId),
        BSONDocument("$set" -> updatedSiftAnswers),
        upsert = true
      ) map validator
    }
  }

  override def findSiftAnswers(applicationId: String): Future[Option[SiftAnswers]] = {
    val query = BSONDocument("applicationId" -> applicationId)

    collection.find(query).one[SiftAnswers]
  }

  override def findSchemeSpecificAnswer(applicationId: String, schemeId: SchemeId) : Future[Option[SchemeSpecificAnswer]] = {
    val query = BSONDocument("applicationId" -> applicationId, "schemeId" -> schemeId)

    collection.find(query).one[SchemeSpecificAnswer]
  }
}
