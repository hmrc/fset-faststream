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

import factories.DateTimeFactory
import model.EvaluationResults.{ Amber, Green, Red }
import model.ProgressStatuses.{ ELIGIBLE_FOR_JOB_OFFER, _ }
import model.command.ApplicationForProgression
import model.persisted.FsbTestGroup
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import repositories.assessmentcentre.AssessmentCentreRepository
import repositories.{ CollectionNames, CommonBSONDocuments, CurrentSchemeStatusHelper, RandomSelection, ReactiveRepositoryHelpers }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


trait FinalOutcomeRepository extends CurrentSchemeStatusHelper {
  def nextApplicationForFinalFailureNotification(batchSize: Int): Future[Seq[ApplicationForProgression]]

  def nextApplicationForFinalSuccessNotification(batchSize: Int): Future[Seq[ApplicationForProgression]]

  def progressToFinalFailureNotified(application: ApplicationForProgression): Future[Unit]

  def progressToJobOfferNotified(application: ApplicationForProgression): Future[Unit]
}

class FinaOutcomeMongoRepository(val dateTimeFactory: DateTimeFactory)(implicit mongo: () => DB) extends
  ReactiveRepository[FsbTestGroup, BSONObjectID](CollectionNames.APPLICATION, mongo, FsbTestGroup.jsonFormat,
    ReactiveMongoFormats.objectIdFormats) with FinalOutcomeRepository with RandomSelection
  with ReactiveRepositoryHelpers with CommonBSONDocuments {


  private val FinalFailedStatuses = Seq(
    ASSESSMENT_CENTRE_FAILED,
    FSB_FAILED
  )

  private val FinalFailedNotifiedStatuses = Seq(
    ASSESSMENT_CENTRE_FAILED_NOTIFIED,
    FSB_FAILED_NOTIFIED
  )

  def nextApplicationForFinalFailureNotification(batchSize: Int): Future[Seq[ApplicationForProgression]] = {
    import AssessmentCentreRepository.applicationForFsacBsonReads

    val queryStatus = BSONDocument("$or" -> BSONArray(
      FinalFailedStatuses.map { failStatus =>
        val notificationStatus = FinalFailedNotifiedStatuses
          .find(notifiedState => notifiedState.applicationStatus == failStatus.applicationStatus)
          .getOrElse(sys.error(s"Could not find relevant notification state for $failStatus"))
        BSONDocument(
          "applicationStatus" -> failStatus.applicationStatus,
          s"progress-status.$failStatus" -> true,
          s"progress-status.$notificationStatus" -> BSONDocument("$ne" -> true)

        )
      }
    ))
    val queryAllRed = BSONDocument(
      "currentSchemeStatus.result" -> Red.toString,
      "currentSchemeStatus.result" -> BSONDocument("$nin" -> BSONArray(Green.toString, Amber.toString))
    )
    val query = BSONDocument("$and" -> BSONArray(queryStatus, queryAllRed))
    selectRandom[BSONDocument](query, batchSize).map(_.map(doc => doc: ApplicationForProgression))
  }

  def nextApplicationForFinalSuccessNotification(batchSize: Int): Future[Seq[ApplicationForProgression]] = {
    import AssessmentCentreRepository.applicationForFsacBsonReads

    val query = BSONDocument(
      "applicationStatus" -> ELIGIBLE_FOR_JOB_OFFER.applicationStatus,
      s"progress-status.$ELIGIBLE_FOR_JOB_OFFER" -> true,
      s"progress-status.$ELIGIBLE_FOR_JOB_OFFER_NOTIFIED" -> BSONDocument("$ne" -> true)
    )

    selectRandom[BSONDocument](query, batchSize).map(_.map(doc => doc: ApplicationForProgression))
  }

  def progressToFinalFailureNotified(application: ApplicationForProgression): Future[Unit] = {
    val query = BSONDocument("applicationId" -> application.applicationId)
    val validator = singleUpdateValidator(application.applicationId, actionDesc = "progressing to final failure")

    val finalNotifiedState = FinalFailedNotifiedStatuses.find(_.applicationStatus == application.applicationStatus)
      .getOrElse(sys.error(s"Unexpected status for progression to final failure: ${application.applicationStatus}"))

    collection.update(query, BSONDocument("$set" -> applicationStatusBSON(finalNotifiedState))) map validator
  }

  def progressToJobOfferNotified(application: ApplicationForProgression): Future[Unit] = {
    val query = BSONDocument("applicationId" -> application.applicationId)
    val validator = singleUpdateValidator(application.applicationId, actionDesc = "progressing to job offer notified")

    collection.update(query, BSONDocument("$set" ->
      applicationStatusBSON(ELIGIBLE_FOR_JOB_OFFER_NOTIFIED)
    )) map validator
  }

}
