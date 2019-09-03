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

package repositories.onlinetesting

import factories.DateTimeFactory
import model.ApplicationStatus.ApplicationStatus
import model.persisted._
import reactivemongo.bson.{ BSONDocument, _ }
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories._
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

//TODO: can delete this
trait MyOnlineTestRepository extends RandomSelection with ReactiveRepositoryHelpers with CommonBSONDocuments with OnlineTestCommonBSONDocuments {
  this: ReactiveRepository[_, _] =>

  val thisApplicationStatus: ApplicationStatus
  val phaseName: String
  val dateTimeFactory: DateTimeFactory
  val expiredTestQuery: BSONDocument
  val resetStatuses: List[String]
  implicit val bsonHandler: BSONHandler[BSONDocument, T]

  type U <: Test
  type T <: TestProfile[U]

  def getTestGroup(applicationId: String, phase: String = "PHASE1"): Future[Option[T]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    phaseTestProfileByQuery(query, phase)
  }

  private def phaseTestProfileByQuery(query: BSONDocument, phase: String): Future[Option[T]] = {
    val projection = BSONDocument(s"testGroups.$phase" -> true, "_id" -> false)

    collection.find(query, projection).one[BSONDocument] map { optDocument =>
      optDocument.flatMap {_.getAs[BSONDocument]("testGroups")}
        .flatMap {_.getAs[BSONDocument](phase)}
        .map {x => bsonHandler.read(x)}
    }
  }
}
