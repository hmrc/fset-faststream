/*
 * Copyright 2018 HM Revenue & Customs
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

package repositories.partnergraduateprogrammes

import model.Exceptions._
import model.persisted.PartnerGraduateProgrammes
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, _ }
import repositories.{ CollectionNames, ReactiveRepositoryHelpers }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait PartnerGraduateProgrammesRepository {
  def update(applicationId: String, pgp: PartnerGraduateProgrammes): Future[Unit]

  def find(applicationId: String): Future[PartnerGraduateProgrammes]
}

class PartnerGraduateProgrammesMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[PartnerGraduateProgrammes, BSONObjectID](CollectionNames.APPLICATION, mongo,
    PartnerGraduateProgrammes.partnerGraduateProgrammesFormat, ReactiveMongoFormats.objectIdFormats) with PartnerGraduateProgrammesRepository
    with ReactiveRepositoryHelpers {

  val PartnerGraduateProgrammesCollection = "partner-graduate-programmes"

  override def update(applicationId: String, pgp: PartnerGraduateProgrammes): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val updateBSON = BSONDocument("$set" -> BSONDocument(
      "progress-status.partner-graduate-programmes" -> true,
      PartnerGraduateProgrammesCollection -> pgp
    ))

    val validator = singleUpdateValidator(applicationId, actionDesc = "updating partner programmes",
      CannotUpdatePartnerGraduateProgrammes(applicationId))

    collection.update(query, updateBSON) map validator
  }

  override def find(applicationId: String): Future[PartnerGraduateProgrammes] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument(PartnerGraduateProgrammesCollection -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) if document.getAs[BSONDocument](PartnerGraduateProgrammesCollection).isDefined =>
        document.getAs[PartnerGraduateProgrammes](PartnerGraduateProgrammesCollection).get
      case _ => throw PartnerGraduateProgrammesNotFound(applicationId)
    }
  }
}
