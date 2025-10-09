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

package repositories.application

import config.MicroserviceAppConfig
import factories.DateTimeFactory
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.*
import model.ProgressStatuses.ProgressStatus
import model.command.*
import model.persisted.*
import model.report.*
import model.{ApplicationStatus, *}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.{BsonArray, BsonDocument}
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import repositories.*
import services.TimeZoneService
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.{LocalDate, OffsetDateTime}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait ReportingRepository {
  def adjustmentReport(frameworkId: String): Future[Seq[AdjustmentReportItem]]
  def fastPassAwaitingAcceptanceReport: Future[Seq[(String, String)]]
  def candidateProgressReport(frameworkId: String): Future[Seq[CandidateProgressReportItem]]
  def diversityReport(frameworkId: String): Future[Seq[ApplicationForDiversityReport]]
  def onlineTestPassMarkReportFsPhase1Failed: Future[Seq[ApplicationForOnlineTestPassMarkReport]]
  def onlineTestPassMarkReportFsNotPhase1Failed: Future[Seq[ApplicationForOnlineTestPassMarkReport]]
  def onlineTestPassMarkReportNonFs: Future[Seq[ApplicationForOnlineTestPassMarkReport]]
  def onlineTestPassMarkReportByIds(applicationIds: Seq[String]): Future[Seq[ApplicationForOnlineTestPassMarkReport]]
  def numericTestExtractReport: Future[Seq[ApplicationForNumericTestExtractReport]]
  def onlineActiveTestCountReport: Future[Seq[ApplicationForOnlineActiveTestCountReport]]
  // TODO: mongo no callers of this method
  def candidateProgressReportNotWithdrawn(frameworkId: String): Future[Seq[CandidateProgressReportItem]]
  // TODO: mongo no callers of this method
  def allApplicationAndUserIds(frameworkId: String): Future[List[PersonalDetailsAdded]]
  def candidatesForDuplicateDetectionReport: Future[Seq[UserApplicationProfile]]
  // TODO: mongo i think this can be deleted
  def applicationsForInternshipReport(frameworkId: String): Future[List[ApplicationForInternshipReport]]
  def applicationsForAnalyticalSchemesReport(frameworkId: String): Future[Seq[ApplicationForAnalyticalSchemesReport]]
  def successfulCandidatesReport: Future[Seq[SuccessfulCandidatePartialItem]]
  def preSubmittedApplications(frameworkId: String): Future[Seq[ApplicationIdsAndStatus]]
  def candidatesStuckAfterFsacEvaluation: Future[Seq[FsacStuckCandidate]]
}

