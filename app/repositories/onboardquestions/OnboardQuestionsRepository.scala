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

package repositories.onboardquestions

import model.persisted.OnboardQuestions
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Projections
import repositories.{CollectionNames, ReactiveRepositoryHelpers}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait OnboardQuestionsRepository {
  def tryGetOnboardQuestions(appId: String): Future[Option[OnboardQuestions]]
  def save(onboardQuestions: OnboardQuestions): Future[Unit]
}

@Singleton
class OnboardQuestionsMongoRepository @Inject() (mongo: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[OnboardQuestions](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongo,
    domainFormat = OnboardQuestions.format,
    indexes = Nil
  ) with OnboardQuestionsRepository with ReactiveRepositoryHelpers {

  override def tryGetOnboardQuestions(appId: String): Future[Option[OnboardQuestions]] = {
    val query = Document(
      "applicationId" -> appId,
      s"onboarding-questions" -> Document("$exists" -> true)
    )
    val projection = Projections.include("applicationId", "onboarding-questions")

    collection.find[Document](query).projection(projection).headOption().map { _.map { doc =>
      val applicationId = doc.get("applicationId").get.asString().getValue
      val onboardingQuestionsRootOpt = doc.get("onboarding-questions").map(_.asDocument())
      val niNumber = onboardingQuestionsRootOpt.flatMap(doc => Try(doc.get("niNumber").asString().getValue).toOption)
      OnboardQuestions(applicationId, niNumber)
    }}
  }

  def save(onboardQuestions: OnboardQuestions): Future[Unit] = {
    val query = Document("applicationId" -> onboardQuestions.applicationId)
    val update = Document("$set" -> Document("onboarding-questions.niNumber" -> onboardQuestions.niNumber))

    val validator = singleUpdateValidator(onboardQuestions.applicationId, actionDesc = "saving onboard questions")
    collection.updateOne(query, update).toFuture().map(updateResult => validator(updateResult))
  }
}
