/*
 * Copyright 2023 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import model.ApplicationRoute.ApplicationRoute
import model.EvaluationResults.{Amber, Green, Red}
import model.Exceptions.{AlreadyEvaluatedForSchemeException, ApplicationNotFound}
import model.ProgressStatuses.{ELIGIBLE_FOR_JOB_OFFER, FSB_AWAITING_ALLOCATION}
import model._
import model.command.ApplicationForProgression
import model.persisted.fsb.ScoresAndFeedback
import model.persisted.{FsbSchemeResult, FsbTestGroup, SchemeEvaluationResult}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.{BsonArray, BsonDocument}
import org.mongodb.scala.model.Projections
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import scala.util.Try
import repositories._
import repositories.assessmentcentre.AssessmentCentreRepository

import java.time.OffsetDateTime
import scala.concurrent.{ExecutionContext, Future}

trait FsbRepository {
  def nextApplicationReadyForFsbEvaluation: Future[Option[UniqueIdentifier]]
  def nextApplicationForFsbOrJobOfferProgression(batchSize: Int): Future[Seq[ApplicationForProgression]]
  def nextApplicationForFsbOrJobOfferProgression(applicationId: String): Future[Seq[ApplicationForProgression]]
  def progressToFsb(application: ApplicationForProgression): Future[Unit]
  def progressToJobOffer(application: ApplicationForProgression): Future[Unit]
  def saveResult(applicationId: String, result: SchemeEvaluationResult): Future[Unit]
  def findScoresAndFeedback(applicationId: String): Future[Option[ScoresAndFeedback]]
  def findScoresAndFeedback(applicationIds: Seq[String]): Future[Map[String, Option[ScoresAndFeedback]]]
  def saveScoresAndFeedback(applicationId: String, scoresAndFeedback: ScoresAndFeedback): Future[Unit]
  def updateResult(applicationId: String, result: SchemeEvaluationResult): Future[Unit]
  def addFsbProgressStatuses(applicationId: String, progressStatuses: List[(String, OffsetDateTime)]): Future[Unit]
  def updateCurrentSchemeStatus(applicationId: String, newCurrentSchemeStatus: Seq[SchemeEvaluationResult]): Future[Unit]
  def findByApplicationId(applicationId: String): Future[Option[FsbTestGroup]]
  def findByApplicationIds(applicationIds: Seq[String], schemeId: Option[SchemeId]): Future[List[FsbSchemeResult]]
  def nextApplicationFailedAtFsb(batchSize: Int): Future[Seq[ApplicationForProgression]]
  def nextApplicationFailedAtFsb(applicationId: String): Future[Seq[ApplicationForProgression]]
  def removeTestGroup(applicationId: String): Future[Unit]
}

@Singleton
class FsbMongoRepository @Inject() (val dateTimeFactory: DateTimeFactory,
                                    mongo: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[FsbTestGroup](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongo,
    domainFormat = FsbTestGroup.jsonFormat,
    indexes = Nil
  ) with FsbRepository with RandomSelection with CurrentSchemeStatusHelper with ReactiveRepositoryHelpers with CommonBSONDocuments {

  private val APPLICATION_ID = "applicationId"
  private val FSB_TEST_GROUPS = "testGroups.FSB"

  override def nextApplicationReadyForFsbEvaluation: Future[Option[UniqueIdentifier]] = {
    val query =
      Document(
        s"applicationStatus" -> ApplicationStatus.FSB.toBson,
        s"progress-status.${ProgressStatuses.FSB_RESULT_ENTERED}" -> true,
        s"progress-status.${ProgressStatuses.FSB_FAILED}" -> Document("$exists" -> false),
        s"progress-status.${ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER}" -> Document("$exists" -> false)
      )

    selectOneRandom[UniqueIdentifier](query)(doc => UniqueIdentifier(getAppId(doc)), ec)
  }

  val commonFailedAtFsbPredicate = Document(
    "applicationStatus" -> ApplicationStatus.FSB.toBson,
    s"progress-status.${ProgressStatuses.FSB_FAILED}" -> true,
    s"progress-status.${ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER}" -> Document("$ne" -> true),
    s"progress-status.${ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED}" -> Document("$ne" -> true),
    "currentSchemeStatus.result" -> Red.toString,
    "currentSchemeStatus.result" -> Document("$nin" -> BsonArray(Green.toString, Amber.toString))
  )

  override def nextApplicationFailedAtFsb(batchSize: Int): Future[Seq[ApplicationForProgression]] = {
    selectRandom[ApplicationForProgression](commonFailedAtFsbPredicate, batchSize)(
      doc => AssessmentCentreRepository.applicationForFsacBsonReads(doc), ec)
  }

  override def nextApplicationFailedAtFsb(applicationId: String): Future[Seq[ApplicationForProgression]] = {
    import AssessmentCentreRepository.applicationForFsacBsonReads

    val predicate = Document(
      "applicationId" -> applicationId
    ) ++ commonFailedAtFsbPredicate

    collection.find[Document](predicate).headOption().map {
      case Some(doc) => List(applicationForFsacBsonReads(doc))
      case _ => Nil
    }
  }

  //scalastyle:off method.length
  override def nextApplicationForFsbOrJobOfferProgression(batchSize: Int): Future[Seq[ApplicationForProgression]] = {
    val xdipQuery = (route: ApplicationRoute) => Document(
      "applicationRoute" -> route.toBson,
      "applicationStatus" -> ApplicationStatus.SIFT.toBson,
      s"progress-status.${ProgressStatuses.SIFT_COMPLETED}" -> true,
      "currentSchemeStatus" -> Document("$elemMatch" -> Document("result" -> Green.toString))
    )

    val query = Document("$or" -> BsonArray(
      Document(
        "applicationStatus" -> ApplicationStatus.ASSESSMENT_CENTRE.toBson,
        s"progress-status.${ProgressStatuses.ASSESSMENT_CENTRE_PASSED}" -> true
      ),
      Document(
        "applicationStatus" -> ApplicationStatus.FSB.toBson,
        s"progress-status.${ProgressStatuses.FSB_FAILED}" -> true,
        s"progress-status.${ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED}" -> Document("$exists" -> false)
      ),
      Document(
        "applicationRoute" -> ApplicationRoute.SdipFaststream.toBson,
        "applicationStatus" -> ApplicationStatus.ASSESSMENT_CENTRE.toBson,
        s"progress-status.${ProgressStatuses.ASSESSMENT_CENTRE_FAILED_SDIP_GREEN_NOTIFIED}" -> true
      ),
      Document(
        "applicationRoute" -> ApplicationRoute.SdipFaststream.toBson,
        "applicationStatus" -> ApplicationStatus.SIFT.toBson,
        s"progress-status.${ProgressStatuses.SIFT_FASTSTREAM_FAILED_SDIP_GREEN}" -> true
      ),
      // All faststream schemes failed before SIFT, i.e. PHASE3, for SDIP we have submitted form and still green in SIFT
      Document("$and" -> BsonArray(
        Document("applicationRoute" -> ApplicationRoute.SdipFaststream.toBson),
        Document("applicationStatus" -> ApplicationStatus.SIFT.toBson),
        Document(s"progress-status.${ProgressStatuses.SIFT_COMPLETED}" -> true),
        // Match documents that contain the css array field with at least one element
        // with Sdip evaluated to Green
        Document("currentSchemeStatus" -> Document("$elemMatch" ->
          Document(
            "schemeId" -> "Sdip",
            "result" -> EvaluationResults.Green.toString
          )
        )),
        // Match documents that also contain the css array field with no non-Sdip elements
        // evaluated to Green
        Document("currentSchemeStatus" -> Document("$not" -> Document("$elemMatch" ->
          Document(
            "schemeId" -> Document("$ne" -> "Sdip"),
            "result" -> EvaluationResults.Green.toString
          )
        ))),
      )),
      xdipQuery(ApplicationRoute.Sdip),
      xdipQuery(ApplicationRoute.Edip)
    ))

    selectRandom[ApplicationForProgression](query, batchSize)(doc => AssessmentCentreRepository.applicationForFsacBsonReads(doc), ec)
  } //scalastyle:on

  override def nextApplicationForFsbOrJobOfferProgression(applicationId: String): Future[Seq[ApplicationForProgression]] = {
    val xdipQuery = (route: ApplicationRoute) => Document(
      "applicationId" -> applicationId,
      "applicationRoute" -> route.toBson,
      "applicationStatus" -> ApplicationStatus.SIFT.toBson,
      s"progress-status.${ProgressStatuses.SIFT_COMPLETED}" -> true,
      "currentSchemeStatus" -> Document("$elemMatch" -> Document("result" -> Green.toString))
    )

    val query = Document("$or" -> BsonArray(
      Document(
        "applicationId" -> applicationId,
        "applicationStatus" -> ApplicationStatus.ASSESSMENT_CENTRE.toBson,
        s"progress-status.${ProgressStatuses.ASSESSMENT_CENTRE_PASSED}" -> true
      ),
      Document(
        "applicationId" -> applicationId,
        "applicationStatus" -> ApplicationStatus.FSB.toBson,
        s"progress-status.${ProgressStatuses.FSB_FAILED}" -> true,
        s"progress-status.${ProgressStatuses.ALL_FSBS_AND_FSACS_FAILED}" -> Document("$exists" -> false)
      ),
      Document(
        "applicationId" -> applicationId,
        "applicationRoute" -> ApplicationRoute.SdipFaststream.toBson,
        "applicationStatus" -> ApplicationStatus.ASSESSMENT_CENTRE.toBson,
        s"progress-status.${ProgressStatuses.ASSESSMENT_CENTRE_FAILED_SDIP_GREEN_NOTIFIED}" -> true
      ),
      Document(
        "applicationId" -> applicationId,
        "applicationRoute" -> ApplicationRoute.SdipFaststream.toBson,
        "applicationStatus" -> ApplicationStatus.SIFT.toBson,
        s"progress-status.${ProgressStatuses.SIFT_FASTSTREAM_FAILED_SDIP_GREEN}" -> true
      ),
      xdipQuery(ApplicationRoute.Sdip),
      xdipQuery(ApplicationRoute.Edip)
    ))

    collection.find[Document](query).headOption().map {
      case Some(doc) => List(AssessmentCentreRepository.applicationForFsacBsonReads(doc))
      case _ => Nil
    }
  }

  override def progressToFsb(application: ApplicationForProgression): Future[Unit] = {
    val query = Document("applicationId" -> application.applicationId)
    val validator = singleUpdateValidator(application.applicationId, actionDesc = "progressing to fsb awaiting allocation")

    collection.updateOne(query, Document("$set" ->
      applicationStatusBSON(FSB_AWAITING_ALLOCATION)
    )).toFuture() map validator
  }

  override def progressToJobOffer(application: ApplicationForProgression): Future[Unit] = {
    val query = Document("applicationId" -> application.applicationId)
    val validator = singleUpdateValidator(application.applicationId, actionDesc = "progressing to eligible for job offer")

    collection.updateOne(query, Document("$set" ->
      applicationStatusBSON(ELIGIBLE_FOR_JOB_OFFER)
    )).toFuture() map validator
  }

  override def saveResult(applicationId: String, result: SchemeEvaluationResult): Future[Unit] = {
    val query = BsonDocument("$and" -> BsonArray(
      BsonDocument(APPLICATION_ID -> applicationId),
      BsonDocument(
        s"$FSB_TEST_GROUPS.evaluation.result.schemeId" -> BsonDocument("$nin" -> BsonArray(result.schemeId.value))
      )
    ))

    val update = BsonDocument(
      "$addToSet" -> BsonDocument(s"$FSB_TEST_GROUPS.evaluation.result" -> Codecs.toBson(result))
    )
    val message = s"Fsb evaluation already done for application $applicationId for scheme ${result.schemeId}"
    val validator = singleUpdateValidator(
      applicationId, actionDesc = s"saving fsb assessment result $result", AlreadyEvaluatedForSchemeException(message) //TODO: mongo test this
    )
    collection.updateOne(query, update).toFuture() map validator
  }

  override def findScoresAndFeedback(applicationId: String): Future[Option[ScoresAndFeedback]] = {
    val query = Document("$and" -> BsonArray(
      Document(APPLICATION_ID -> applicationId),
      Document(s"$FSB_TEST_GROUPS.scoresAndFeedback" ->  Document("$exists" -> true))
    ))
    val projection = Projections.include(s"$FSB_TEST_GROUPS.scoresAndFeedback")

    collection.find[Document](query).projection(projection).headOption().map { docOpt =>
      docOpt.flatMap(processScoresAndFeedback)
    }
  }

  override def findScoresAndFeedback(applicationIds: Seq[String]): Future[Map[String, Option[ScoresAndFeedback]]] = {

    def docToReport(document: Document): (String, Option[ScoresAndFeedback]) = {
      val applicationId = document.get("applicationId").get.asString().getValue
      val scoresAndFeedbackOpt = processScoresAndFeedback(document)

      applicationId -> scoresAndFeedbackOpt
    }

    val query = Document("applicationId" -> Document("$in" -> applicationIds))
    collection.find[Document](query).toFuture().map ( _.map ( doc => docToReport(doc) ).toMap)
  }

  private def processScoresAndFeedback(doc: Document): Option[ScoresAndFeedback] = {
    for {
      testGroups <- subDocRoot("testGroups")(doc)
      fsb <- subDocRoot("FSB")(testGroups)
      scoresAndFeedback <- Try(Codecs.fromBson[ScoresAndFeedback](fsb.get("scoresAndFeedback"))).toOption
    } yield scoresAndFeedback
  }

  override def saveScoresAndFeedback(applicationId: String, scoresAndFeedback: ScoresAndFeedback): Future[Unit] = {
    val query = Document(APPLICATION_ID -> applicationId)
    val update = Document(
      "$set" -> Document(
        s"$FSB_TEST_GROUPS.scoresAndFeedback" -> scoresAndFeedback.toBson
      )
    )
    val validator = singleUpdateValidator(applicationId, actionDesc = s"saving fsb scores and feedback")
    collection.updateOne(query, update).toFuture() map validator
  }

  override def updateResult(applicationId: String, result: SchemeEvaluationResult): Future[Unit] = {
    val saveEvaluationResultsDoc = Document(s"$FSB_TEST_GROUPS.evaluation.result" -> result.toBson)
    val removeDoc = Document(
      "$pull" -> Document(s"$FSB_TEST_GROUPS.evaluation.result" -> Document("schemeId" -> result.schemeId.value))
    )
    val setDoc = Document("$addToSet" -> saveEvaluationResultsDoc)

    val removePredicate = Document("$and" -> BsonArray(
      Document("applicationId" -> applicationId),
      Document(
        s"$FSB_TEST_GROUPS.evaluation.result.schemeId" -> Document("$in" -> BsonArray(result.schemeId.value))
      )
    ))
    val setPredicate = Document("$and" -> BsonArray(
      Document("applicationId" -> applicationId),
      Document(
        s"$FSB_TEST_GROUPS.evaluation.result.schemeId" -> Document("$nin" -> BsonArray(result.schemeId.value))
      )
    ))

    val validator = singleUpdateValidator(applicationId, s"Fixing FSB results for ${result.schemeId}", ApplicationNotFound(applicationId))

    for {
      _ <- collection.updateOne(removePredicate, removeDoc).toFuture() map validator
      _ <- collection.updateOne(setPredicate, setDoc).toFuture() map validator
    } yield ()
  }

  override def updateCurrentSchemeStatus(applicationId: String, newCurrentSchemeStatus: Seq[SchemeEvaluationResult]): Future[Unit] = {
    val query = Document("applicationId" -> applicationId)
    val update = Document("$set" -> Document("currentSchemeStatus" -> Codecs.toBson(newCurrentSchemeStatus)))
    val validator = singleUpdateValidator(applicationId, actionDesc = s"Updating current scheme status")

    collection.updateOne(query, update).toFuture() map validator
  }

  override def addFsbProgressStatuses(applicationId: String, progressStatuses: List[(String, OffsetDateTime)]): Future[Unit] = {
    require(progressStatuses.nonEmpty, "Progress statuses to add must be specified")

    val query = Document("applicationId" -> applicationId)

    val updateSubDoc = progressStatuses.map { case (progressStatus, progressStatusTimestamp) =>
      Document(
        s"fsb-progress-status.$progressStatus" -> true,
        s"fsb-progress-status-timestamp.$progressStatus" -> offsetDateTimeToBson(progressStatusTimestamp)
      )
    }.reduce(_ ++ _)

    val update = Document("$set" -> updateSubDoc)

    val validator = singleUpdateValidator(applicationId, actionDesc = "adding fsb progress statuses")

    collection.updateOne(query, update).toFuture() map validator
  }

  override def findByApplicationId(applicationId: String): Future[Option[FsbTestGroup]] = {
    val query = Document("$and" -> BsonArray(
      Document(APPLICATION_ID -> applicationId),
      Document(FSB_TEST_GROUPS ->  Document("$exists" -> true))
    ))

    val projection = Projections.include(FSB_TEST_GROUPS)

    collection.find[Document](query).projection(projection).headOption() map {
      case Some(doc) =>
        for {
          testGroupsDoc <- subDocRoot("testGroups")(doc)
          fsbTestGroupOpt <- subDocRoot("FSB")(testGroupsDoc).flatMap ( fsb => Try(Codecs.fromBson[FsbTestGroup](fsb)).toOption )
        } yield fsbTestGroupOpt
      case _ => None
    }
  }

  override def findByApplicationIds(applicationIds: Seq[String], schemeId: Option[SchemeId]): Future[List[FsbSchemeResult]] = {
    val query = Document("$and" -> BsonArray(
      Document(APPLICATION_ID -> Document("$in" -> applicationIds)),
      Document(FSB_TEST_GROUPS ->  Document("$exists" -> true))
    ))

    val projection = Projections.include(APPLICATION_ID, FSB_TEST_GROUPS)

    collection.find[Document](query).projection(projection).toFuture().map { documents =>
      documents.foldLeft(List[FsbSchemeResult]())((list, document) => {

        FsbSchemeResult.fromBson(document) match {
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
    val query = Document("applicationId" -> applicationId)
    val update = Document("$unset" -> Document(FSB_TEST_GROUPS -> ""))

    collection.updateOne(query, update).toFuture().map { result =>
      if (result.getModifiedCount == 0) { throw ApplicationNotFound(s"Failed to match a document to fix for id $applicationId") }
      else { () }
    }
  }
}
