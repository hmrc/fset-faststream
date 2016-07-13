/*
 * Copyright 2016 HM Revenue & Customs
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

package repositories.application

import model.Commands
import model.Commands._
import model.Exceptions.AssistanceDetailsNotFound
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, _ }
import repositories._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AssistanceDetailsRepository {

  val errorCode = 500

  def update(applicationId: String, userId: String, ad: AssistanceDetailsExchange): Future[Unit]

  def find(applicationId: String): Future[AssistanceDetailsExchange]
}

class AssistanceDetailsMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[AssistanceDetailsExchange, BSONObjectID]("application", mongo,
    Commands.Implicits.updateAssistanceDetailsRequestFormats, ReactiveMongoFormats.objectIdFormats) with AssistanceDetailsRepository {

  override def update(applicationId: String, userId: String, ad: AssistanceDetailsExchange): Future[Unit] = {

    val query = BSONDocument("applicationId" -> applicationId, "userId" -> userId)

    val assistanceDetailsBSON = BSONDocument("$set" -> BSONDocument(
      "applicationStatus" -> "IN_PROGRESS",
      s"progress-status.assistance" -> true,
      "assistance-details" -> ad
    ))

    collection.update(query, assistanceDetailsBSON, upsert = false) map {
      case _ => ()
    }
  }

  override def find(applicationId: String): Future[AssistanceDetailsExchange] = {

    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("assistance-details" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) if document.getAs[BSONDocument]("assistance-details").isDefined => {
        val root = document.getAs[BSONDocument]("assistance-details").get
        val needsAssistance = root.getAs[String]("needsAssistance").get
        val typeOfdisability = root.getAs[List[String]]("typeOfdisability")
        val detailsOfdisability = root.getAs[String]("detailsOfdisability")
        val guaranteedInterview = root.getAs[String]("guaranteedInterview")
        val needsAdjustment = root.getAs[String]("needsAdjustment")
        val typeOfAdjustments = root.getAs[List[String]]("typeOfAdjustments")
        val otherAdjustments = root.getAs[String]("otherAdjustments")
        val campaignReferrer = root.getAs[String]("campaignReferrer")
        val campaignOther = root.getAs[String]("campaignOther")
        val confirmation = root.getAs[Boolean]("adjustments-confirmed")
        val verbalTimeAdjustmentPercentage = root.getAs[Int]("verbalTimeAdjustmentPercentage")
        val numericalTimeAdjustmentPercentage = root.getAs[Int]("numericalTimeAdjustmentPercentage")

        AssistanceDetailsExchange(needsAssistance, typeOfdisability, detailsOfdisability, guaranteedInterview,
          needsAdjustment, typeOfAdjustments, otherAdjustments, campaignReferrer, campaignOther, confirmation,
          numericalTimeAdjustmentPercentage, verbalTimeAdjustmentPercentage)
      }
      case _ => throw new AssistanceDetailsNotFound(applicationId)
    }
  }

}
