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
import model.{ ApplicationStatus, EvaluationResults, Scheme }
import model.command.ApplicationForSift
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import repositories.{ CollectionNames, RandomSelection, ReactiveRepositoryHelpers }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats


trait AssessmentCentreRepository extends RandomSelection with ReactiveRepositoryHelpers {
  this: ReactiveRepository[_, _] =>

  def dateTime: DateTimeFactory
  def nextApplicationForAssessmentCentre(batchSize: Int)

}

class AssessmentCentreMongoRepository (
  val dateTime: DateTimeFactory,
  val siftableSchemes: Seq[Scheme]
)(implicit mongo: () => DB)
  extends ReactiveRepository[ApplicationForSift, BSONObjectID](CollectionNames.APPLICATION, mongo,
    ApplicationForSift.applicationForSiftFormat,
    ReactiveMongoFormats.objectIdFormats
) with AssessmentCentreRepository {

  def nextApplicationForAssessmentCentre(batchSize: Int) = {

    val hasSiftableSchemeInPhase3Query = BSONDocument(s"testGroups.PHASE3.evaluation.result" -> BSONDocument("$elemMatch" -> BSONDocument(
        "schemeId" -> BSONDocument("$in" -> siftableSchemes.map(_.id)),
        "result " -> EvaluationResults.Green.toPassmark
      )))

    val noSiftRequiredQuery = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED),
      BSONDocument("$not" -> hasSiftableSchemeInPhase3Query),
      BSONDocument(s"testGroups.PHASE3.evaluation.result" -> BSONDocument("$elemMatch" ->
        BSONDocument("result " -> EvaluationResults.Green.toPassmark)
      ))
    ))

    val siftCompletedQuery = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> ApplicationStatus.SIFT),
      BSONDocument()
  ))
  }

}