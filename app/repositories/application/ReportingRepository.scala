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
import model.ApplicationStatus._
import model.Commands._
import model.command._
import model.persisted._
import model.report._
import model.{ ApplicationStatus, _ }
import org.joda.time.format.{ DateTimeFormat, DateTimeFormatter }
import org.joda.time.{ DateTime, LocalDate }
import play.api.Logger
import play.api.libs.json.{ Format, Json }
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.{ BSONDocument, BSONDocumentReader, _ }
import repositories._
import services.TimeZoneService
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ReportingRepository {
  def adjustmentReport(frameworkId: String): Future[List[AdjustmentReportItem]]

  def candidateProgressReport(frameworkId: String): Future[List[CandidateProgressReportItem]]

  def diversityReport(frameworkId: String): Future[List[ApplicationForDiversityReport]]

  def onlineTestPassMarkReport(frameworkId: String): Future[List[ApplicationForOnlineTestPassMarkReport]]

  def candidateProgressReportNotWithdrawn(frameworkId: String): Future[List[CandidateProgressReportItem]]

  def allApplicationAndUserIds(frameworkId: String): Future[List[PersonalDetailsAdded]]

  def candidateDeferralReport(frameworkId: String): Future[List[ApplicationDeferralPartialItem]]

  def candidatesForDuplicateDetectionReport: Future[List[UserApplicationProfile]]

  def applicationsForInternshipReport(frameworkId: String): Future[List[ApplicationForInternshipReport]]

  def applicationsForAnalyticalSchemesReport(frameworkId: String): Future[List[ApplicationForAnalyticalSchemesReport]]

  def candidatesForTimeToOfferReport: Future[List[TimeToOfferPartialItem]]
}

