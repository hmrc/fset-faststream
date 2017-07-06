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

package repositories.passmarksettings

import model.Commands._
import model.exchange.passmarksettings._
import play.api.libs.json.{ Format, JsNumber, JsObject }
import reactivemongo.api.DB
import reactivemongo.bson._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import repositories.{ CollectionNames, OFormatHelper }

import Phase1PassMarkSettings._
import Phase3PassMarkSettings._
import Phase2PassMarkSettings._
import ReactiveMongoFormats._

class Phase1PassMarkSettingsMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[Phase1PassMarkSettings, BSONObjectID](CollectionNames.PHASE1_PASS_MARK_SETTINGS, mongo,
    phase1PassMarkSettingsFormat, objectIdFormats) with PassMarkSettingsRepository[Phase1PassMarkSettings]

class Phase2PassMarkSettingsMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[Phase2PassMarkSettings, BSONObjectID](CollectionNames.PHASE2_PASS_MARK_SETTINGS, mongo,
    phase2PassMarkSettingsFormat, objectIdFormats) with PassMarkSettingsRepository[Phase2PassMarkSettings]

class Phase3PassMarkSettingsMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[Phase3PassMarkSettings, BSONObjectID](CollectionNames.PHASE3_PASS_MARK_SETTINGS, mongo,
    phase3PassMarkSettingsFormat, objectIdFormats) with PassMarkSettingsRepository[Phase3PassMarkSettings]

trait PassMarkSettingsRepository[T <: PassMarkSettings] {
  this: ReactiveRepository[T, _] =>

  implicit val oFormats = OFormatHelper.oFormat(domainFormatImplicit)

  def create(passMarkSettings: T)(implicit jsonFormat: Format[T]): Future[PassMarkSettingsCreateResponse] = {
    collection.insert(passMarkSettings) flatMap { _ =>
      getLatestVersion.map(createResponse =>
        PassMarkSettingsCreateResponse(
          createResponse.map(_.version).get,
          createResponse.map(_.createDate).get
        ))
    }
  }

  def getLatestVersion(implicit jsonFormat: Format[T]): Future[Option[T]] = {
    val query = BSONDocument.empty
    val sort = JsObject(Seq("createDate" -> JsNumber(-1)))
    collection.find(query).sort(sort).one[T]
  }
}
