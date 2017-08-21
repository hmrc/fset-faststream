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

package repositories.application

import model.Exceptions.AlreadyEvaluatedForSchemeException
import model.SchemeId
import model.persisted.{ FsbSchemeResult, FsbTestGroup, SchemeEvaluationResult }
import reactivemongo.api.DB
import reactivemongo.bson.{ BSON, BSONArray, BSONDocument, BSONObjectID }
import repositories._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait FsbTestGroupRepository {
  def save(applicationId: String, result: SchemeEvaluationResult): Future[Unit]

  def findByApplicationId(applicationId: String): Future[Option[FsbTestGroup]]

  def findByApplicationIds(applicationIds: List[String], schemeId: Option[SchemeId]): Future[List[FsbSchemeResult]]
}

class FsbTestGroupMongoRepository(implicit mongo: () => DB) extends
  ReactiveRepository[FsbTestGroup, BSONObjectID](CollectionNames.APPLICATION, mongo, FsbTestGroup.jsonFormat,
    ReactiveMongoFormats.objectIdFormats) with FsbTestGroupRepository with CurrentSchemeStatusHelper with ReactiveRepositoryHelpers {

  private val APPLICATION_ID = "applicationId"
  private val FSB_TEST_GROUPS = "testGroups.FSB"

  override def save(applicationId: String, result: SchemeEvaluationResult): Future[Unit] = {
    val selector = BSONDocument("$and" -> BSONArray(
      BSONDocument(APPLICATION_ID -> applicationId),
      BSONDocument(
        s"$FSB_TEST_GROUPS.evaluation.result.schemeId" -> BSONDocument("$nin" -> BSONArray(result.schemeId.value))
      )
    ))

    val modifier = BSONDocument(
      "$addToSet" -> BSONDocument(s"$FSB_TEST_GROUPS.evaluation.result" -> result),
      "$set" -> currentSchemeStatusBSON(Seq(result))
    )
    val message = s"Fsb evaluation already done for application $applicationId for scheme ${result.schemeId}"
    val validator = singleUpdateValidator(
      applicationId, actionDesc = s"saving fsb assessment result $result", AlreadyEvaluatedForSchemeException(message)
    )
    collection.update(selector, modifier) map validator
  }

  override def findByApplicationId(applicationId: String): Future[Option[FsbTestGroup]] = {
    val query = BSONDocument(APPLICATION_ID -> applicationId)
    val projection = BSONDocument(FSB_TEST_GROUPS -> 1)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) =>
        for {
          testGroups <- document.getAs[BSONDocument]("testGroups")
          fsb <- testGroups.getAs[FsbTestGroup]("FSB")
        } yield fsb
      case _ => None
    }
  }

  override def findByApplicationIds(applicationIds: List[String], schemeId: Option[SchemeId]): Future[List[FsbSchemeResult]] = {
    val applicationIdFilter = applicationIds.foldLeft(BSONArray())((bsonArray, applicationId) => bsonArray ++ applicationId)
    val query = BSONDocument(APPLICATION_ID -> BSONDocument("$in" -> applicationIdFilter))
    val projection = BSONDocument(FSB_TEST_GROUPS -> 1, APPLICATION_ID -> 1)

    collection.find(query, projection).cursor[BSONDocument]().collect[List]().map { documents =>
      documents.foldLeft(List[FsbSchemeResult]())((list, document) => {
        BSON.readDocument[Option[FsbSchemeResult]](document) match {
          case Some(fsbSchemeResult) => {
            schemeId match {
              case Some(scheme) => filterBySchemeId(list, fsbSchemeResult, scheme)
              case None => list :+ fsbSchemeResult
            }
          }
          case _ => list
        }
      })
    }
  }

  private def filterBySchemeId(list: List[FsbSchemeResult], fsbSchemeResult: FsbSchemeResult, schemeId: SchemeId): List[FsbSchemeResult] = {
    val applicationId = fsbSchemeResult.applicationId
    val filteredResult = fsbSchemeResult.results.filter(s => schemeId == s.schemeId) match {
      case Nil => list
      case head :: tail => list :+ FsbSchemeResult(applicationId, head :: tail)
    }
    filteredResult
  }

}
