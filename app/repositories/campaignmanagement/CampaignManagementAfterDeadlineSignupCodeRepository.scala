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

package repositories.campaignmanagement

import javax.inject.{ Inject, Singleton }
import model.persisted.CampaignManagementAfterDeadlineCode
import org.joda.time.DateTime
import play.api.libs.json.JsObject
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.{ Ascending, Descending }
import reactivemongo.bson.{ BSONDocument, BSONObjectID }
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories.{ CollectionNames, ReactiveRepositoryHelpers }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait CampaignManagementAfterDeadlineSignupCodeRepository {
  def findUnusedValidCode(code: String): Future[Option[CampaignManagementAfterDeadlineCode]]
  def markSignupCodeAsUsed(code: String, applicationId: String): Future[Unit]
  def save(code: CampaignManagementAfterDeadlineCode): Future[Unit]
}

@Singleton
class CampaignManagementAfterDeadlineSignupCodeMongoRepository @Inject() (mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[CampaignManagementAfterDeadlineCode, BSONObjectID](
    CollectionNames.CAMPAIGN_MANAGEMENT_AFTER_DEADLINE_CODE,
    mongoComponent.mongoConnector.db,
    CampaignManagementAfterDeadlineCode.campaignManagementAfterDeadlineCodeFormat,
    ReactiveMongoFormats.objectIdFormats) with CampaignManagementAfterDeadlineSignupCodeRepository with ReactiveRepositoryHelpers {

  override def indexes: Seq[Index] = Seq(
    Index(Seq("code" -> Ascending), unique = true),
    Index(Seq("expires" -> Descending), unique = false)
  )

  def findUnusedValidCode(code: String): Future[Option[CampaignManagementAfterDeadlineCode]] = {
    val query = BSONDocument(
      "code" -> code,
      "usedByApplicationId" -> BSONDocument("$exists" -> false),
      "expires" -> BSONDocument("$gte" -> DateTime.now.getMillis)
    )

    collection.find(query, projection = Option.empty[JsObject]).one[CampaignManagementAfterDeadlineCode]
  }

  def markSignupCodeAsUsed(code: String, applicationId: String): Future[Unit] = {
    val updateValidator = singleUpdateValidator(applicationId, actionDesc = s"marking signup code $code as used")

    collection.update(ordered = false).one(
      BSONDocument(
        "code" -> code,
        "usedByApplicationId" -> BSONDocument("$exists" -> false)
      ),
      BSONDocument(
        "$set" -> BSONDocument(
          "usedByApplicationId" -> applicationId
        )
      )
    ).map(updateValidator)
  }

  def save(code: CampaignManagementAfterDeadlineCode): Future[Unit] = {
    collection.insert(ordered = false).one(code).map(_ => ())
  }
}