//scalastyle:off number.of.methods
@Singleton
class ReportingMongoRepository @Inject() (timeZoneService: TimeZoneService,
                                          val dateTimeFactory: DateTimeFactory,
                                          mongo: MongoComponent,
                                          val appConfig: MicroserviceAppConfig
                                         )(implicit ec: ExecutionContext)
  extends PlayMongoRepository[CreateApplicationRequest](
    collectionName = CollectionNames.APPLICATION,
    mongoComponent = mongo,
    domainFormat = CreateApplicationRequest.createApplicationRequestFormat,
    indexes = Nil
  ) with ReportingRepository with ReportingRepoBSONReader with ReactiveRepositoryHelpers with Schemes {

  override def candidateProgressReportNotWithdrawn(frameworkId: String): Future[Seq[CandidateProgressReportItem]] = {
    import org.mongodb.scala.bsonDocumentToDocument
    candidateProgressReport(
      BsonDocument("$and" -> BsonArray(
        BsonDocument("frameworkId" -> frameworkId),
        BsonDocument("applicationStatus" -> BsonDocument("$ne" -> "WITHDRAWN"))
      ))
    )
  }

  override def candidateProgressReport(frameworkId: String): Future[Seq[CandidateProgressReportItem]] =
    candidateProgressReport(Document("frameworkId" -> frameworkId))

  // ReportingRepoBSONReader contains the method to convert the BSONDocument to the case class
  private def candidateProgressReport(query: Document): Future[Seq[CandidateProgressReportItem]] = {
    val projection = Document(
      "userId" -> true,
      "applicationRoute" -> true,
      "personal-details" -> true,
      "scheme-preferences.schemes" -> true,
      "assistance-details" -> true,
      "civil-service-experience-details" -> true,
      "applicationId" -> true,
      "progress-status" -> true,
      "fsac-indicator.assessmentCentre" -> true
    )

    collection.find[Document](query).projection(projection).toFuture().map {
      _.map( toCandidateProgressReportItem )
    }
  }

  // TODO: mongo i think this can be deleted and the method in ReportingRepoBSONReader
  override def applicationsForInternshipReport(frameworkId: String): Future[List[ApplicationForInternshipReport]] = {
/*
    val query = BsonDocument("$and" ->
      BsonArray(
        BsonDocument("frameworkId" -> frameworkId),
        BsonDocument("$or" -> BsonArray(
          BsonDocument("applicationRoute" -> ApplicationRoute.Edip.toBson),
          BsonDocument("applicationRoute" -> ApplicationRoute.Sdip.toBson),
          BsonDocument("applicationRoute" ->  ApplicationRoute.SdipFaststream.toBson)
        )),
        BsonDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_COMPLETED}" -> true),
        BsonDocument("personal-details" -> BsonDocument("$exists" -> true)),
        BsonDocument("assistance-details" -> BsonDocument("$exists" -> true)),
        BsonDocument("testGroups" -> BsonDocument("$exists" -> true))
      ))

    val projection = BsonDocument(
      "_id" -> false,
      "applicationId" -> true,
      "userId" -> true,
      "applicationRoute" -> true,
      "personal-details.firstName" -> true,
      "personal-details.lastName" -> true,
      "personal-details.preferredName" -> true,
      "assistance-details.guaranteedInterview" -> true,
      "progress-status" -> true,
      "testGroups" -> true
    )

//    reportQueryWithProjectionsBSON[ApplicationForInternshipReport](query, projection)
    collection.find[Document](query).projection(projection).toFuture().map {
      _.map ( toApplicationForInternshipReport ).toList
    }
 */
    ???
  }

  override def applicationsForAnalyticalSchemesReport(frameworkId: String): Future[Seq[ApplicationForAnalyticalSchemesReport]] = {
    val firstSchemePreference = "scheme-preferences.schemes.0"
    val query = BsonDocument("$and" ->
      BsonArray(
        BsonDocument("frameworkId" -> frameworkId),
        BsonDocument(s"progress-status.${ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED}" -> true),
        BsonDocument("$or" -> BsonArray(
          BsonDocument(firstSchemePreference -> GovernmentOperationalResearchService.value),
          BsonDocument(firstSchemePreference -> GovernmentStatisticalService.value)
        ))
      ))

    val projection = BsonDocument(
      "applicationId" -> true,
      "userId" -> true,
      "personal-details.firstName" -> true,
      "personal-details.lastName" -> true,
      "scheme-preferences" -> true,
      "assistance-details.guaranteedInterview" -> true,
      "testGroups" -> true
    )

    collection.find[Document](query).projection(projection).toFuture().map {
      _.map( toApplicationForAnalyticalSchemesReport )
    }
  }

  override def diversityReport(frameworkId: String): Future[Seq[ApplicationForDiversityReport]] = {
    val query = Document("frameworkId" -> frameworkId)
    val projection = Document(
      "userId" -> true,
      "applicationRoute" -> true,
      "scheme-preferences.schemes" -> true,
      "assistance-details" -> true,
      "civil-service-experience-details" -> true,
      "personal-details" -> true,
      "applicationId" -> true,
      "progress-status" -> true,
      "currentSchemeStatus" -> true
    )

    collection.find[Document](query).projection(projection).toFuture().map {
      _.map(toApplicationForDiversityReport)
    }
  }

  override def numericTestExtractReport: Future[Seq[ApplicationForNumericTestExtractReport]] = {
    val query = BsonDocument("$and" -> BsonArray(
      BsonDocument(s"applicationStatus" -> ApplicationStatus.SIFT.toBson),
      BsonDocument(s"progress-status.${ProgressStatuses.SIFT_COMPLETED}" -> BsonDocument("$exists" -> false))
    ))

    val projection = BsonDocument(
      "userId" -> true,
      "applicationId" -> true,
      "personal-details" -> true,
      "scheme-preferences.schemes" -> true,
      "assistance-details" -> true,
      "testGroups.PHASE1" -> true,
      "testGroups.PHASE2" -> true,
      "testGroups.PHASE3.tests.callbacks.reviewed" -> true,
      "testGroups.SIFT_PHASE" -> true,
      "progress-status" -> true,
      "currentSchemeStatus" -> true
    )

    collection.find[Document](query).projection(projection).toFuture().map {
      _.map( toApplicationForNumericTestExtractReport )
    }
  }

  override def onlineActiveTestCountReport: Future[Seq[ApplicationForOnlineActiveTestCountReport]] = {
    val query = BsonDocument("$or" -> BsonArray(
      BsonDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_INVITED}" -> BsonDocument("$exists" -> true)),
      BsonDocument(s"progress-status.${ProgressStatuses.PHASE2_TESTS_INVITED}" -> BsonDocument("$exists" -> true))
    ))

    val projection = BsonDocument(
      "userId" -> true,
      "applicationId" -> true,
      "assistance-details.guaranteedInterview" -> true,
      "testGroups.PHASE1" -> true,
      "testGroups.PHASE2" -> true
    )

    collection.find[Document](query).projection(projection).toFuture().map {
      _.map ( toApplicationForOnlineActiveTestCountReport )
    }
  }

  override def onlineTestPassMarkReportFsPhase1Failed: Future[Seq[ApplicationForOnlineTestPassMarkReport]] = {
    val query = BsonDocument("$and" -> BsonArray(
      BsonDocument(s"applicationRoute" -> ApplicationRoute.Faststream.toBson),
      BsonDocument(s"applicationStatus" -> ApplicationStatus.PHASE1_TESTS_FAILED.toBson),
      BsonDocument(
        "$or" -> BsonArray(
          BsonDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED}" -> true),
          BsonDocument(s"progress-status.${ProgressStatuses.FAST_PASS_ACCEPTED}" -> true)
        )
      )))

    commonOnlineTestPassMarkReport(query)
  }

  override def onlineTestPassMarkReportFsNotPhase1Failed: Future[Seq[ApplicationForOnlineTestPassMarkReport]] = {
    val query = BsonDocument("$and" -> BsonArray(
      BsonDocument(s"applicationRoute" -> ApplicationRoute.Faststream.toBson),
      BsonDocument("applicationStatus" -> BsonDocument("$ne" -> ApplicationStatus.PHASE1_TESTS_FAILED.toBson)),
      BsonDocument(
        "$or" -> BsonArray(
          BsonDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED}" -> true),
          BsonDocument(s"progress-status.${ProgressStatuses.FAST_PASS_ACCEPTED}" -> true)
        )
      )))

    commonOnlineTestPassMarkReport(query)
  }

  override def onlineTestPassMarkReportNonFs: Future[Seq[ApplicationForOnlineTestPassMarkReport]] = {
    val query = BsonDocument("$and" -> BsonArray(
      BsonDocument("applicationRoute" -> BsonDocument("$in" ->
        Seq(ApplicationRoute.Edip.toString, ApplicationRoute.Sdip.toString, ApplicationRoute.SdipFaststream.toString))
      ),
      BsonDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED}" -> true)
    ))

    commonOnlineTestPassMarkReport(query)
  }

  def onlineTestPassMarkReportByIds(applicationIds: Seq[String]): Future[Seq[ApplicationForOnlineTestPassMarkReport]] = {

    val query = BsonDocument("$and" -> BsonArray(
      BsonDocument("applicationId" -> BsonDocument("$in" -> applicationIds)),
      BsonDocument(
        "$or" -> BsonArray(
          BsonDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED}" -> true),
          BsonDocument(s"progress-status.${ProgressStatuses.FAST_PASS_ACCEPTED}" -> true)
        )
      )
    ))

    commonOnlineTestPassMarkReport(query)
  }

  private def commonOnlineTestPassMarkReport(query: BsonDocument): Future[Seq[ApplicationForOnlineTestPassMarkReport]] = {
    val projection = BsonDocument(
      "userId" -> true,
      "applicationId" -> true,
      "applicationRoute" -> true,
      "scheme-preferences.schemes" -> true,
      "assistance-details" -> true,
      "testGroups" -> true,
      "progress-status" -> true,
      "currentSchemeStatus" -> true
    )

    collection.find[Document](query).projection(projection).toFuture().map {
      _.map( toApplicationForOnlineTestPassMarkReport )
    }
  }

  override def fastPassAwaitingAcceptanceReport: Future[Seq[(String, String)]] = {
    val query = BsonDocument("$and" -> BsonArray(
      Document("applicationStatus" -> Document("$eq" -> ApplicationStatus.SUBMITTED_CHECK_PASSED.toBson)),
      Document("civil-service-experience-details.fastPassReceived" -> Document("$eq" -> true)),
      Document("civil-service-experience-details.fastPassAccepted" -> Document("$exists" -> false))
    ))

    val projection = Document(
      "applicationId" -> true,
      "civil-service-experience-details.certificateNumber" -> true
    )

    collection.find[Document](query).projection(projection).toFuture().map {
      _.map { doc =>
        val appId = doc.get("applicationId").map(_.asString().getValue).get
        val cert = subDocRoot("civil-service-experience-details")(doc).map { doc =>
          doc.get("certificateNumber").asString().getValue
        }.getOrElse("Not found")
        appId -> cert // Tuple
      }
    }
  }

  //scalastyle:off method.length
  override def adjustmentReport(frameworkId: String): Future[Seq[AdjustmentReportItem]] = {
    val query = BsonDocument("$and" ->
      BsonArray(
        Document("frameworkId" -> frameworkId),
        Document("applicationStatus" -> Document("$ne" -> ApplicationStatus.CREATED.toBson)),
        Document("applicationStatus" -> Document("$ne" -> ApplicationStatus.IN_PROGRESS.toBson)),
        Document("applicationStatus" -> Document("$ne" -> ApplicationStatus.WITHDRAWN.toBson)),
        BsonDocument("$and" ->
          BsonArray(
            BsonDocument("$or" ->
              BsonArray(
                Document("applicationRoute" -> Document("$in" ->
                  Seq(ApplicationRoute.Faststream.toString, ApplicationRoute.SdipFaststream.toString, ApplicationRoute.Sdip.toString))
                ),
                Document("applicationRoute" -> Document("$exists" -> false))
              )
            ),
            BsonDocument("$or" ->
              BsonArray(
                Document("assistance-details.needsSupportAtVenue" -> true),
                Document("assistance-details.needsSupportForPhoneInterview" -> true),
                Document("assistance-details.guaranteedInterview" -> true)
              )
            )
          )
        )
      )
    )

    val projection = Document(
      "userId" -> true,
      "applicationStatus" -> true,
      "progress-status" -> true,
      "applicationId" -> true,
      "applicationRoute" -> true,
      "personal-details.firstName" -> true,
      "personal-details.lastName" -> true,
      "personal-details.preferredName" -> true,
      "assistance-details" -> true
    )

    collection.find[Document](query).projection(projection).toFuture().map {
      _.map { document =>

        val applicationId = extractAppIdOpt(document)
        val applicationRoute = Try(Codecs.fromBson[ApplicationRoute](document.get("applicationRoute").get)).toOption.map(_.toString)
        val userId = extractUserId(document)

        val personalDetailsOpt = subDocRoot("personal-details")(document)
        val firstName = extract("firstName")(personalDetailsOpt)
        val lastName = extract("lastName")(personalDetailsOpt)
        val preferredName = extract("preferredName")(personalDetailsOpt)

        val adDocOpt = subDocRoot("assistance-details")(document)
        val gis = adDocOpt.flatMap( doc => Try(doc.get("guaranteedInterview").asBoolean().getValue).map(booleanTranslator).toOption )
        val needsSupportAtVenue = adDocOpt.flatMap( doc =>
          Try(doc.get("needsSupportAtVenue").asBoolean().getValue).map(booleanTranslator).toOption
        )
        val needsSupportForPhoneInterview = adDocOpt.flatMap( doc =>
          Try(doc.get("needsSupportForPhoneInterview").asBoolean().getValue).map(booleanTranslator).toOption
        )
        val disabilityImpact = adDocOpt.flatMap( doc => Try(doc.get("disabilityImpact").asString().getValue).toOption )
        val disabilityCategories = adDocOpt.flatMap( doc => Try(Codecs.fromBson[List[String]](doc.get("disabilityCategories"))).toOption )
        val otherDisabilityDescription = adDocOpt.flatMap( doc => Try(doc.get("otherDisabilityDescription").asString().getValue).toOption )

        val candidateProgressStatuses = toProgressResponse(applicationId.get)(document)
        val latestProgressStatus = Some(ProgressStatusesReportLabels.progressStatusNameInReports(candidateProgressStatuses))

        val needsSupportAtVenueDescription = extract("needsSupportAtVenueDescription")(adDocOpt)
        val needsSupportForPhoneInterviewDescription = extract("needsSupportForPhoneInterviewDescription")(adDocOpt)
        val hasDisability = extract("hasDisability")(adDocOpt)
        val hasDisabilityDescription = extract("hasDisabilityDescription")(adDocOpt)
        val adjustmentsComment = extract("adjustmentsComment")(adDocOpt)

        AdjustmentReportItem(
          userId, applicationId, applicationRoute, firstName, lastName, preferredName, email = None, telephone = None,
          gis, needsSupportAtVenue, needsSupportForPhoneInterview,
          disabilityImpact, disabilityCategories, otherDisabilityDescription, latestProgressStatus,
          needsSupportAtVenueDescription, needsSupportForPhoneInterviewDescription, hasDisability, hasDisabilityDescription,
          adjustmentsComment
        )
      }
    }
  }
  //scalastyle:on method.length

