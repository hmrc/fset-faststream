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

package repositories.passmarksettings

import model.PassMarkSettingsCreateResponse
import model.exchange.passmarksettings.*
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.Sorts.descending
import org.mongodb.scala.model.{IndexModel, IndexOptions, Projections}
import org.mongodb.scala.{Document, ObservableFuture, SingleObservableFuture}
import repositories.CollectionNames
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Phase1PassMarkSettingsMongoRepository @Inject() (mongo: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[Phase1PassMarkSettingsPersistence](
    collectionName = CollectionNames.PHASE1_PASS_MARK_SETTINGS,
    mongoComponent = mongo,
    domainFormat = Phase1PassMarkSettingsPersistence.jsonFormat,
    indexes = Seq(
      IndexModel(ascending("createDate"), IndexOptions().unique(true))
    )
  ) with PassMarkSettingsRepository[Phase1PassMarkSettingsPersistence] {

  override def getLatestVersion: Future[Option[Phase1PassMarkSettingsPersistence]] = {
    // Add a projection in addition to the sort so mongo will perform an IXSCAN instead of a COLLSCAN
    val projection = Projections.include(
      "schemes",
      "version",
      "createDate",
      "createdBy"
    )
    collection.find(Document.empty).projection(projection).sort(descending("createDate")).headOption()
  }
}

@Singleton
class Phase2PassMarkSettingsMongoRepository @Inject() (mongo: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[Phase2PassMarkSettingsPersistence](
    collectionName = CollectionNames.PHASE2_PASS_MARK_SETTINGS,
    mongoComponent = mongo,
    domainFormat = Phase2PassMarkSettingsPersistence.jsonFormat,
    indexes = Seq(
      IndexModel(ascending("createDate"), IndexOptions().unique(true))
    )
  ) with PassMarkSettingsRepository[Phase2PassMarkSettingsPersistence] {

  override def getLatestVersion: Future[Option[Phase2PassMarkSettingsPersistence]] = {
    collection.find(Document.empty).sort(descending("createDate")).headOption()
  }
}

@Singleton
class Phase3PassMarkSettingsMongoRepository @Inject() (mongo: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[Phase3PassMarkSettingsPersistence](
    collectionName = CollectionNames.PHASE3_PASS_MARK_SETTINGS,
    mongoComponent = mongo,
    domainFormat = Phase3PassMarkSettingsPersistence.jsonFormat,
    indexes = Seq(
      IndexModel(ascending("createDate"), IndexOptions().unique(true))
    )
  ) with PassMarkSettingsRepository[Phase3PassMarkSettingsPersistence] {

  override def getLatestVersion: Future[Option[Phase3PassMarkSettingsPersistence]] = {
    collection.find(Document.empty).sort(descending("createDate")).headOption()
  }
}

@Singleton
class AssessmentCentrePassMarkSettingsMongoRepository @Inject() (mongo: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[AssessmentCentrePassMarkSettingsPersistence](
    collectionName = CollectionNames.ASSESSMENT_CENTRE_PASS_MARK_SETTINGS,
    mongoComponent = mongo,
    domainFormat = AssessmentCentrePassMarkSettingsPersistence.jsonFormat,
    indexes = Seq(
      IndexModel(ascending("createDate"), IndexOptions().unique(true))
    )
  ) with PassMarkSettingsRepository[AssessmentCentrePassMarkSettingsPersistence] {

  override def getLatestVersion: Future[Option[AssessmentCentrePassMarkSettingsPersistence]] = {
    collection.find(Document.empty).sort(descending("createDate")).headOption()
  }
}

trait PassMarkSettingsRepository[T <: PassMarkSettingsPersistence] {
  this: PlayMongoRepository[T] =>

  def create(passMarkSettings: T)(implicit ec: ExecutionContext): Future[PassMarkSettingsCreateResponse] = {
    collection.insertOne(passMarkSettings).toFuture() flatMap { _ =>
      getLatestVersion.map( createResponse =>
        PassMarkSettingsCreateResponse(
          createResponse.map(_.version).get,
          createResponse.map(_.createDate).get
        )
      )
    }
  }

  def getLatestVersion: Future[Option[T]]
}
