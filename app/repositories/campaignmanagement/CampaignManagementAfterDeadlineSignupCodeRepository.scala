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

package repositories.campaignmanagement

import model.persisted.CampaignManagementAfterDeadlineCode
import org.joda.time.DateTime
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, BSONObjectID }
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

class CampaignManagementAfterDeadlineSignupCodeMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[CampaignManagementAfterDeadlineCode, BSONObjectID](CollectionNames.CAMPAIGN_MANAGEMENT_AFTER_DEADLINE_CODE,
    mongo, CampaignManagementAfterDeadlineCode.campaignManagementAfterDeadlineCodeFormat,
    ReactiveMongoFormats.objectIdFormats) with CampaignManagementAfterDeadlineSignupCodeRepository with ReactiveRepositoryHelpers {

  def findUnusedValidCode(code: String): Future[Option[CampaignManagementAfterDeadlineCode]] = {
    val query = BSONDocument(
      "code" -> code,
      "usedByApplicationId" -> BSONDocument("$exists" -> false),
      "expires" -> BSONDocument("$gte" -> DateTime.now.getMillis)
    )

    collection.find(query).one[CampaignManagementAfterDeadlineCode]
  }

  def markSignupCodeAsUsed(code: String, applicationId: String): Future[Unit] = {
    val updateValidator = singleUpdateValidator(applicationId, actionDesc = s"marking signup code $code as used")

    collection.update(
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
    collection.insert(code).map(_ => ())
  }
}