/*
  override def allApplicationAndUserIds(frameworkId: String): Future[List[PersonalDetailsAdded]] = {
    val query = BSONDocument("frameworkId" -> frameworkId)
    val projection = BSONDocument(
      "applicationId" -> "1",
      "userId" -> "1"
    )

    collection.find(query, Some(projection)).cursor[BSONDocument]()
      .collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]()).map {
      _.map { doc =>
        val userId = doc.getAs[String]("userId").getOrElse("")
        val applicationId = doc.getAs[String]("applicationId").getOrElse("")
        PersonalDetailsAdded(applicationId, userId)
      }
    }
  }*/
  //TODO: mongo no usages
  override def allApplicationAndUserIds(frameworkId: String): Future[List[PersonalDetailsAdded]] = ???

  override def candidatesForDuplicateDetectionReport: Future[Seq[UserApplicationProfile]] = {
    val query = BsonDocument(
      "personal-details" -> BsonDocument("$exists" -> true),
      "progress-status.SUBMITTED" -> BsonDocument("$exists" -> true)
    )
    val projection = BsonDocument(
      "applicationId" -> true,
      "applicationRoute" -> true,
      "userId" -> true,
      "progress-status" -> true,
      "personal-details.firstName" -> true,
      "personal-details.lastName" -> true,
      "personal-details.dateOfBirth" -> true
    )

    collection.find[Document](query).projection(projection).toFuture().map {
      _.map( toUserApplicationProfile )
    }
  }

  //scalastyle:off method.length
  override def successfulCandidatesReport: Future[Seq[SuccessfulCandidatePartialItem]] = {

    val query = BsonDocument("$and" ->
      BsonArray(
        BsonDocument("userId" -> BsonDocument("$exists" -> true)),
        BsonDocument("applicationId" -> BsonDocument("$exists" -> true)),
        BsonDocument(s"progress-status.${ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER_NOTIFIED}" -> true)
      )
    )

    val projection = BsonDocument(
      "userId" -> true,
      "applicationId" -> true,
      "applicationStatus" -> true,
      "applicationRoute" -> true,
      "progress-status" -> true,
      "progress-status-dates" -> true,
      "progress-status-timestamp" -> true,
      "personal-details.firstName" -> true,
      "personal-details.lastName" -> true,
      "personal-details.preferredName" -> true,
      "fsac-indicator.assessmentCentre" -> true
    )

    def getDate(doc: Document, status: ProgressStatus): Option[OffsetDateTime] = {
      import repositories.formats.MongoJavatimeFormats.Implicits.jtOffsetDateTimeFormat
      doc.get("progress-status-timestamp").map{ _.asDocument().get(status.key) }.flatMap {
        bson => Try(Codecs.fromBson[OffsetDateTime](bson)).toOption
      }
    }

    collection.find[Document](query).projection(projection).toFuture().map {
      _.map { doc =>

        val userId = extractUserIdOpt(doc).get
        val appId = extractAppIdOpt(doc).get
        val applicationStatus = Codecs.fromBson[ApplicationStatus](doc.get("applicationStatus").get).toString
        val applicationRoute = Codecs.fromBson[ApplicationRoute](doc.get("applicationRoute").get).toString
        val personalDetailsDocOpt = subDocRoot("personal-details")(doc)
        val fullName = for {
          first <- extract("firstName")(personalDetailsDocOpt)
          last <- extract("lastName")(personalDetailsDocOpt)
        } yield s"$first $last"
        val preferredName = extract("preferredName")(personalDetailsDocOpt)

        val submittedTimestampOpt = getDate(doc, ProgressStatuses.SUBMITTED)
        val eligibleForOfferTimestampOpt = getDate(doc, ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER_NOTIFIED)

        val fsacIndicatorDocOpt = subDocRoot("fsac-indicator")(doc)
        val assessmentCentre = extract("assessmentCentre")(fsacIndicatorDocOpt)

        SuccessfulCandidatePartialItem(
          userId,
          appId,
          applicationStatus,
          applicationRoute,
          fullName,
          preferredName,
          submittedTimestampOpt,
          eligibleForOfferTimestampOpt,
          assessmentCentre
        )
      }
    }
  }
  //scalastyle:on method.length

  override def preSubmittedApplications(frameworkId: String): Future[Seq[ApplicationIdsAndStatus]] = {
    val query = BsonDocument("$and" -> BsonArray(

      BsonDocument("frameworkId" -> frameworkId),
      BsonDocument("$or" -> BsonArray(
        BsonDocument(s"progress-status.${ProgressStatuses.CREATED}" -> true),
        BsonDocument(s"applicationStatus" -> ApplicationStatus.IN_PROGRESS.toBson),
        BsonDocument(s"applicationStatus" -> ApplicationStatus.CREATED.toBson)
      ))
    ))

    val projection = BsonDocument(
      "_id" -> false,
      "applicationId" -> true,
      "userId" -> true,
      "applicationStatus" -> true,
      "applicationRoute" -> true,
      "progress-status" -> true
    )

    collection.find[Document](query).projection(projection).toFuture().map {
      _.map { doc =>

        val applicationId = extractAppIdOpt(doc).get
        val userId = extractUserIdOpt(doc).get
        val applicationStatus = Codecs.fromBson[ApplicationStatus](doc.get("applicationStatus").get).toString
        val applicationRoute = Codecs.fromBson[ApplicationRoute](doc.get("applicationRoute").get).toString
        val candidateProgressStatuses = toProgressResponse(applicationId)(doc)
        val latestProgressStatus = Some(ProgressStatusesReportLabels.progressStatusNameInReports(candidateProgressStatuses))
        ApplicationIdsAndStatus(applicationId, userId, applicationStatus, applicationRoute, latestProgressStatus)

      }
    }
  }

  //scalastyle:off method.length
  override def candidatesStuckAfterFsacEvaluation: Future[Seq[FsacStuckCandidate]] = {
    val query = Document("$and" -> BsonArray(
      Document(s"applicationStatus" -> ApplicationStatus.ASSESSMENT_CENTRE.toBson),
      Document(s"progress-status.${ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ACCEPTED}" -> true),
      Document(s"progress-status.${ProgressStatuses.ASSESSMENT_CENTRE_FAILED}" -> Document("$exists" -> false)),
      Document("testGroups.FSAC.evaluation" -> Document("$exists" -> true))
    ))

    val projection = Document(
      "applicationId" -> true,
      "applicationStatus" -> true,
      "progress-status" -> true,
      "progress-status-timestamp" -> true
    )

    collection.find[Document](query).projection(projection).toFuture()
      .map(_.map { document =>
        val applicationId = extractAppIdOpt(document).get
        val applicationStatus = extractApplicationStatus(document)

        val progressStatusTimeStampDocOpt = subDocRoot("progress-status-timestamp")(document)

        // We need this implicit in scope to perform the maxBy below
        implicit val dateOrdering: Ordering[OffsetDateTime] = _ compareTo _

        import repositories.formats.MongoJavatimeFormats.Implicits.jtOffsetDateTimeFormat

        import scala.jdk.CollectionConverters.* // Needed for ISODate

        val latestProgressStatusOpt = progressStatusTimeStampDocOpt.flatMap { timestamps =>
          val convertedTimestamps = timestamps.entrySet().asScala.toSet
          val relevantProgressStatuses = convertedTimestamps.filter( _.getKey.startsWith(applicationStatus) )
          val latestRelevantProgressStatus = relevantProgressStatuses.maxBy(element =>
            Codecs.fromBson[OffsetDateTime](timestamps.get(element.getKey))
          )
          Try(ProgressStatuses.nameToProgressStatus(latestRelevantProgressStatus.getKey)).toOption
        }

        val latestProgressStatusTimestampOpt = latestProgressStatusOpt.flatMap { latestProgressStatus =>
          progressStatusTimeStampDocOpt.map { timestamps =>
            Codecs.fromBson[OffsetDateTime](timestamps.get(latestProgressStatus.toString)).toString
          }
        }

        val scoresAccepted = Some(ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ACCEPTED)
        val scoresAcceptedTimestamp = progressStatusTimeStampDocOpt.map { timestamps =>
          Codecs.fromBson[OffsetDateTime](timestamps.get(ProgressStatuses.ASSESSMENT_CENTRE_SCORES_ACCEPTED.toString)).toString
        }

        FsacStuckCandidate(applicationId, applicationStatus, scoresAccepted, scoresAcceptedTimestamp,
          latestProgressStatusOpt, latestProgressStatusTimestampOpt
        )
      })
  } //scalastyle:on

  private def toUserApplicationProfile(document: Document) = {
    val applicationId = extractAppIdOpt(document).get
    val userId = extractUserIdOpt(document).get
    val applicationRoute = Codecs.fromBson[ApplicationRoute](document.get("applicationRoute").get)

    val personalDetailsDocOpt = subDocRoot("personal-details")(document)
    val firstName = extract("firstName")(personalDetailsDocOpt).get
    val lastName = extract("lastName")(personalDetailsDocOpt).get

    // TODO: mongo date problem here because the dob is stored as a string in mongo so the MongoJodaFormats.Implicits will not be able to read it
    val dobString = extract("dateOfBirth")(personalDetailsDocOpt).get
    val dob = LocalDate.parse(dobString)

    val candidateProgressResponse = toProgressResponse(applicationId)(document)
    val latestProgressStatus = ProgressStatusesReportLabels.progressStatusNameInReports(candidateProgressResponse)

    UserApplicationProfile(userId, candidateProgressResponse, latestProgressStatus, firstName, lastName, dob, applicationRoute)
  }

  private[application] def isNonSubmittedStatus(progress: ProgressResponse): Boolean = {
    val isNotSubmitted = !progress.submitted
    val isNotWithdrawn = !progress.withdrawn
    isNotWithdrawn && isNotSubmitted
  }

