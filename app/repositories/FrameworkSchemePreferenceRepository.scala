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

import model.{Preferences, SchemePreference}
import reactivemongo.api.DB
import reactivemongo.bson.{BSONDocument, BSONObjectID, _}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait FrameworkSchemePreferenceRepository {
  def saveSchemePreference(applicationId: String, schemePreference: SchemePreference): Future[Unit]
}

class FrameworkSchemePreferenceMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[SchemePreference, BSONObjectID](
    "application", mongo, SchemePreference.jsonFormat, ReactiveMongoFormats.objectIdFormats
  )
  with FrameworkSchemePreferenceRepository {

  override def saveSchemePreference(applicationId: String, schemePreference: SchemePreference): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val preferencesBSON = BSONDocument("$set" -> BSONDocument(
      "applicationStatus" -> "IN_PROGRESS",
      "framework-preferences" -> schemePreference
    ))
    collection.update(query, preferencesBSON, upsert = false) map {
      case _ => ()
    }
  }
}