class ReportingMongoRepository(timeZoneService: TimeZoneService, val dateTimeFactory: DateTimeFactory)(implicit mongo: () => DB)
  extends ReactiveRepository[CreateApplicationRequest, BSONObjectID](CollectionNames.APPLICATION, mongo,
    CreateApplicationRequest.createApplicationRequestFormat, ReactiveMongoFormats.objectIdFormats) with ReportingRepository with RandomSelection with
    CommonBSONDocuments with ReportingRepoBSONReader with ReactiveRepositoryHelpers {

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

  override def candidateDeferralReport(frameworkId: String): Future[List[ApplicationDeferralPartialItem]] = {
    val query = BSONDocument(
      "$and" -> BSONArray(
        BSONDocument("frameworkId" -> frameworkId),
        BSONDocument("partner-graduate-programmes.interested" -> true)
    ))

    val projection = BSONDocument("userId" -> true, "personal-details" -> true, "partner-graduate-programmes" -> true)

    reportQueryWithProjections[BSONDocument](query, projection).map { docs =>
      docs.flatMap { doc =>
        for {
          userId <- doc.getAs[String]("userId")
          personalDetails <- doc.getAs[model.persisted.PersonalDetails]("personal-details")
          programmes <- doc.getAs[BSONDocument]("partner-graduate-programmes").map { p =>
            p.getAs[List[String]]("partnerGraduateProgrammes").getOrElse(Nil)
          }
        } yield {
          ApplicationDeferralPartialItem(
            userId,
            personalDetails.firstName,
            personalDetails.lastName,
            personalDetails.preferredName,
            programmes
          )
        }
      }
    }
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
      "progress-status" -> "2"
    )
    reportQueryWithProjectionsBSON[ApplicationForDiversityReport](query, projection)
  }

  override def onlineTestPassMarkReport(frameworkId: String): Future[List[ApplicationForOnlineTestPassMarkReport]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("frameworkId" -> frameworkId),
      BSONDocument(s"progress-status.${ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED}" -> true)
    ))

    val projection = BSONDocument(
      "userId" -> "1",
      "applicationId" -> "1",
      "applicationRoute" -> "1",
      "scheme-preferences.schemes" -> "1",
      "assistance-details" -> "1",
      "testGroups.PHASE1" -> "1",
      "testGroups.PHASE2" -> "1",
      "testGroups.PHASE3.tests.callbacks.reviewed" -> 1,
      "progress-status" -> "1"
    )

    reportQueryWithProjectionsBSON[ApplicationForOnlineTestPassMarkReport](query, projection)
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
                BSONDocument("applicationRoute" -> "Faststream"),
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
        val applicationStatus = document.getAs[String]("applicationStatus")
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
          applicationStatus,
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

    collection.find(query, projection).cursor[BSONDocument]().collect[List]().map {
      _.map { doc =>
        val userId = doc.getAs[String]("userId").getOrElse("")
        val applicationId = doc.getAs[String]("applicationId").getOrElse("")
        PersonalDetailsAdded(applicationId, userId)
      }
    }
  }

  override def candidatesForDuplicateDetectionReport: Future[List[UserApplicationProfile]] = {
    val query = BSONDocument("personal-details" -> BSONDocument("$exists" -> true))
    val projection = BSONDocument(
      "applicationId" -> 1,
      "userId" -> "1",
      "progress-status" -> "1",
      "personal-details.firstName" -> "1",
      "personal-details.lastName" -> "1",
      "personal-details.dateOfBirth" -> "1"
    )

    collection.find(query, projection)
      .cursor[BSONDocument]()
      .collect[List]()
      .map(_.map(toUserApplicationProfile))
  }

  //scalastyle:off method.length
  override def candidatesForTimeToOfferReport: Future[List[TimeToOfferPartialItem]] = {
    def getDate(doc: BSONDocument, status: ApplicationStatus): Option[DateTime] = {
      doc.getAs[BSONDocument]("progress-status-timestamp").flatMap(_.getAs[DateTime](status))
    }

    def getLegacyDate(doc: BSONDocument, status: ApplicationStatus): Option[DateTime] = {
      doc.getAs[BSONDocument]("progress-status-dates").flatMap { legacyDates =>
        legacyDates.getAs[String](status.toString.toLowerCase).map { legacyDateString =>
          DateTime.parse(legacyDateString, DateTimeFormat.forPattern("YYYY-MM-dd"))
        }
      }
    }

    val query = BSONDocument("$and" ->
      BSONArray(
        BSONDocument("userId" -> BSONDocument("$exists" -> true)),
        BSONDocument("$or" ->
          BSONArray(
            BSONDocument(s"progress-status.${ApplicationStatus.PHASE3_TESTS_PASSED}" -> true),
            BSONDocument(s"progress-status.${ApplicationStatus.PHASE1_TESTS_PASSED}" -> true)
          )
        )
      )
    )

    val projection = BSONDocument(
      "userId" -> true,
      "personal-details" -> true,
      "progress-status" -> true,
      "progress-status-dates" -> true,
      "progress-status-timestamp" -> true,
      "personal-details.firstName" -> true,
      "personal-details.lastName" -> true,
      "personal-details.preferredName" -> true,
      "fsac-indicator.assessmentCentre" -> true
    )

    collection.find(query, projection).cursor[BSONDocument]().collect[List]().map {
      _.map { doc =>
        val userId = doc.getAs[String]("userId").get
        val personalDetailsDoc = doc.getAs[BSONDocument]("personal-details")
        val fullName = for {
          first <- personalDetailsDoc.flatMap(_.getAs[String]("firstName"))
          last <- personalDetailsDoc.flatMap(_.getAs[String]("lastName"))
        } yield first + " " + last

        val preferredName = personalDetailsDoc.flatMap(_.getAs[String]("preferredName"))
        val maybeSubmittedTimestamp = getDate(doc, ApplicationStatus.SUBMITTED).orElse(getLegacyDate(doc, ApplicationStatus.SUBMITTED))
        val maybeExportedTimestamp = getDate(doc, ApplicationStatus.PHASE3_TESTS_PASSED).orElse(
          getLegacyDate(doc, ApplicationStatus.PHASE3_TESTS_PASSED)
        )
        val maybeUpdateExportedTimestamp = getDate(doc, ApplicationStatus.PHASE1_TESTS_PASSED).orElse(
          getLegacyDate(doc, ApplicationStatus.PHASE1_TESTS_PASSED)
        )
        val fsacIndicatorDoc = doc.getAs[BSONDocument]("fsac-indicator")
        val assessmentCentre = fsacIndicatorDoc.flatMap(_.getAs[String]("assessmentCentre"))

        TimeToOfferPartialItem(
          userId,
          fullName,
          preferredName,
          maybeSubmittedTimestamp,
          maybeExportedTimestamp,
          maybeUpdateExportedTimestamp,
          assessmentCentre
        )
      }
    }
  }
  //scalastyle:on method.length

  private def toUserApplicationProfile(document: BSONDocument) = {
    val applicationId = document.getAs[String]("applicationId").get
    val userId = document.getAs[String]("userId").get

    val personalDetailsDoc = document.getAs[BSONDocument]("personal-details").get
    val firstName = personalDetailsDoc.getAs[String]("firstName").get
    val lastName = personalDetailsDoc.getAs[String]("lastName").get
    val dob = personalDetailsDoc.getAs[LocalDate]("dateOfBirth").get
    val candidateProgressStatuses = toProgressResponse(applicationId).read(document)
    val latestProgressStatus = ProgressStatusesReportLabels.progressStatusNameInReports(candidateProgressStatuses)

    UserApplicationProfile(userId, latestProgressStatus, firstName, lastName, dob)
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
                                           )(implicit reader: Format[A]): Future[List[A]] =
    collection.find(query).projection(prj).cursor[A](ReadPreference.nearest).collect[List](upTo, stopOnError)

  private def extract(key: String)(root: Option[BSONDocument]) = root.flatMap(_.getAs[String](key))

  private def reportQueryWithProjectionsBSON[A](
                                                 query: BSONDocument,
                                                 prj: BSONDocument,
                                                 upTo: Int = Int.MaxValue,
                                                 stopOnError: Boolean = true
                                               )(implicit reader: BSONDocumentReader[A]): Future[List[A]] =
    bsonCollection.find(query).projection(prj)
      .cursor[A](ReadPreference.nearest)
      .collect[List](Int.MaxValue, stopOnError = true)
}