/*
  private def reportQueryWithProjections[A](
                                             query: BSONDocument,
                                             prj: BSONDocument,
                                             upTo: Int = Int.MaxValue,
                                             stopOnError: Boolean = true
                                           )(implicit reader: Format[A]): Future[List[A]] = {
    val err = if (stopOnError) FailOnError[List[A]]() else ContOnError[List[A]]()
    collection.find(query, Some(prj)).cursor[A](ReadPreference.nearest).collect[List](upTo, err)
  }*/

  /*
    private def reportQueryWithProjectionsBSON[A](
                                                   query: BSONDocument,
                                                   prj: BSONDocument,
                                                   upTo: Int = Int.MaxValue,
                                                   stopOnError: Boolean = true
                                                 )(implicit reader: BSONDocumentReader[A]): Future[List[A]] = {
      val err = if (stopOnError) FailOnError[List[A]]() else ContOnError[List[A]]()
      bsonCollection.find(query, Some(prj))
        .cursor[A](ReadPreference.nearest)
        .collect[List](Int.MaxValue, err)
    }*/

//  protected lazy val applicationCollection: MongoCollection[Document] = this.mongoComponent.database.getCollection(CollectionNames.APPLICATION)
/*
  private def reportQueryWithProjectionsBSON[A](
                                                 query: Document,
                                                 projection: Bson,
                                               )(implicit reader: BSONDocumentReader[A]): Future[List[A]] = {
    bsonCollection.find(query).projection(prj).
      .cursor[A](ReadPreference.nearest)
      .collect[List](Int.MaxValue, err)
  }*/
}
