/*
 * Copyright 2019 HM Revenue & Customs
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

package repositories.testdata

import model.CreateApplicationRequest
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, BSONObjectID }
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories.{ CollectionNames, ReactiveRepositoryHelpers }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait ApplicationRemovalRepository {
  def remove(applicationStatus: Option[String]): Future[List[String]]
}

class ApplicationRemovalMongoRepository (implicit mongo: () => DB)
  extends ReactiveRepository[CreateApplicationRequest, BSONObjectID](CollectionNames.APPLICATION, mongo,
    CreateApplicationRequest.createApplicationRequestFormat,
    ReactiveMongoFormats.objectIdFormats) with ApplicationRemovalRepository
    with ReactiveRepositoryHelpers
{
  override def remove(applicationStatus: Option[String]): Future[List[String]] = {
    val query = applicationStatus.map(as => BSONDocument("applicationStatus" -> as)).getOrElse(BSONDocument())

    val projection = BSONDocument(
      "userId" -> true
    )

    collection.find(query, projection).cursor[BSONDocument]().collect[List]().map {
      docList => docList.map { doc => doc.getAs[String]("userId").get }
    }.map { userIds =>
      collection.remove(query).map(_.n)
      userIds
    }
  }
}