/*
 * Copyright 2018 HM Revenue & Customs
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

package repositories.fsb

import factories.DateTimeFactory
import model.ApplicationRoute.ApplicationRoute
import model.EvaluationResults.{ Amber, Green, Red }
import model.Exceptions.{ AlreadyEvaluatedForSchemeException, ApplicationNotFound }
import model.ProgressStatuses.{ ELIGIBLE_FOR_JOB_OFFER, FSB_AWAITING_ALLOCATION }
import model._
import model.command.ApplicationForProgression
import model.persisted.fsb.ScoresAndFeedback
import model.persisted.{ FsbSchemeResult, FsbTestGroup, SchemeEvaluationResult }
import org.joda.time.DateTime
import reactivemongo.api.DB
import reactivemongo.bson.{ BSON, BSONArray, BSONDocument, BSONObjectID }
import repositories._
import repositories.assessmentcentre.AssessmentCentreRepository
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait FsbRepository {
  def nextApplicationReadyForFsbEvaluation: Future[Option[UniqueIdentifier]]
  def nextApplicationForFsbOrJobOfferProgression(batchSize: Int): Future[Seq[ApplicationForProgression]]
  def progressToFsb(application: ApplicationForProgression): Future[Unit]
  def progressToJobOffer(application: ApplicationForProgression): Future[Unit]
  def saveResult(applicationId: String, result: SchemeEvaluationResult): Future[Unit]
  def findScoresAndFeedback(applicationId: String): Future[Option[ScoresAndFeedback]]
  def saveScoresAndFeedback(applicationId: String, scoresAndFeedback: ScoresAndFeedback): Future[Unit]
  def updateResult(applicationId: String, result: SchemeEvaluationResult): Future[Unit]
  def addFsbProgressStatuses(applicationId: String, progressStatuses: List[(String, DateTime)]): Future[Unit]
  def updateCurrentSchemeStatus(applicationId: String, newCurrentSchemeStatus: Seq[SchemeEvaluationResult]): Future[Unit]
  def findByApplicationId(applicationId: String): Future[Option[FsbTestGroup]]
  def findByApplicationIds(applicationIds: List[String], schemeId: Option[SchemeId]): Future[List[FsbSchemeResult]]
  def nextApplicationFailedAtFsb(batchSize: Int): Future[Seq[ApplicationForProgression]]
  def removeTestGroup(applicationId: String): Future[Unit]
}

class FsbMongoRepository(val dateTimeFactory: DateTimeFactory)(implicit mongo: () => DB) extends
  ReactiveRepository[FsbTestGroup, BSONObjectID](CollectionNames.APPLICATION, mongo, FsbTestGroup.jsonFormat,
    ReactiveMongoFormats.objectIdFormats) with FsbRepository with RandomSelection with CurrentSchemeStatusHelper with ReactiveRepositoryHelpers
  with CommonBSONDocuments {

  private val APPLICATION_ID = "applicationId"
  private val FSB_TEST_GROUPS = "testGroups.FSB"

  override def nextApplicationReadyForFsbEvaluation: Future[Option[UniqueIdentifier]] = {
    val query =
      BSONDocument(
        s"applicationStatus" -> ApplicationStatus.FSB.toString,
        s"progress-status.${ProgressStatuses.FSB_RESULT_ENTERED}" -> true,
        s"progress-status.${ProgressStatuses.FSB_FAILED}" -> BSONDocument("$exists" -> false),
        s"progress-status.${ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER}" -> BSONDocument("$exists" -> false)
      )

    selectOneRandom[BSONDocument](query).map(_.map(doc => doc.getAs[UniqueIdentifier]("applicationId").get)
    )
  }

  def nextApplicationFailedAtFsb(batchSize: Int): Future[Seq[ApplicationForProgression]] = {
    import AssessmentCentreRepository.applicationForFsacBsonReads

    val predicate = BSONDocument(
      "applicationStatus" -> ApplicationStatus.FSB,
      s"progress-status.${ProgressStatuses.FSB_FAILED}" -> true,
      s"progress-status.${ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER}" -> BSONDocument("$ne" -> true),
      "currentSchemeStatus.result" -> Red.toString,
      "currentSchemeStatus.result" -> BSONDocument("$nin" -> BSONArray(Green.toString, Amber.toString))
    )

    selectRandom[BSONDocument](predicate, batchSize).map(_.map(doc => doc: ApplicationForProgression))
  }

  def nextApplicationForFsbOrJobOfferProgression(batchSize: Int): Future[Seq[ApplicationForProgression]] = {
    import AssessmentCentreRepository.applicationForFsacBsonReads
    val xdipQuery = (route: ApplicationRoute) => BSONDocument(
      "applicationRoute" -> route,
      "applicationStatus" -> ApplicationStatus.SIFT,
      s"progress-status.${ProgressStatuses.SIFT_COMPLETED}" -> true,
      "currentSchemeStatus" -> BSONDocument("$elemMatch" -> BSONDocument("result" -> Green.toString))
    )

    val query = BSONDocument("$or" -> BSONArray(
      BSONDocument(
        "applicationStatus" -> ApplicationStatus.ASSESSMENT_CENTRE,
        s"progress-status.${ProgressStatuses.ASSESSMENT_CENTRE_PASSED}" -> true
      ),
      BSONDocument(
        "applicationStatus" -> ApplicationStatus.FSB,
        s"progress-status.${ProgressStatuses.FSB_FAILED}" -> true,
        s"progress-status.${ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED}" -> BSONDocument("$exists" -> false)
      ),
      BSONDocument(
        "applicationRoute" -> ApplicationRoute.SdipFaststream,
        "applicationStatus" -> ApplicationStatus.ASSESSMENT_CENTRE,
        s"progress-status.${ProgressStatuses.ASSESSMENT_CENTRE_FAILED_SDIP_GREEN_NOTIFIED}" -> true
      ),
      BSONDocument(
        "applicationRoute" -> ApplicationRoute.SdipFaststream,
        "applicationStatus" -> ApplicationStatus.SIFT,
        s"progress-status.${ProgressStatuses.SIFT_FASTSTREAM_FAILED_SDIP_GREEN}" -> true
      ),
      xdipQuery(ApplicationRoute.Sdip),
      xdipQuery(ApplicationRoute.Edip)
    ))

    selectRandom[BSONDocument](query, batchSize).map(_.map(doc => doc: ApplicationForProgression))
  }

  def progressToFsb(application: ApplicationForProgression): Future[Unit] = {
    val query = BSONDocument("applicationId" -> application.applicationId)
    val validator = singleUpdateValidator(application.applicationId, actionDesc = "progressing to fsb awaiting allocation")

    collection.update(query, BSONDocument("$set" ->
      applicationStatusBSON(FSB_AWAITING_ALLOCATION)
    )) map validator
  }

  def progressToJobOffer(application: ApplicationForProgression): Future[Unit] = {
    val query = BSONDocument("applicationId" -> application.applicationId)
    val validator = singleUpdateValidator(application.applicationId, actionDesc = "progressing to eligible for job offer")

    collection.update(query, BSONDocument("$set" ->
      applicationStatusBSON(ELIGIBLE_FOR_JOB_OFFER)
    )) map validator
  }

  override def saveResult(applicationId: String, result: SchemeEvaluationResult): Future[Unit] = {
    val selector = BSONDocument("$and" -> BSONArray(
      BSONDocument(APPLICATION_ID -> applicationId),
      BSONDocument(
        s"$FSB_TEST_GROUPS.evaluation.result.schemeId" -> BSONDocument("$nin" -> BSONArray(result.schemeId.value))
      )
    ))

    val modifier = BSONDocument(
      "$addToSet" -> BSONDocument(s"$FSB_TEST_GROUPS.evaluation.result" -> result)
    )
    val message = s"Fsb evaluation already done for application $applicationId for scheme ${result.schemeId}"
    val validator = singleUpdateValidator(
      applicationId, actionDesc = s"saving fsb assessment result $result", AlreadyEvaluatedForSchemeException(message)
    )
    collection.update(selector, modifier) map validator
  }

  override def findScoresAndFeedback(applicationId: String): Future[Option[ScoresAndFeedback]] = {
    val query = BSONDocument(APPLICATION_ID -> applicationId)
    val projection = BSONDocument(s"$FSB_TEST_GROUPS.scoresAndFeedback" -> true)

    collection.find(query, projection).one[BSONDocument].map { docOpt =>
      docOpt.flatMap { doc =>
        for {
          testGroups <- doc.getAs[BSONDocument]("testGroups")
          fsb <- testGroups.getAs[BSONDocument]("FSB")
          scoresAndFeedback <- fsb.getAs[ScoresAndFeedback]("scoresAndFeedback")
        } yield scoresAndFeedback
      }
    }
  }

  override def saveScoresAndFeedback(applicationId: String, scoresAndFeedback: ScoresAndFeedback): Future[Unit] = {
    val query = BSONDocument(APPLICATION_ID -> applicationId)
    val modifier = BSONDocument(
      "$set" -> BSONDocument(
        s"$FSB_TEST_GROUPS.scoresAndFeedback" -> scoresAndFeedback
      )
    )
    val validator = singleUpdateValidator(applicationId, actionDesc = s"saving fsb scores and feedback")
    collection.update(query, modifier) map validator
  }

  override def updateResult(applicationId: String, result: SchemeEvaluationResult): Future[Unit] = {
    val saveEvaluationResultsDoc = BSONDocument(s"$FSB_TEST_GROUPS.evaluation.result" -> result)
    val removeDoc = BSONDocument(
      "$pull" -> BSONDocument(s"$FSB_TEST_GROUPS.evaluation.result" -> BSONDocument("schemeId" -> result.schemeId.value))
    )
    val setDoc = BSONDocument("$addToSet" -> saveEvaluationResultsDoc)

    val removePredicate = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument(
        s"$FSB_TEST_GROUPS.evaluation.result.schemeId" -> BSONDocument("$in" -> BSONArray(result.schemeId.value))
      )
    ))
    val setPredicate = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument(
        s"$FSB_TEST_GROUPS.evaluation.result.schemeId" -> BSONDocument("$nin" -> BSONArray(result.schemeId.value))
      )
    ))

    val validator = singleUpdateValidator(applicationId, s"Fixing FSB results for ${result.schemeId}", ApplicationNotFound(applicationId))

    for {
      _ <- collection.update(removePredicate, removeDoc) map validator
      _ <- collection.update(setPredicate, setDoc) map validator
    } yield ()
  }

  override def updateCurrentSchemeStatus(applicationId: String, newCurrentSchemeStatus: Seq[SchemeEvaluationResult]): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val update = BSONDocument(
      "$set" -> BSONDocument("currentSchemeStatus" -> newCurrentSchemeStatus)
    )

    val validator = singleUpdateValidator(
      applicationId, actionDesc = s"Updating current scheme status"
    )

    collection.update(query, update) map validator
  }

  override def addFsbProgressStatuses(applicationId: String, progressStatuses: List[(String, DateTime)]): Future[Unit] = {
    require(progressStatuses.nonEmpty, "Progress statuses to add must be specified")

    val query = BSONDocument("applicationId" -> applicationId)

    val updateSubDoc = progressStatuses.map { case (progressStatus, progressStatusTimestamp) =>
      BSONDocument(
        s"fsb-progress-status.$progressStatus" -> true,
        s"fsb-progress-status-timestamp.$progressStatus" -> progressStatusTimestamp
      )
    }.reduce(_ ++ _)

    val update = BSONDocument("$set" -> updateSubDoc)

    val validator = singleUpdateValidator(applicationId, actionDesc = "adding fsb progress statuses")

    collection.update(query, update) map validator
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
          case Some(fsbSchemeResult) =>
            schemeId match {
              case Some(scheme) => filterBySchemeId(list, fsbSchemeResult, scheme)
              case None => list :+ fsbSchemeResult
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

  def removeTestGroup(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val update = BSONDocument("$unset" -> BSONDocument(FSB_TEST_GROUPS -> ""))

    collection.update(query, update).map(_ => ())
  }
}
