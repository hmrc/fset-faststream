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

import model.PassmarkPersistedObjects
import model.PassmarkPersistedObjects.AssessmentCentrePassMarkSettings
import play.api.libs.json.{ JsNumber, JsObject }
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, BSONObjectID }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AssessmentCentrePassMarkSettingsRepository {

  def tryGetLatestVersion: Future[Option[AssessmentCentrePassMarkSettings]]

  def create(settings: AssessmentCentrePassMarkSettings): Future[Unit]
}

//scalastyle:off
class AssessmentCentrePassMarkSettingsMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[PassmarkPersistedObjects.AssessmentCentrePassMarkSettings, BSONObjectID]("assessment-centre-pass-mark-settings", mongo,
    PassmarkPersistedObjects.Implicits.PersistedAssessmentCentrePassMarkSettingsFormat,
    ReactiveMongoFormats.objectIdFormats) with AssessmentCentrePassMarkSettingsRepository {
  //scalastyle:on

  def tryGetLatestVersion: Future[Option[AssessmentCentrePassMarkSettings]] = {
    val query = BSONDocument()
    val sort = new JsObject(Seq("info.createDate" -> JsNumber(-1)))

    collection.find(query).sort(sort).one[BSONDocument].map { docOpt =>
      docOpt.map { doc =>
        assessmentCentrePassMarkSettingsHandler.read(doc)
      }
    }
  }

  def create(settings: AssessmentCentrePassMarkSettings): Future[Unit] = {
    val doc = assessmentCentrePassMarkSettingsHandler.write(settings)

    collection.insert(doc) map {
      case _ => ()
    }
  }
}
