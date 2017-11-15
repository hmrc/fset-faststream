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
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, BSONObjectID }
import repositories.CollectionNames
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait CampaignManagementAfterDeadlineSignupCodeRepository {
  def isUnusedCode(code: String): Future[Option[CampaignManagementAfterDeadlineCode]]
  def save(code: CampaignManagementAfterDeadlineCode): Future[Unit]
}

class CampaignManagementAfterDeadlineSignupCodeMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[CampaignManagementAfterDeadlineCode, BSONObjectID](CollectionNames.CAMPAIGN_MANAGEMENT_AFTER_DEADLINE_CODE,
    mongo, CampaignManagementAfterDeadlineCode.campaignManagementAfterDeadlineCodeFormat,
    ReactiveMongoFormats.objectIdFormats) with CampaignManagementAfterDeadlineSignupCodeRepository {

  def isUnusedCode(code: String): Future[Option[CampaignManagementAfterDeadlineCode]] = {
    collection.find(
      BSONDocument(
        "code" -> code,
        "usedByApplicationId" -> BSONDocument("$exists" -> false)
      )
    ).one[CampaignManagementAfterDeadlineCode]
  }

  def save(code: CampaignManagementAfterDeadlineCode): Future[Unit] = {
    collection.insert(code).map(_ => ())
  }
}
