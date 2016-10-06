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

package repositories

import model.Commands._
import model.exchange.passmarksettings.Phase1PassMarkSettings
import play.api.libs.json.{ JsNumber, JsObject }
import reactivemongo.api.DB
import reactivemongo.bson._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Phase1PassMarkSettingsRepository {
  def create(phase1PassMarkSettings: Phase1PassMarkSettings): Future[PassMarkSettingsCreateResponse]

  def getLatestVersion: Future[Option[Phase1PassMarkSettings]]
}

class Phase1PassMarkSettingsMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[Phase1PassMarkSettings, BSONObjectID]("phase1-pass-mark-settings", mongo,
    Phase1PassMarkSettings.phase1PassMarkSettings, ReactiveMongoFormats.objectIdFormats) with Phase1PassMarkSettingsRepository {

  override def create(phase1PassMarkSettings: Phase1PassMarkSettings): Future[PassMarkSettingsCreateResponse] = {
    collection.insert(phase1PassMarkSettings) flatMap { _ =>
      getLatestVersion.map(createResponse =>
        PassMarkSettingsCreateResponse(
          createResponse.map(_.version).get,
          createResponse.map(_.createDate).get
        )
      )
    }
  }

  override def getLatestVersion: Future[Option[Phase1PassMarkSettings]] = {
    val query = BSONDocument()
    val sort = JsObject(Seq("createDate" -> JsNumber(-1)))
    collection.find(query).sort(sort).one[Phase1PassMarkSettings]
  }
}
