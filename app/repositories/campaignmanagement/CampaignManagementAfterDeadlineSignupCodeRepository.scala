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

package repositories.campaignmanagement

import model.persisted.CampaignManagementAfterDeadlineCode
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Indexes.{ascending, descending}
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import repositories.{CollectionNames, ReactiveRepositoryHelpers, offsetDateTimeToBson}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.{OffsetDateTime, ZoneId}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait CampaignManagementAfterDeadlineSignupCodeRepository {
  def findUnusedValidCode(code: String): Future[Option[CampaignManagementAfterDeadlineCode]]
  def markSignupCodeAsUsed(code: String, applicationId: String): Future[Unit]
  def save(code: CampaignManagementAfterDeadlineCode): Future[Unit]
}

@Singleton
class CampaignManagementAfterDeadlineSignupCodeMongoRepository @Inject() (mongo: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[CampaignManagementAfterDeadlineCode](
    collectionName = CollectionNames.CAMPAIGN_MANAGEMENT_AFTER_DEADLINE_CODE,
    mongoComponent = mongo,
    domainFormat = CampaignManagementAfterDeadlineCode.campaignManagementAfterDeadlineCodeFormat,
    indexes = Seq(
      IndexModel(ascending("code"), IndexOptions().unique(true)),
      IndexModel(descending("expires"), IndexOptions().unique(false))
    )
  ) with CampaignManagementAfterDeadlineSignupCodeRepository with ReactiveRepositoryHelpers {

  override def findUnusedValidCode(code: String): Future[Option[CampaignManagementAfterDeadlineCode]] = {
    val query = Document(
      "code" -> code,
      "usedByApplicationId" -> Document("$exists" -> false),
      "expires" -> Document("$gte" -> offsetDateTimeToBson(OffsetDateTime.now(ZoneId.of("UTC"))))
    )

    collection.find(query).headOption()
  }

  override def markSignupCodeAsUsed(code: String, applicationId: String): Future[Unit] = {
    val updateValidator = singleUpdateValidator(applicationId, actionDesc = s"marking signup code $code as used")

    collection.updateOne(
      Document(
        "code" -> code,
        "usedByApplicationId" -> Document("$exists" -> false)
      ),
      Document(
        "$set" -> Document("usedByApplicationId" -> applicationId)
      )
    ).toFuture() map updateValidator
  }

  override def save(code: CampaignManagementAfterDeadlineCode): Future[Unit] = {
    collection.insertOne(code).toFuture() map(_ => ())
  }
}
