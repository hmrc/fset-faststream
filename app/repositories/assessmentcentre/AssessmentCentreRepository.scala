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

package repositories.assessmentcentre

import factories.DateTimeFactory
import model.{ ApplicationStatus, EvaluationResults, Scheme, SchemeId }
import model.command.{ ApplicationForFsac, ApplicationForSift }
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import repositories.{ CollectionNames, RandomSelection, ReactiveRepositoryHelpers }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


trait AssessmentCentreRepository extends RandomSelection with ReactiveRepositoryHelpers {
  this: ReactiveRepository[_, _] =>

  def dateTime: DateTimeFactory
  def nextApplicationForAssessmentCentre(batchSize: Int): Future[Seq[ApplicationForFsac]]

}

class AssessmentCentreMongoRepository (
  val dateTime: DateTimeFactory,
  val siftableSchemeIds: Seq[SchemeId]
)(implicit mongo: () => DB)
  extends ReactiveRepository[ApplicationForSift, BSONObjectID](CollectionNames.APPLICATION, mongo,
    ApplicationForSift.applicationForSiftFormat,
    ReactiveMongoFormats.objectIdFormats
) with AssessmentCentreRepository {

  def nextApplicationForAssessmentCentre(batchSize: Int): Future[Seq[ApplicationForFsac]] = {
    def query = BSONDocument(
      "applicationStatus" -> ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
      "testGroups.PHASE3.evaluation.result" -> BSONDocument("$elemMatch" -> BSONDocument(
        "schemeId" -> BSONDocument("$nin" -> siftableSchemeIds),
        "result" -> EvaluationResults.Green.toPassmark
      ))
    )

    selectRandom[BSONDocument](query).map(_.map(doc => doc: ApplicationForFsac).filter { app =>
      app.evaluationResult.result.filter(_.result == EvaluationResults.Green.toPassmark).forall(s => !siftableSchemeIds.contains(s.schemeId))
    })
  }

  def progressApplicationToAssessmentCentre(app: ApplicationForFsac): Future[Unit] = {
    val query = BSONDocument("applicationId" -> app.applicationId)
    val update = BSONDocument("applicationStatus" -> ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED)

    val validator = singleUpdateValidator(app.applicationId, "progressing to assessment centre")

    collection.update(query, update) map validator
  }
}
