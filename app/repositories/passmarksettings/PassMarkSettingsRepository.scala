/*
 * Copyright 2022 HM Revenue & Customs
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
import model.exchange.passmarksettings._
import org.mongodb.scala.Document
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.Sorts.descending
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import repositories.CollectionNames
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class Phase1PassMarkSettingsMongoRepository @Inject() (mongo: MongoComponent)
  extends PlayMongoRepository[Phase1PassMarkSettings](
    collectionName = CollectionNames.PHASE1_PASS_MARK_SETTINGS,
    mongoComponent = mongo,
    domainFormat = Phase1PassMarkSettings.jsonFormat,
    indexes = Seq(
      IndexModel(ascending("createDate"), IndexOptions().unique(true))
    )
  ) with PassMarkSettingsRepository[Phase1PassMarkSettings] {

  override def getLatestVersion: Future[Option[Phase1PassMarkSettings]] = {
    collection.find(Document.empty).sort(descending("createDate")).headOption()
  }
}

@Singleton
class Phase2PassMarkSettingsMongoRepository @Inject() (mongo: MongoComponent)
  extends PlayMongoRepository[Phase2PassMarkSettings](
    collectionName = CollectionNames.PHASE2_PASS_MARK_SETTINGS,
    mongoComponent = mongo,
    domainFormat = Phase2PassMarkSettings.jsonFormat,
    indexes = Seq(
      IndexModel(ascending("createDate"), IndexOptions().unique(true))
    )
  ) with PassMarkSettingsRepository[Phase2PassMarkSettings] {

  override def getLatestVersion: Future[Option[Phase2PassMarkSettings]] = {
    collection.find(Document.empty).sort(descending("createDate")).headOption()
  }
}

@Singleton
class Phase3PassMarkSettingsMongoRepository @Inject() (mongo: MongoComponent)
  extends PlayMongoRepository[Phase3PassMarkSettings](
    collectionName = CollectionNames.PHASE3_PASS_MARK_SETTINGS,
    mongoComponent = mongo,
    domainFormat = Phase3PassMarkSettings.jsonFormat,
    indexes = Seq(
      IndexModel(ascending("createDate"), IndexOptions().unique(true))
    )
  ) with PassMarkSettingsRepository[Phase3PassMarkSettings] {

  override def getLatestVersion: Future[Option[Phase3PassMarkSettings]] = {
    collection.find(Document.empty).sort(descending("createDate")).headOption()
  }
}

@Singleton
class AssessmentCentrePassMarkSettingsMongoRepository @Inject() (mongo: MongoComponent)
  extends PlayMongoRepository[AssessmentCentrePassMarkSettings](
    collectionName = CollectionNames.ASSESSMENT_CENTRE_PASS_MARK_SETTINGS,
    mongoComponent = mongo,
    domainFormat = AssessmentCentrePassMarkSettings.jsonFormat,
    indexes = Seq(
      IndexModel(ascending("createDate"), IndexOptions().unique(true))
    )
  ) with PassMarkSettingsRepository[AssessmentCentrePassMarkSettings] {

  override def getLatestVersion: Future[Option[AssessmentCentrePassMarkSettings]] = {
    collection.find(Document.empty).sort(descending("createDate")).headOption()
  }
}

trait PassMarkSettingsRepository[T <: PassMarkSettings] {
  this: PlayMongoRepository[T] =>

  def create(passMarkSettings: T): Future[PassMarkSettingsCreateResponse] = {
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
