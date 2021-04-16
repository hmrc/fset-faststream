/*
 * Copyright 2021 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import model.PassMarkSettingsCreateResponse
import model.exchange.passmarksettings._
import play.api.libs.json.{Format, JsNumber, JsObject}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
//import play.modules.reactivemongo.ReactiveMongoComponent
//import reactivemongo.api.DB
//import reactivemongo.api.indexes.Index
//import reactivemongo.api.indexes.IndexType.Ascending
//import reactivemongo.bson._
//import reactivemongo.play.json.ImplicitBSONHandlers._
//import uk.gov.hmrc.mongo.ReactiveRepository
//import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import repositories.{ CollectionNames, OFormatHelper }

@Singleton
class Phase1PassMarkSettingsMongoRepository @Inject() (mongo: MongoComponent)
  extends PlayMongoRepository[Phase1PassMarkSettings](
    collectionName = CollectionNames.PHASE1_PASS_MARK_SETTINGS,
    mongoComponent = mongo,
    domainFormat = Phase1PassMarkSettings.jsonFormat,
    indexes = Nil
  ) with PassMarkSettingsRepository[Phase1PassMarkSettings] {

  //TODO: test the index
/*
  override def indexes: Seq[Index] = Seq(
    Index(Seq(("createDate", Ascending)), unique = true)
  )*/
}

@Singleton
class Phase2PassMarkSettingsMongoRepository @Inject() (mongo: MongoComponent)
  extends PlayMongoRepository[Phase2PassMarkSettings](
    collectionName = CollectionNames.PHASE2_PASS_MARK_SETTINGS,
    mongoComponent = mongo,
    domainFormat = Phase2PassMarkSettings.jsonFormat,
    indexes = Nil
  ) with PassMarkSettingsRepository[Phase2PassMarkSettings] {

  //TODO: test the index
/*
  override def indexes: Seq[Index] = Seq(
    Index(Seq(("createDate", Ascending)), unique = true)
  )*/
}

@Singleton
class Phase3PassMarkSettingsMongoRepository @Inject() (mongo: MongoComponent)
  extends PlayMongoRepository[Phase3PassMarkSettings](
    collectionName = CollectionNames.PHASE3_PASS_MARK_SETTINGS,
    mongoComponent = mongo,
    domainFormat = Phase3PassMarkSettings.jsonFormat,
    indexes = Nil
  ) with PassMarkSettingsRepository[Phase3PassMarkSettings] {

  //TODO: test the index
/*
  override def indexes: Seq[Index] = Seq(
    Index(Seq(("createDate", Ascending)), unique = true)
  )*/
}

@Singleton
class AssessmentCentrePassMarkSettingsMongoRepository @Inject() (mongo: MongoComponent)
  extends PlayMongoRepository[AssessmentCentrePassMarkSettings](
    collectionName = CollectionNames.ASSESSMENT_CENTRE_PASS_MARK_SETTINGS,
    mongoComponent = mongo,
    domainFormat = AssessmentCentrePassMarkSettings.jsonFormat,
    indexes = Nil
  ) with PassMarkSettingsRepository[AssessmentCentrePassMarkSettings] {

  //TODO: test the index
/*
  override def indexes: Seq[Index] = Seq(
    Index(Seq(("createDate", Ascending)), unique = true)
  )*/
}

trait PassMarkSettingsRepository[T <: PassMarkSettings] {
  //  this: ReactiveRepository[T, _] =>

  //  implicit val oFormats = OFormatHelper.oFormat(domainFormatImplicit)

  /*
  def create(passMarkSettings: T)(implicit jsonFormat: Format[T]): Future[PassMarkSettingsCreateResponse] = {
    collection.insert(ordered = false).one(passMarkSettings) flatMap { _ =>
      getLatestVersion.map(createResponse =>
        PassMarkSettingsCreateResponse(
          createResponse.map(_.version).get,
          createResponse.map(_.createDate).get
        )
      )
    }
  }*/
  def create(passMarkSettings: T)(implicit jsonFormat: Format[T]): Future[PassMarkSettingsCreateResponse] = ???

  /*
  def getLatestVersion(implicit jsonFormat: Format[T]): Future[Option[T]] = {
    val query = BSONDocument.empty
    val descending = -1
    val sort = JsObject(Seq("createDate" -> JsNumber(descending)))
    collection.find(query, projection = Option.empty[JsObject]).sort(sort).one[T]
  }*/
  def getLatestVersion(implicit jsonFormat: Format[T]): Future[Option[T]] = ???
}
