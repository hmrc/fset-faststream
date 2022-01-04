/*
 * Copyright 2022 HM Revenue & Customs
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
import javax.inject.{ Inject, Singleton }
import model.EvaluationResults.{ Amber, Green }
import model.ProgressStatuses
import model.ProgressStatuses.{ ELIGIBLE_FOR_JOB_OFFER, _ }
import model.command.ApplicationForProgression
import model.persisted.FsbTestGroup
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories.assessmentcentre.AssessmentCentreRepository
import repositories.{ CollectionNames, CommonBSONDocuments, CurrentSchemeStatusHelper, RandomSelection, ReactiveRepositoryHelpers }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait FinalOutcomeRepository extends CurrentSchemeStatusHelper {
  def nextApplicationForFinalFailureNotification(batchSize: Int): Future[Seq[ApplicationForProgression]]
  def nextApplicationForFinalSuccessNotification(batchSize: Int): Future[Seq[ApplicationForProgression]]
  def progressToFinalFailureNotified(application: ApplicationForProgression): Future[Unit]
  def progressToAssessmentCentreFailedSdipGreenNotified(application: ApplicationForProgression): Future[Unit]
  def progressToJobOfferNotified(application: ApplicationForProgression): Future[Unit]
}

@Singleton
class FinaOutcomeMongoRepository @Inject() (val dateTimeFactory: DateTimeFactory,
                                            mongoComponent: ReactiveMongoComponent
                                            )
  extends ReactiveRepository[FsbTestGroup, BSONObjectID](
    CollectionNames.APPLICATION,
    mongoComponent.mongoConnector.db,
    FsbTestGroup.jsonFormat,
    ReactiveMongoFormats.objectIdFormats
  ) with FinalOutcomeRepository with RandomSelection with ReactiveRepositoryHelpers with CommonBSONDocuments {

  private case class FinalState(failed: ProgressStatuses.ProgressStatus, notified: ProgressStatuses.ProgressStatus,
                                currentSchemeStatusQuery: BSONDocument)

  val allRedOrWithdrawnQuery = BSONDocument(
    "currentSchemeStatus.result" -> BSONDocument("$nin" -> BSONArray(Green.toString, Amber.toString))
  )

  val doNothingQuery = BSONDocument()

  private val FailedStatuses = Seq(
    FinalState(ASSESSMENT_CENTRE_FAILED, ASSESSMENT_CENTRE_FAILED_NOTIFIED, allRedOrWithdrawnQuery),
    FinalState(ASSESSMENT_CENTRE_FAILED_SDIP_GREEN, ASSESSMENT_CENTRE_FAILED_SDIP_GREEN_NOTIFIED, doNothingQuery),
    FinalState(ALL_FSBS_AND_FSACS_FAILED, ALL_FSBS_AND_FSACS_FAILED_NOTIFIED, allRedOrWithdrawnQuery),
    FinalState(FAILED_AT_SIFT, FAILED_AT_SIFT_NOTIFIED, allRedOrWithdrawnQuery)
  )

  override def nextApplicationForFinalFailureNotification(batchSize: Int): Future[Seq[ApplicationForProgression]] = {
    import AssessmentCentreRepository.applicationForFsacBsonReads

    val query = BSONDocument("$or" -> BSONArray(
      FailedStatuses.map { status =>
        BSONDocument("$and" -> BSONArray(
          BSONDocument(
            "applicationStatus" -> status.failed.applicationStatus,
            s"progress-status.${status.failed}" -> true,
            s"progress-status.${status.notified}" -> BSONDocument("$ne" -> true)
          ),
          status.currentSchemeStatusQuery
        ))
      }
    ))

    selectRandom[BSONDocument](query, batchSize).map(_.map(doc => doc: ApplicationForProgression))
  }

  override def nextApplicationForFinalSuccessNotification(batchSize: Int): Future[Seq[ApplicationForProgression]] = {
    import AssessmentCentreRepository.applicationForFsacBsonReads

    val query = BSONDocument(
      "applicationStatus" -> ELIGIBLE_FOR_JOB_OFFER.applicationStatus,
      s"progress-status.$ELIGIBLE_FOR_JOB_OFFER" -> true,
      s"progress-status.$ELIGIBLE_FOR_JOB_OFFER_NOTIFIED" -> BSONDocument("$ne" -> true)
    )

    selectRandom[BSONDocument](query, batchSize).map(_.map(doc => doc: ApplicationForProgression))
  }

  override def progressToFinalFailureNotified(application: ApplicationForProgression): Future[Unit] = {
    val query = BSONDocument("applicationId" -> application.applicationId)
    val validator = singleUpdateValidator(application.applicationId, actionDesc = "progressing to final failure")

    val finalNotifiedState = FailedStatuses.find(_.notified.applicationStatus == application.applicationStatus)
      .getOrElse(sys.error(s"Unexpected status for progression to final failure: ${application.applicationStatus}"))

    collection.update(ordered = false).one(query, BSONDocument("$set" -> applicationStatusBSON(finalNotifiedState.notified))) map validator
  }

  override def progressToAssessmentCentreFailedSdipGreenNotified(application: ApplicationForProgression): Future[Unit] = {
    val query = BSONDocument("applicationId" -> application.applicationId)
    val notifiedState = ASSESSMENT_CENTRE_FAILED_SDIP_GREEN_NOTIFIED
    val msg = s"progressing candidate to $notifiedState"
    val validator = singleUpdateValidator(application.applicationId, actionDesc = msg)

    collection.update(ordered = false).one(query, BSONDocument("$set" -> applicationStatusBSON(notifiedState))) map validator
  }

  override def progressToJobOfferNotified(application: ApplicationForProgression): Future[Unit] = {
    val query = BSONDocument("applicationId" -> application.applicationId)
    val validator = singleUpdateValidator(application.applicationId, actionDesc = "progressing to job offer notified")

    collection.update(ordered = false).one(query, BSONDocument("$set" ->
      applicationStatusBSON(ELIGIBLE_FOR_JOB_OFFER_NOTIFIED)
    )) map validator
  }
}
