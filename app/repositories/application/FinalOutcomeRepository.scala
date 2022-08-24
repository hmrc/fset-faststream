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
import model.EvaluationResults.{Amber, Green}
import model.ProgressStatuses
import model.ProgressStatuses._
import model.command.ApplicationForProgression
import model.persisted.FsbTestGroup
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.bson.collection.immutable.Document
import repositories.{CollectionNames, CommonBSONDocuments, CurrentSchemeStatusHelper, RandomSelection, ReactiveRepositoryHelpers}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, CollectionFactory, PlayMongoRepository}

import javax.inject.{Inject, Singleton}
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
class FinalOutcomeMongoRepository @Inject() (val dateTimeFactory: DateTimeFactory,
                                            mongo: MongoComponent
                                           )
  extends PlayMongoRepository[FsbTestGroup](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongo,
    domainFormat = FsbTestGroup.jsonFormat,
    indexes = Nil
  ) with FinalOutcomeRepository with RandomSelection with ReactiveRepositoryHelpers with CommonBSONDocuments {

  // Additional collections configured to work with the appropriate domainFormat and automatically register the
  // codec to work with BSON serialization
  val applicationForProgressionCollection: MongoCollection[ApplicationForProgression] =
  CollectionFactory.collection(
    collectionName = CollectionNames.APPLICATION,
    db = mongo.database,
    domainFormat = ApplicationForProgression.applicationForProgressionFormat
  )

  private case class FinalState(failed: ProgressStatuses.ProgressStatus, notified: ProgressStatuses.ProgressStatus,
                                currentSchemeStatusQuery: Document)

  val allRedOrWithdrawnQuery = Document(
    "currentSchemeStatus.result" -> Document("$nin" -> BsonArray(Green.toString, Amber.toString))
  )

  val doNothingQuery = Document.empty

  private val FailedStatuses = Seq(
    FinalState(ASSESSMENT_CENTRE_FAILED, ASSESSMENT_CENTRE_FAILED_NOTIFIED, allRedOrWithdrawnQuery),
    FinalState(ASSESSMENT_CENTRE_FAILED_SDIP_GREEN, ASSESSMENT_CENTRE_FAILED_SDIP_GREEN_NOTIFIED, doNothingQuery),
    FinalState(ALL_FSBS_AND_FSACS_FAILED, ALL_FSBS_AND_FSACS_FAILED_NOTIFIED, allRedOrWithdrawnQuery),
    FinalState(FAILED_AT_SIFT, FAILED_AT_SIFT_NOTIFIED, allRedOrWithdrawnQuery)
  )

  override def nextApplicationForFinalFailureNotification(batchSize: Int): Future[Seq[ApplicationForProgression]] = {
    val query = Document("$or" ->
      FailedStatuses.map { status =>
        Document("$and" -> BsonArray(
          Document(
            "applicationStatus" -> Codecs.toBson(status.failed.applicationStatus),
            s"progress-status.${status.failed}" -> true,
            s"progress-status.${status.notified}" -> Document("$ne" -> true)
          ),
          status.currentSchemeStatusQuery
        ))
      }
    )

    selectRandom[ApplicationForProgression](applicationForProgressionCollection, query, batchSize)
  }

  override def nextApplicationForFinalSuccessNotification(batchSize: Int): Future[Seq[ApplicationForProgression]] = {
    val query = Document(
      "applicationStatus" -> Codecs.toBson(ELIGIBLE_FOR_JOB_OFFER.applicationStatus),
      s"progress-status.$ELIGIBLE_FOR_JOB_OFFER" -> true,
      s"progress-status.$ELIGIBLE_FOR_JOB_OFFER_NOTIFIED" -> Document("$ne" -> true)
    )

    selectRandom[ApplicationForProgression](applicationForProgressionCollection, query, batchSize)
  }

  override def progressToFinalFailureNotified(application: ApplicationForProgression): Future[Unit] = {
    val query = Document("applicationId" -> application.applicationId)
    val validator = singleUpdateValidator(application.applicationId, actionDesc = "progressing to final failure")

    val finalNotifiedState = FailedStatuses.find(_.notified.applicationStatus == application.applicationStatus)
      .getOrElse(sys.error(s"Unexpected status for progression to final failure: ${application.applicationStatus}"))

    collection.updateOne(query, Document("$set" -> applicationStatusBSON(finalNotifiedState.notified))).toFuture() map validator
  }

  override def progressToAssessmentCentreFailedSdipGreenNotified(application: ApplicationForProgression): Future[Unit] = {
    val query = Document("applicationId" -> application.applicationId)
    val notifiedState = ASSESSMENT_CENTRE_FAILED_SDIP_GREEN_NOTIFIED
    val msg = s"progressing candidate to $notifiedState"
    val validator = singleUpdateValidator(application.applicationId, actionDesc = msg)

    collection.updateOne(query, Document("$set" -> applicationStatusBSON(notifiedState))).toFuture() map validator
  }

  override def progressToJobOfferNotified(application: ApplicationForProgression): Future[Unit] = {
    val query = Document("applicationId" -> application.applicationId)
    val validator = singleUpdateValidator(application.applicationId, actionDesc = "progressing to job offer notified")

    collection.updateOne(query, Document("$set" ->
      applicationStatusBSON(ELIGIBLE_FOR_JOB_OFFER_NOTIFIED)
    )).toFuture() map validator
  }
}
