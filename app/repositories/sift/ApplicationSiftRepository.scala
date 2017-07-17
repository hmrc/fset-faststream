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

package repositories.sift

import factories.DateTimeFactory
import model.ApplicationStatus.ApplicationStatus
import model.command.ApplicationForSift
import model._
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import repositories.{ CollectionNames, CommonBSONDocuments, RandomSelection, ReactiveRepositoryHelpers }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.Future

trait ApplicationSiftRepository extends RandomSelection with ReactiveRepositoryHelpers {
  this: ReactiveRepository[_, _] =>

  def thisApplicationStatus: ApplicationStatus
  def dateTime: DateTimeFactory
  def siftableSchemes: Seq[Scheme]

  def nextApplicationsForSift(maxBatchSize: Int): Future[List[ApplicationForSift]]
  def progressApplicationToSift(application: ApplicationForSift): Future[Unit]

}

class ApplicationSiftMongoRepository(
  val dateTime: DateTimeFactory,
  val siftableSchemes: Seq[Scheme]
)(implicit mongo: () => DB)
  extends ReactiveRepository[ApplicationForSift, BSONObjectID](CollectionNames.APPLICATION, mongo,
    ApplicationForSift.applicationForSiftFormat,
    ReactiveMongoFormats.objectIdFormats
) with ApplicationSiftRepository {

  val thisApplicationStatus = ApplicationStatus.SIFT
  val prevPhase = ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED
  val prevTestGroup = ApplicationStatus.PHASE3_TESTS

  val eligibleForSiftQuery = BSONDocument("$and" -> BSONArray(
    BSONDocument("applicationStatus" -> prevPhase),
    BSONDocument(s"testGroups.$prevTestGroup.evaluation.result" -> BSONDocument("$elemMatch" ->
      BSONDocument("schemeId" -> BSONDocument("$in" -> siftableSchemes.map(_.id))),
      "result" ->EvaluationResults.Green.toPassmark
    ))
  ))

  def nextApplicationsForSift(batchSize: Int): Future[List[ApplicationForSift]] = {
    selectRandom[ApplicationForSift](eligibleForSiftQuery, batchSize)
  }

  def progressApplicationToSift(application: ApplicationForSift): Future[Unit] = {
    val query = BSONDocument("applicationId" -> application.applicationId) ++ eligibleForSiftQuery
    val update = BSONDocument("applicationStatus" -> ApplicationStatus.SIFT)

    val validator = singleUpdateValidator(application.applicationId, "progressing to sift stage")

    collection.update(query, update) map validator
  }
}