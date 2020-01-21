/*
 * Copyright 2020 HM Revenue & Customs
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
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus._
import model.ProgressStatuses.ProgressStatus
import model.command._
import model.persisted._
import model.report._
import model.{ ApplicationStatus, _ }
import org.joda.time.format.DateTimeFormat
import org.joda.time.{ DateTime, LocalDate }
import play.api.libs.json.Format
import reactivemongo.api.Cursor.{ ContOnError, FailOnError }
import reactivemongo.api.{ Cursor, DB, ReadPreference }
import reactivemongo.bson.{ BSONDocument, BSONDocumentReader, _ }
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories._
import repositories.BSONDateTimeHandler
import services.TimeZoneService
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ReportingRepository {
  def adjustmentReport(frameworkId: String): Future[List[AdjustmentReportItem]]

  def fastPassAwaitingAcceptanceReport: Future[List[(String, String)]]

  def candidateProgressReport(frameworkId: String): Future[List[CandidateProgressReportItem]]

  def diversityReport(frameworkId: String): Future[List[ApplicationForDiversityReport]]

  def onlineTestPassMarkReportFsPhase1Failed: Future[List[ApplicationForOnlineTestPassMarkReport]]

  def onlineTestPassMarkReportFsNotPhase1Failed: Future[List[ApplicationForOnlineTestPassMarkReport]]

  def onlineTestPassMarkReportNonFs: Future[List[ApplicationForOnlineTestPassMarkReport]]

  def onlineTestPassMarkReportByIds(applicationIds: Seq[String]): Future[List[ApplicationForOnlineTestPassMarkReport]]

  def numericTestExtractReport: Future[List[ApplicationForNumericTestExtractReport]]

  def onlineActiveTestCountReport: Future[List[ApplicationForOnlineActiveTestCountReport]]

  def candidateProgressReportNotWithdrawn(frameworkId: String): Future[List[CandidateProgressReportItem]]

  def allApplicationAndUserIds(frameworkId: String): Future[List[PersonalDetailsAdded]]

  def candidatesForDuplicateDetectionReport: Future[List[UserApplicationProfile]]

  def applicationsForInternshipReport(frameworkId: String): Future[List[ApplicationForInternshipReport]]

  def applicationsForAnalyticalSchemesReport(frameworkId: String): Future[List[ApplicationForAnalyticalSchemesReport]]

  def successfulCandidatesReport: Future[List[SuccessfulCandidatePartialItem]]

  def preSubmittedApplications(frameworkId: String): Future[List[ApplicationIdsAndStatus]]
}

class ReportingMongoRepository(timeZoneService: TimeZoneService, val dateTimeFactory: DateTimeFactory)(implicit mongo: () => DB)
  extends ReactiveRepository[CreateApplicationRequest, BSONObjectID](CollectionNames.APPLICATION, mongo,
    CreateApplicationRequest.createApplicationRequestFormat, ReactiveMongoFormats.objectIdFormats) with ReportingRepository
    with RandomSelection with CommonBSONDocuments with ReportingRepoBSONReader with ReactiveRepositoryHelpers {

  private val unlimitedMaxDocs = -1

  override def candidateProgressReportNotWithdrawn(frameworkId: String): Future[List[CandidateProgressReportItem]] =
    candidateProgressReport(BSONDocument("$and" -> BSONArray(
      BSONDocument("frameworkId" -> frameworkId),
      BSONDocument("applicationStatus" -> BSONDocument("$ne" -> "WITHDRAWN"))
    )))

  override def candidateProgressReport(frameworkId: String): Future[List[CandidateProgressReportItem]] =
    candidateProgressReport(BSONDocument("frameworkId" -> frameworkId))

  private def candidateProgressReport(query: BSONDocument): Future[List[CandidateProgressReportItem]] = {
    val projection = BSONDocument(
      "userId" -> "1",
      "applicationRoute" -> "1",
      "personal-details.edipCompleted" -> "1",
      "scheme-preferences.schemes" -> "1",
      "assistance-details" -> "1",
      "civil-service-experience-details" -> "1",
      "applicationId" -> "1",
      "progress-status" -> "2",
      "fsac-indicator.assessmentCentre" -> 1
    )
    reportQueryWithProjectionsBSON[CandidateProgressReportItem](query, projection)
  }

  override def applicationsForInternshipReport(frameworkId: String): Future[List[ApplicationForInternshipReport]] = {
    val query = BSONDocument("$and" ->
      BSONArray(
        BSONDocument("frameworkId" -> frameworkId),
        BSONDocument("$or" -> BSONArray(
          BSONDocument("applicationRoute" -> ApplicationRoute.Edip),
          BSONDocument("applicationRoute" -> ApplicationRoute.Sdip),
          BSONDocument("applicationRoute" ->  ApplicationRoute.SdipFaststream)
        )),
        BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_COMPLETED}" -> true),
        BSONDocument("personal-details" -> BSONDocument("$exists" -> true)),
        BSONDocument("assistance-details" -> BSONDocument("$exists" -> true)),
        BSONDocument("testGroups" -> BSONDocument("$exists" -> true))
      ))

    val projection = BSONDocument(
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

    reportQueryWithProjectionsBSON[ApplicationForInternshipReport](query, projection)
  }

  override def applicationsForAnalyticalSchemesReport(frameworkId: String): Future[List[ApplicationForAnalyticalSchemesReport]] = {
    val firstSchemePreference = "scheme-preferences.schemes.0"
    val query = BSONDocument("$and" ->
      BSONArray(
        BSONDocument("frameworkId" -> frameworkId),
        BSONDocument(s"progress-status.${ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED}" -> true),
        BSONDocument("$or" -> BSONArray(
          BSONDocument(firstSchemePreference -> SchemeId("GovernmentOperationalResearchService").value),
          BSONDocument(firstSchemePreference -> SchemeId("GovernmentStatisticalService").value)
        ))
      ))

    val projection = BSONDocument(
      "applicationId" -> true,
      "userId" -> true,
      "personal-details.firstName" -> true,
      "personal-details.lastName" -> true,
      "scheme-preferences" -> true,
      "assistance-details.guaranteedInterview" -> true,
      "testGroups" -> true
    )

    reportQueryWithProjectionsBSON[ApplicationForAnalyticalSchemesReport](query, projection)
  }

  override def diversityReport(frameworkId: String): Future[List[ApplicationForDiversityReport]] = {
    val query = BSONDocument("frameworkId" -> frameworkId)
    val projection = BSONDocument(
      "userId" -> "1",
      "applicationRoute" -> "1",
      "scheme-preferences.schemes" -> "1",
      "assistance-details" -> "1",
      "civil-service-experience-details" -> "1",
      "applicationId" -> "1",
      "progress-status" -> "2",
      "currentSchemeStatus" -> "1"
    )
    reportQueryWithProjectionsBSON[ApplicationForDiversityReport](query, projection)
  }

  override def numericTestExtractReport: Future[List[ApplicationForNumericTestExtractReport]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument(s"applicationStatus" -> ApplicationStatus.SIFT),
      BSONDocument(s"progress-status.${ProgressStatuses.SIFT_COMPLETED}" -> BSONDocument("$exists" -> false))
    ))

    val projection = BSONDocument(
      "userId" -> "1",
      "applicationId" -> "1",
      "personal-details" -> "1",
      "scheme-preferences.schemes" -> "1",
      "assistance-details" -> "1",
      "testGroups.PHASE1" -> "1",
      "testGroups.PHASE2" -> "1",
      "testGroups.PHASE3.tests.callbacks.reviewed" -> 1,
      "testGroups.SIFT_PHASE" -> "1",
      "progress-status" -> "1",
      "currentSchemeStatus" -> "1"
    )

    reportQueryWithProjectionsBSON[ApplicationForNumericTestExtractReport](query, projection)
  }

  def onlineActiveTestCountReport: Future[List[ApplicationForOnlineActiveTestCountReport]] = {
    val query = BSONDocument("$or" -> BSONArray(
      BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_INVITED}" -> BSONDocument("$exists" -> true)),
      BSONDocument(s"progress-status.${ProgressStatuses.PHASE2_TESTS_INVITED}" -> BSONDocument("$exists" -> true))
    ))

    val projection = BSONDocument(
      "userId" -> "1",
      "applicationId" -> "1",
      "testGroups.PHASE1" -> "1",
      "testGroups.PHASE2" -> "1"
    )

    reportQueryWithProjectionsBSON[ApplicationForOnlineActiveTestCountReport](query, projection)
  }

  override def onlineTestPassMarkReportFsPhase1Failed: Future[List[ApplicationForOnlineTestPassMarkReport]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument(s"applicationRoute" -> ApplicationRoute.Faststream),
      BSONDocument(s"applicationStatus" -> ApplicationStatus.PHASE1_TESTS_FAILED),
      BSONDocument(
        "$or" -> BSONArray(
          BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED}" -> true),
          BSONDocument(s"progress-status.${ProgressStatuses.FAST_PASS_ACCEPTED}" -> true)
        )
      )))

    commonOnlineTestPassMarkReport(query)
  }

  override def onlineTestPassMarkReportFsNotPhase1Failed: Future[List[ApplicationForOnlineTestPassMarkReport]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument(s"applicationRoute" -> ApplicationRoute.Faststream),
      BSONDocument("applicationStatus" -> BSONDocument("$ne" -> ApplicationStatus.PHASE1_TESTS_FAILED)),

      BSONDocument(
        "$or" -> BSONArray(
          BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED}" -> true),
          BSONDocument(s"progress-status.${ProgressStatuses.FAST_PASS_ACCEPTED}" -> true)
        )
      )))

    commonOnlineTestPassMarkReport(query)
  }

  override def onlineTestPassMarkReportNonFs: Future[List[ApplicationForOnlineTestPassMarkReport]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationRoute" -> BSONDocument("$in" ->
        Seq(ApplicationRoute.Edip.toString, ApplicationRoute.Sdip.toString, ApplicationRoute.SdipFaststream.toString))
      ),
      BSONDocument(
        "$or" -> BSONArray(
          BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED}" -> true),
          BSONDocument(s"progress-status.${ProgressStatuses.FAST_PASS_ACCEPTED}" -> true)
        )
      )))

    commonOnlineTestPassMarkReport(query)
  }

  def onlineTestPassMarkReportByIds(applicationIds: Seq[String]): Future[List[ApplicationForOnlineTestPassMarkReport]] = {
    val query = BSONDocument("$or" -> BSONArray(
      BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED}" -> true),
      BSONDocument(s"progress-status.${ProgressStatuses.FAST_PASS_ACCEPTED}" -> true)
    )) ++ BSONDocument("applicationId" -> BSONDocument("$in" -> applicationIds))

    commonOnlineTestPassMarkReport(query)
  }

  private def commonOnlineTestPassMarkReport(query: BSONDocument): Future[List[ApplicationForOnlineTestPassMarkReport]] = {
    val projection = BSONDocument(
      "userId" -> "1",
      "applicationId" -> "1",
      "applicationRoute" -> "1",
      "scheme-preferences.schemes" -> "1",
      "assistance-details" -> "1",
      "testGroups.PHASE1" -> "1",
      "testGroups.PHASE2" -> "1",
      "testGroups.PHASE3.tests.callbacks.reviewed" -> 1,
      "testGroups.SIFT_PHASE" -> "1",
      "progress-status" -> "1",
      "currentSchemeStatus" -> "1"
    )

    reportQueryWithProjectionsBSON[ApplicationForOnlineTestPassMarkReport](query, projection)
  }

  override def fastPassAwaitingAcceptanceReport: Future[List[(String, String)]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> BSONDocument("$eq" -> ApplicationStatus.SUBMITTED)),
      BSONDocument("civil-service-experience-details.fastPassReceived" -> BSONDocument("$eq" -> true)),
      BSONDocument("civil-service-experience-details.fastPassAccepted" -> BSONDocument("$exists" -> false))
    ))

    val projection = BSONDocument(
      "applicationId" -> true,
      "civil-service-experience-details.certificateNumber" -> true
    )

    collection.find(query, projection).cursor[BSONDocument]()
      .collect[List](unlimitedMaxDocs, FailOnError[List[BSONDocument]]()).map { docList =>
      docList.map { doc =>
        val app = doc.getAs[String]("applicationId").get
        val cert = doc.getAs[BSONDocument]("civil-service-experience-details").map { doc =>
          doc.getAs[String]("certificateNumber").get
        }.getOrElse("Not found")
        app -> cert // Tuple
      }
    }
  }

  //scalastyle:off method.length
  override def adjustmentReport(frameworkId: String): Future[List[AdjustmentReportItem]] = {
    val query = BSONDocument("$and" ->
      BSONArray(
        BSONDocument("frameworkId" -> frameworkId),
        BSONDocument("applicationStatus" -> BSONDocument("$ne" -> ApplicationStatus.CREATED)),
        BSONDocument("applicationStatus" -> BSONDocument("$ne" -> ApplicationStatus.IN_PROGRESS)),
        BSONDocument("applicationStatus" -> BSONDocument("$ne" -> ApplicationStatus.WITHDRAWN)),
        BSONDocument("$and" ->
          BSONArray(
            BSONDocument("$or" ->
              BSONArray(
                BSONDocument("applicationRoute" -> BSONDocument("$in" ->
                  Seq(ApplicationRoute.Faststream.toString, ApplicationRoute.SdipFaststream.toString))
                ),
                BSONDocument("applicationRoute" -> BSONDocument("$exists" -> false))
              )
            ),
            BSONDocument("$or" ->
              BSONArray(
                BSONDocument("assistance-details.needsSupportForOnlineAssessment" -> true),
                BSONDocument("assistance-details.needsSupportAtVenue" -> true),
                BSONDocument("assistance-details.guaranteedInterview" -> true),
                BSONDocument("assistance-details.adjustmentsConfirmed" -> true)
              )
            )
          )
        )
      )
    )

    val projection = BSONDocument(
      "userId" -> "1",
      "applicationStatus" -> "1",
      "progress-status" -> "1",
      "applicationId" -> "1",
      "personal-details.firstName" -> "1",
      "personal-details.lastName" -> "1",
      "personal-details.preferredName" -> "1",
      "assistance-details.hasDisability" -> "1",
      "assistance-details.needsSupportAtVenueDescription" -> "1",
      "assistance-details.needsSupportForOnlineAssessmentDescription" -> "1",
      "assistance-details.guaranteedInterview" -> "1",
      "assistance-details.hasDisabilityDescription" -> "1",
      "assistance-details.typeOfAdjustments" -> "1",
      "assistance-details.etray" -> "1",
      "assistance-details.video" -> "1",
      "assistance-details.adjustmentsConfirmed" -> "1",
      "assistance-details.adjustmentsComment" -> "1"
    )

    reportQueryWithProjections[BSONDocument](query, projection).map { list =>
      list.map { document =>

        val personalDetails = document.getAs[BSONDocument]("personal-details")
        val userId = document.getAs[String]("userId").getOrElse("")
        val applicationId = document.getAs[String]("applicationId")
        val candidateProgressStatuses = toProgressResponse(applicationId.get).read(document)
        val latestProgressStatus = Some(ProgressStatusesReportLabels.progressStatusNameInReports(candidateProgressStatuses))
        val firstName = extract("firstName")(personalDetails)
        val lastName = extract("lastName")(personalDetails)
        val preferredName = extract("preferredName")(personalDetails)

        val assistance = document.getAs[BSONDocument]("assistance-details")
        val gis = assistance.flatMap(_.getAs[Boolean]("guaranteedInterview")).flatMap(b => Some(booleanTranslator(b)))
        val needsSupportForOnlineAssessmentDescription = extract("needsSupportForOnlineAssessmentDescription")(assistance)
        val needsSupportAtVenueDescription = extract("needsSupportAtVenueDescription")(assistance)
        val hasDisability = extract("hasDisability")(assistance)
        val hasDisabilityDescription = extract("hasDisabilityDescription")(assistance)
        val adjustmentsConfirmed = assistance.flatMap(_.getAs[Boolean]("adjustmentsConfirmed"))
        val adjustmentsComment = extract("adjustmentsComment")(assistance)
        val etray = assistance.flatMap(_.getAs[AdjustmentDetail]("etray"))
        val video = assistance.flatMap(_.getAs[AdjustmentDetail]("video"))
        val typeOfAdjustments = assistance.flatMap(_.getAs[List[String]]("typeOfAdjustments"))

        val adjustments = adjustmentsConfirmed.flatMap { ac =>
          if (ac) Some(Adjustments(typeOfAdjustments, adjustmentsConfirmed, etray, video)) else None
        }

        AdjustmentReportItem(
          userId,
          applicationId,
          firstName,
          lastName,
          preferredName,
          None,
          None,
          gis,
          latestProgressStatus,
          needsSupportForOnlineAssessmentDescription,
          needsSupportAtVenueDescription,
          hasDisability,
          hasDisabilityDescription,
          adjustments,
          adjustmentsComment)
      }
    }
  }
  //scalastyle:on method.length

  override def allApplicationAndUserIds(frameworkId: String): Future[List[PersonalDetailsAdded]] = {
    val query = BSONDocument("frameworkId" -> frameworkId)
    val projection = BSONDocument(
      "applicationId" -> "1",
      "userId" -> "1"
    )

    collection.find(query, projection).cursor[BSONDocument]().collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]()).map {
      _.map { doc =>
        val userId = doc.getAs[String]("userId").getOrElse("")
        val applicationId = doc.getAs[String]("applicationId").getOrElse("")
        PersonalDetailsAdded(applicationId, userId)
      }
    }
  }

  override def candidatesForDuplicateDetectionReport: Future[List[UserApplicationProfile]] = {
    val query = BSONDocument(
      "personal-details" -> BSONDocument("$exists" -> true),
      "progress-status.SUBMITTED" -> BSONDocument("$exists" -> true)
    )
    val projection = BSONDocument(
      "applicationId" -> 1,
      "applicationRoute" -> 1,
      "userId" -> "1",
      "progress-status" -> "1",
      "personal-details.firstName" -> "1",
      "personal-details.lastName" -> "1",
      "personal-details.dateOfBirth" -> "1"
    )

    collection.find(query, projection)
      .cursor[BSONDocument]()
      .collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]())
      .map(_.map(toUserApplicationProfile))
  }

  //scalastyle:off method.length
  override def successfulCandidatesReport: Future[List[SuccessfulCandidatePartialItem]] = {
    def getDate(doc: BSONDocument, status: ProgressStatus): Option[DateTime] = {
      doc.getAs[BSONDocument]("progress-status-timestamp").flatMap(_.getAs[DateTime](status.key))
    }

    def getLegacyDate(doc: BSONDocument, status: ProgressStatus): Option[DateTime] = {
      doc.getAs[BSONDocument]("progress-status-dates").flatMap { legacyDates =>
        legacyDates.getAs[String](status.toString.toLowerCase).map { legacyDateString =>
          DateTime.parse(legacyDateString, DateTimeFormat.forPattern("YYYY-MM-dd"))
        }
      }
    }

    val query = BSONDocument("$and" ->
      BSONArray(
        BSONDocument("userId" -> BSONDocument("$exists" -> true)),
        BSONDocument("applicationId" -> BSONDocument("$exists" -> true)),
        BSONDocument(s"progress-status.${ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER_NOTIFIED}" -> true)
      )
    )

    val projection = BSONDocument(
      "userId" -> true,
      "applicationId" -> true,
      "applicationStatus" -> true,
      "applicationRoute" -> true,
      "personal-details" -> true,
      "progress-status" -> true,
      "progress-status-dates" -> true,
      "progress-status-timestamp" -> true,
      "personal-details.firstName" -> true,
      "personal-details.lastName" -> true,
      "personal-details.preferredName" -> true,
      "fsac-indicator.assessmentCentre" -> true
    )

    collection.find(query, projection).cursor[BSONDocument]().collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]()).map {
      _.map { doc =>
        val userId = doc.getAs[String]("userId").get
        val appId = doc.getAs[String]("applicationId").get
        val applicationStatus = doc.getAs[String]("applicationStatus").get
        val applicationRoute = doc.getAs[String]("applicationRoute").get
        val personalDetailsDoc = doc.getAs[BSONDocument]("personal-details")
        val fullName = for {
          first <- personalDetailsDoc.flatMap(_.getAs[String]("firstName"))
          last <- personalDetailsDoc.flatMap(_.getAs[String]("lastName"))
        } yield s"$first $last"

        val preferredName = personalDetailsDoc.flatMap(_.getAs[String]("preferredName"))
        val maybeSubmittedTimestamp = getDate(doc, ProgressStatuses.SUBMITTED).orElse(getLegacyDate(doc, ProgressStatuses.SUBMITTED))
        val maybeEligibleForOfferTimestamp = getDate(doc, ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER_NOTIFIED).orElse(
          getLegacyDate(doc, ProgressStatuses.ELIGIBLE_FOR_JOB_OFFER_NOTIFIED)
        )
        val fsacIndicatorDoc = doc.getAs[BSONDocument]("fsac-indicator")
        val assessmentCentre = fsacIndicatorDoc.flatMap(_.getAs[String]("assessmentCentre"))

        SuccessfulCandidatePartialItem(
          userId,
          appId,
          applicationStatus,
          applicationRoute,
          fullName,
          preferredName,
          maybeSubmittedTimestamp,
          maybeEligibleForOfferTimestamp,
          assessmentCentre
        )
      }
    }
  }
  //scalastyle:on method.length

  override def preSubmittedApplications(frameworkId: String): Future[List[ApplicationIdsAndStatus]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("frameworkId" -> frameworkId),
      BSONDocument("$or" -> BSONArray(
        BSONDocument(s"progress-status.${ProgressStatuses.CREATED}" -> true),
        BSONDocument(s"applicationStatus" -> ApplicationStatus.IN_PROGRESS),
        BSONDocument(s"applicationStatus" -> ApplicationStatus.CREATED)
      ))
    ))

    val projection = BSONDocument(
      "_id" -> false,
      "applicationId" -> true,
      "userId" -> true,
      "applicationStatus" -> true,
      "applicationRoute" -> true,
      "progress-status" -> "2"
    )

    collection.find(query, projection)
      .cursor[BSONDocument]().collect[List](unlimitedMaxDocs, Cursor.FailOnError[List[BSONDocument]]())
      .map(_.map{ document =>
        val applicationId = document.getAs[String]("applicationId").get
        val userId = document.getAs[String]("userId").get
        val applicationStatus = document.getAs[String]("applicationStatus").get
        val applicationRoute = document.getAs[String]("applicationRoute").get
        val candidateProgressStatuses = toProgressResponse(applicationId).read(document)
        val latestProgressStatus = Some(ProgressStatusesReportLabels.progressStatusNameInReports(candidateProgressStatuses))
        ApplicationIdsAndStatus(applicationId, userId, applicationStatus, applicationRoute, latestProgressStatus)
      })
  }

  private def toUserApplicationProfile(document: BSONDocument) = {
    val applicationId = document.getAs[String]("applicationId").get
    val userId = document.getAs[String]("userId").get

    val applicationRoute = document.getAs[ApplicationRoute]("applicationRoute").get
    val personalDetailsDoc = document.getAs[BSONDocument]("personal-details").get
    val firstName = personalDetailsDoc.getAs[String]("firstName").get
    val lastName = personalDetailsDoc.getAs[String]("lastName").get
    val dob = personalDetailsDoc.getAs[LocalDate]("dateOfBirth").get
    val candidateProgressResponse = toProgressResponse(applicationId).read(document)
    val latestProgressStatus = ProgressStatusesReportLabels.progressStatusNameInReports(candidateProgressResponse)

    UserApplicationProfile(userId, candidateProgressResponse, latestProgressStatus, firstName, lastName, dob, applicationRoute)
  }

  private[application] def isNonSubmittedStatus(progress: ProgressResponse): Boolean = {
    val isNotSubmitted = !progress.submitted
    val isNotWithdrawn = !progress.withdrawn
    isNotWithdrawn && isNotSubmitted
  }

  private def reportQueryWithProjections[A](
                                             query: BSONDocument,
                                             prj: BSONDocument,
                                             upTo: Int = Int.MaxValue,
                                             stopOnError: Boolean = true
                                           )(implicit reader: Format[A]): Future[List[A]] = {
    val err = if (stopOnError) FailOnError[List[A]]() else ContOnError[List[A]]()
    collection.find(query).projection(prj).cursor[A](ReadPreference.nearest).collect[List](upTo, err)
  }

  private def extract(key: String)(root: Option[BSONDocument]) = root.flatMap(_.getAs[String](key))

  private def reportQueryWithProjectionsBSON[A](
                                                 query: BSONDocument,
                                                 prj: BSONDocument,
                                                 upTo: Int = Int.MaxValue,
                                                 stopOnError: Boolean = true
                                               )(implicit reader: BSONDocumentReader[A]): Future[List[A]] = {
    val err = if (stopOnError) FailOnError[List[A]]() else ContOnError[List[A]]()
    bsonCollection.find(query).projection(prj)
      .cursor[A](ReadPreference.nearest)
      .collect[List](Int.MaxValue, err)
  }
}
